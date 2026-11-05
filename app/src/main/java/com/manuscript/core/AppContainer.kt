package com.manuscript.core

import android.content.Context
import com.manuscript.core.data.Account
import com.manuscript.core.data.AccountKind
import com.manuscript.core.data.AccountStore
import com.manuscript.core.data.AppSettings
import com.manuscript.core.data.ObjectCache
import com.manuscript.core.data.Session
import com.manuscript.core.data.SettingsStore
import com.manuscript.core.data.TokenVault
import com.manuscript.core.data.db.AppDatabase
import com.manuscript.core.demo.DemoInterceptor
import com.manuscript.core.demo.DemoStore
import com.manuscript.core.net.NotionApi
import com.manuscript.core.net.NotionHttp
import com.manuscript.core.net.NotionOAuth
import com.manuscript.core.net.OAuthConfig
import com.manuscript.core.net.userMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.UUID
import java.util.concurrent.TimeUnit

private const val NOTION_FILES_HOST = "https://prod-files-secure.s3.us-west-2.amazonaws.com/"
private const val NOTION_ICONS_URL = "https://www.notion.so/icons/document_gray.svg"

sealed interface SessionState {
    data object Loading : SessionState
    data object SignedOut : SessionState
    data class Active(val session: Session) : SessionState
}

sealed interface OAuthPhase {
    data object Idle : OAuthPhase
    data object InBrowser : OAuthPhase
    data object Exchanging : OAuthPhase
    data class Failed(val message: String) : OAuthPhase
}

class AppContainer(val context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val vault = TokenVault()
    val accounts = AccountStore(context, vault)
    val settingsStore = SettingsStore(context)
    val db: AppDatabase by lazy { AppDatabase.create(context) }
    val demoStore: DemoStore by lazy { DemoStore() }

    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /** Notion's files live on S3, which only speaks HTTP/1.1, so parallel downloads each need their own connection. */
    val imageClient: OkHttpClient by lazy {
        httpClient.newBuilder()
            .dispatcher(Dispatcher().apply { maxRequests = 64; maxRequestsPerHost = 12 })
            .connectionPool(ConnectionPool(16, 5, TimeUnit.MINUTES))
            .build()
    }

    /** Opens connections to the hosts serving page images so the first images skip the TLS handshake. */
    fun warmUpImageHosts() {
        listOf(NOTION_FILES_HOST, NOTION_ICONS_URL).forEach { url ->
            imageClient.newCall(Request.Builder().url(url).head().build()).enqueue(object : Callback {
                override fun onFailure(call: Call, e: java.io.IOException) = Unit
                override fun onResponse(call: Call, response: Response) = response.close()
            })
        }
    }

    val settings: StateFlow<AppSettings> = settingsStore.settings.stateIn(appScope, SharingStarted.Eagerly, AppSettings())
    val accountList: StateFlow<List<Account>> = accounts.accounts.stateIn(appScope, SharingStarted.Eagerly, emptyList())

    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = _state.asStateFlow()
    val session: Session? get() = (state.value as? SessionState.Active)?.session

    private val sessions = mutableMapOf<String, Session>()
    private val sessionScopes = mutableMapOf<String, CoroutineScope>()

    init {
        combine(accounts.accounts, accounts.currentId) { list, current ->
            list.firstOrNull { it.id == current } ?: list.firstOrNull()
        }.onEach { account ->
            _state.value = if (account == null) SessionState.SignedOut else SessionState.Active(sessionFor(account))
        }.launchIn(appScope)
    }

    private fun sessionFor(account: Account): Session {
        sessions[account.id]?.let { existing ->
            if (existing.account == account) return existing
            if (existing.account.kind == account.kind) {
                // Only display details changed (e.g. a renamed workspace); keep the live connection and scope.
                return Session(account, existing.api, existing.cache, db, context, existing.scope).also { sessions[account.id] = it }
            }
        }
        sessionScopes.remove(account.id)?.cancel()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val cache = ObjectCache(db.cache(), account.id, scope)
        var unsealed: Pair<String, String>? = null
        val client = if (account.kind == AccountKind.DEMO) {
            httpClient.newBuilder().addInterceptor(DemoInterceptor(demoStore)).build()
        } else httpClient
        val http = NotionHttp(
            client = client,
            token = {
                if (account.kind == AccountKind.DEMO) "demo"
                else {
                    val sealed = accounts.sealedToken(account.id)
                    when {
                        sealed == null -> null
                        unsealed?.first == sealed -> unsealed?.second
                        else -> accounts.unseal(sealed)?.also { unsealed = sealed to it }
                    }
                }
            },
            refresh = {
                if (account.kind != AccountKind.OAUTH) false
                else {
                    val config = accounts.oauthConfig(account.id)
                    val refreshToken = accounts.refreshToken(account.id)
                    if (config == null || refreshToken == null) false
                    else runCatching {
                        val tokens = NotionOAuth.refresh(httpClient, config, refreshToken)
                        accounts.updateTokens(account.id, tokens.accessToken, tokens.refreshToken)
                    }.isSuccess
                }
            },
        )
        val api = NotionApi(http, sink = cache::accept)
        val session = Session(account, api, cache, db, context, scope)
        sessions[account.id] = session
        sessionScopes[account.id] = scope
        return session
    }

    /** Validates a personal access token or internal connection token and adds the account. */
    suspend fun signInWithToken(token: String): Account {
        val clean = token.trim()
        val api = NotionApi(NotionHttp(httpClient, token = { clean }))
        val me = api.me()
        val owner = me.ownerUser
        val account = Account(
            id = UUID.randomUUID().toString(),
            kind = AccountKind.TOKEN,
            workspaceId = me.workspaceId,
            workspaceName = me.workspaceName ?: if (me.isPerson) "${me.displayName.substringBefore(' ')}'s Notion" else null,
            userId = if (me.isPerson) me.id else owner?.id,
            userName = if (me.isPerson) me.name else owner?.name ?: me.name,
            userEmail = if (me.isPerson) me.email else owner?.email,
            userAvatar = if (me.isPerson) me.avatarUrl else owner?.avatarUrl ?: me.avatarUrl,
            botId = if (me.isBot) me.id else "pat:${me.id}",
            isPersonal = me.isPerson,
            maxUploadBytes = me.maxUploadBytes,
        )
        accounts.add(account, clean)
        return account
    }

    suspend fun signInWithOAuth(config: OAuthConfig, code: String): Account {
        val tokens = NotionOAuth.exchangeCode(httpClient, config, code)
        val me = runCatching { NotionApi(NotionHttp(httpClient, token = { tokens.accessToken })).me() }.getOrNull()
        val account = Account(
            id = UUID.randomUUID().toString(),
            kind = AccountKind.OAUTH,
            workspaceId = tokens.workspaceId,
            workspaceName = tokens.workspaceName,
            workspaceIcon = tokens.workspaceIcon,
            userId = tokens.owner?.id,
            userName = tokens.owner?.name,
            userEmail = tokens.owner?.email,
            userAvatar = tokens.owner?.avatarUrl,
            botId = tokens.botId,
            maxUploadBytes = me?.maxUploadBytes,
        )
        accounts.add(account, tokens.accessToken, tokens.refreshToken, config)
        return account
    }

    suspend fun startDemo(): Account {
        accountList.value.firstOrNull { it.kind == AccountKind.DEMO }?.let {
            accounts.setCurrent(it.id)
            return it
        }
        val account = Account(
            id = "demo",
            kind = AccountKind.DEMO,
            workspaceName = "Manuscript Studio",
            userId = DemoStore.ME,
            userName = "Ada Lovelace",
            userEmail = "ada@manuscript.app",
            botId = "demo",
            maxUploadBytes = 5L * 1024 * 1024,
        )
        accounts.add(account, null)
        return account
    }

    private var pendingOAuth: Pair<OAuthConfig, String>? = null
    private val _oauthPhase = MutableStateFlow<OAuthPhase>(OAuthPhase.Idle)
    val oauthPhase: StateFlow<OAuthPhase> = _oauthPhase.asStateFlow()

    /** Starts a browser sign-in and returns the Notion consent URL to open. */
    suspend fun beginOAuth(config: OAuthConfig): android.net.Uri {
        val state = UUID.randomUUID().toString()
        pendingOAuth = config to state
        accounts.savePendingOAuth(config, state)
        _oauthPhase.value = OAuthPhase.InBrowser
        return NotionOAuth.authorizeUrl(config, state).also {
            if (com.manuscript.BuildConfig.DEBUG) android.util.Log.d("ManuscriptOAuth", "authorize $it")
        }
    }

    /** Completes OAuth from the redirect delivered to the activity. Returns true if [uri] was an OAuth callback. */
    fun handleOAuthRedirect(uri: android.net.Uri): Boolean {
        if (!NotionOAuth.isAppCallback(uri)) return false
        _oauthPhase.value = OAuthPhase.Exchanging
        appScope.launch {
            val stored = accounts.takePendingOAuth()
            val (config, state) = pendingOAuth ?: stored ?: run {
                _oauthPhase.value = OAuthPhase.Failed("That sign-in link expired. Please try again.")
                return@launch
            }
            pendingOAuth = null
            val error = uri.getQueryParameter("error")
            val code = uri.getQueryParameter("code")
            val problem = when {
                error == "access_denied" -> "Sign-in was cancelled in Notion."
                error != null -> "Notion couldn't authorize Manuscript ($error)."
                uri.getQueryParameter("state") != state -> "The sign-in response didn't match. Please try again."
                code == null -> "Notion didn't return an authorization code."
                else -> null
            }
            _oauthPhase.value = if (problem != null) OAuthPhase.Failed(problem)
            else runCatching { signInWithOAuth(config, code!!) }.fold(
                onSuccess = { OAuthPhase.Idle },
                onFailure = { OAuthPhase.Failed(it.userMessage()) },
            )
        }
        return true
    }

    /** The user came back from the browser without finishing; let them try again. */
    fun onAppResumed() {
        if (_oauthPhase.value == OAuthPhase.InBrowser) _oauthPhase.value = OAuthPhase.Idle
    }

    fun clearOAuthError() {
        if (_oauthPhase.value is OAuthPhase.Failed) _oauthPhase.value = OAuthPhase.Idle
    }

    suspend fun switchTo(accountId: String) = accounts.setCurrent(accountId)

    suspend fun renameWorkspace(account: Account, name: String) = accounts.update(account.copy(workspaceName = name))

    suspend fun signOut(accountId: String) {
        sessionScopes.remove(accountId)?.cancel()
        sessions.remove(accountId)
        accounts.remove(accountId)
    }

    suspend fun currentSettings(): AppSettings = settingsStore.settings.first()
}
