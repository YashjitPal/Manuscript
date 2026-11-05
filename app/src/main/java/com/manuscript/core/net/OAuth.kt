package com.manuscript.core.net

import android.net.Uri
import android.util.Base64
import com.manuscript.BuildConfig
import com.manuscript.core.json.NotionJson
import com.manuscript.core.json.Parse
import com.manuscript.core.json.o
import com.manuscript.core.json.obj
import com.manuscript.core.json.s
import com.manuscript.core.model.NUser
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * A Notion public connection. Notion's token endpoint requires the client secret, so either
 * [tokenProxy] (a small server that holds the secret) or [clientSecret] must be set.
 */
data class OAuthConfig(
    val clientId: String,
    val clientSecret: String?,
    val redirectUri: String,
    val tokenProxy: String? = null,
    val builtIn: Boolean = false,
)

data class OAuthTokens(
    val accessToken: String,
    val refreshToken: String?,
    val workspaceId: String?,
    val workspaceName: String?,
    val workspaceIcon: String?,
    val botId: String?,
    val owner: NUser?,
)

/** The public connection compiled into this build from local.properties, if any. */
object BuiltInOAuth {
    val config: OAuthConfig? by lazy {
        val clientId = BuildConfig.NOTION_OAUTH_CLIENT_ID
        val proxy = BuildConfig.NOTION_OAUTH_TOKEN_PROXY.ifBlank { null }
        val secret = BuildConfig.NOTION_OAUTH_CLIENT_SECRET.ifBlank { null }
        if (clientId.isBlank() || (proxy == null && secret == null)) null
        else OAuthConfig(clientId, if (proxy == null) secret else null, BuildConfig.NOTION_OAUTH_REDIRECT_URI, proxy, builtIn = true)
    }

    val isAvailable: Boolean get() = config != null
}

object NotionOAuth {
    const val DEFAULT_REDIRECT = "manuscript://oauth/callback"

    /** Every sign-in returns to the app through this URI, even when Notion redirects to an https bounce page first. */
    fun isAppCallback(uri: Uri): Boolean = uri.scheme == "manuscript" && uri.host == "oauth"

    fun authorizeUrl(config: OAuthConfig, state: String): Uri =
        Uri.parse("$NOTION_BASE/v1/oauth/authorize").buildUpon()
            .appendQueryParameter("client_id", config.clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("owner", "user")
            .appendQueryParameter("redirect_uri", config.redirectUri)
            .appendQueryParameter("state", state)
            .build()

    suspend fun exchangeCode(client: OkHttpClient, config: OAuthConfig, code: String): OAuthTokens =
        tokenRequest(client, config, obj("grant_type" to "authorization_code", "code" to code, "redirect_uri" to config.redirectUri))

    suspend fun refresh(client: OkHttpClient, config: OAuthConfig, refreshToken: String): OAuthTokens =
        tokenRequest(client, config, obj("grant_type" to "refresh_token", "refresh_token" to refreshToken))

    private suspend fun tokenRequest(client: OkHttpClient, config: OAuthConfig, body: JsonObject): OAuthTokens {
        val request = when {
            config.tokenProxy != null -> Request.Builder()
                .url(config.tokenProxy.trimEnd('/') + "/token")
                .post(body.toJsonBody())
                .build()
            config.clientSecret != null -> {
                val basic = Base64.encodeToString("${config.clientId}:${config.clientSecret}".toByteArray(), Base64.NO_WRAP)
                Request.Builder()
                    .url("$NOTION_BASE/v1/oauth/token")
                    .header("Authorization", "Basic $basic")
                    .header("Notion-Version", NOTION_VERSION)
                    .post(body.toJsonBody())
                    .build()
            }
            else -> throw NotionException.Api(0, "oauth_error", "This connection has no way to finish signing in.")
        }
        val (code, text) = try {
            client.newCall(request).await().use { it.code to it.body.string() }
        } catch (e: java.io.IOException) {
            throw NotionException.Network(e)
        }
        val json = runCatching { NotionJson.parseToJsonElement(text) as JsonObject }.getOrNull()
            ?: throw NotionException.Api(code, "invalid_response", "Unexpected response from the sign-in service")
        if (code !in 200..299) {
            throw NotionException.Api(code, json.s("error") ?: json.s("code") ?: "oauth_error",
                json.s("error_description") ?: json.s("message") ?: "Authorization failed")
        }
        return OAuthTokens(
            accessToken = json.s("access_token") ?: throw NotionException.Api(code, "oauth_error", "No access token returned"),
            refreshToken = json.s("refresh_token"),
            workspaceId = json.s("workspace_id"),
            workspaceName = json.s("workspace_name"),
            workspaceIcon = json.s("workspace_icon"),
            botId = json.s("bot_id"),
            owner = json.o("owner")?.o("user")?.let(Parse::user),
        )
    }
}
