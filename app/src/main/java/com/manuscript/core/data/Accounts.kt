package com.manuscript.core.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.manuscript.core.json.NotionJson
import com.manuscript.core.net.BuiltInOAuth
import com.manuscript.core.net.OAuthConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "manuscript")

@Serializable
enum class AccountKind { TOKEN, OAUTH, DEMO }

@Serializable
data class Account(
    val id: String,
    val kind: AccountKind,
    val workspaceId: String? = null,
    val workspaceName: String? = null,
    val workspaceIcon: String? = null,
    val userId: String? = null,
    val userName: String? = null,
    val userEmail: String? = null,
    val userAvatar: String? = null,
    val botId: String? = null,
    val isPersonal: Boolean = false,
    val maxUploadBytes: Long? = null,
    val addedAt: Long = System.currentTimeMillis(),
) {
    val displayWorkspace: String get() = workspaceName?.takeIf { it.isNotBlank() } ?: "Notion workspace"
    val displayUser: String get() = userName?.takeIf { it.isNotBlank() } ?: userEmail ?: "You"
}

/** Encrypts secrets with a non-exportable AES key held in the Android Keystore. */
class TokenVault {
    private val alias = "manuscript.secrets.v1"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val body = Base64.encodeToString(cipher.doFinal(plain.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        return "$iv:$body"
    }

    fun decrypt(encoded: String): String? = runCatching {
        val (iv, body) = encoded.split(':', limit = 2)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
        String(cipher.doFinal(Base64.decode(body, Base64.NO_WRAP)), Charsets.UTF_8)
    }.getOrNull()
}

@Serializable
private data class StoredOAuth(
    val clientId: String,
    val clientSecret: String? = null,
    val redirectUri: String,
    val tokenProxy: String? = null,
    val builtIn: Boolean = false,
) {
    fun toConfig(): OAuthConfig =
        if (builtIn) BuiltInOAuth.config ?: OAuthConfig(clientId, clientSecret, redirectUri, tokenProxy, builtIn = true)
        else OAuthConfig(clientId, clientSecret, redirectUri, tokenProxy)

    companion object {
        fun of(config: OAuthConfig) = StoredOAuth(
            config.clientId,
            config.clientSecret.takeUnless { config.builtIn },
            config.redirectUri,
            config.tokenProxy,
            config.builtIn,
        )
    }
}

@Serializable
private data class PendingOAuth(val state: String, val oauth: StoredOAuth, val startedAt: Long)

class AccountStore(private val context: Context, private val vault: TokenVault) {
    private val store get() = context.appDataStore
    private val accountsKey = stringPreferencesKey("accounts")
    private val currentKey = stringPreferencesKey("current_account")
    private fun tokenKey(id: String) = stringPreferencesKey("secret_$id")
    private fun refreshKey(id: String) = stringPreferencesKey("refresh_$id")
    private fun oauthKey(id: String) = stringPreferencesKey("oauth_$id")
    private val pendingOAuthKey = stringPreferencesKey("pending_oauth")
    private val listSerializer = ListSerializer(Account.serializer())

    val accounts: Flow<List<Account>> = store.data.map { prefs ->
        prefs[accountsKey]?.let { runCatching { NotionJson.decodeFromString(listSerializer, it) }.getOrNull() } ?: emptyList()
    }

    val currentId: Flow<String?> = store.data.map { it[currentKey] }

    suspend fun add(account: Account, token: String?, refreshToken: String? = null, oauth: OAuthConfig? = null) {
        store.edit { prefs ->
            val list = prefs[accountsKey]?.let { NotionJson.decodeFromString(listSerializer, it) } ?: emptyList()
            val existing = list.firstOrNull {
                account.kind != AccountKind.DEMO && it.kind == account.kind && it.botId != null && it.botId == account.botId
            }
            val merged = if (existing != null) list.map { if (it.id == existing.id) account.copy(id = existing.id) else it }
            else list.filterNot { it.kind == AccountKind.DEMO && account.kind == AccountKind.DEMO } + account
            val id = existing?.id ?: account.id
            prefs[accountsKey] = NotionJson.encodeToString(listSerializer, merged)
            prefs[currentKey] = id
            token?.let { prefs[tokenKey(id)] = vault.encrypt(it) }
            refreshToken?.let { prefs[refreshKey(id)] = vault.encrypt(it) }
            oauth?.let {
                prefs[oauthKey(id)] = vault.encrypt(NotionJson.encodeToString(StoredOAuth.serializer(), StoredOAuth.of(it)))
            }
        }
    }

    suspend fun update(account: Account) {
        store.edit { prefs ->
            val list = prefs[accountsKey]?.let { NotionJson.decodeFromString(listSerializer, it) } ?: return@edit
            prefs[accountsKey] = NotionJson.encodeToString(listSerializer, list.map { if (it.id == account.id) account else it })
        }
    }

    suspend fun remove(id: String) {
        store.edit { prefs ->
            val list = prefs[accountsKey]?.let { NotionJson.decodeFromString(listSerializer, it) } ?: emptyList()
            val remaining = list.filterNot { it.id == id }
            prefs[accountsKey] = NotionJson.encodeToString(listSerializer, remaining)
            prefs.remove(tokenKey(id))
            prefs.remove(refreshKey(id))
            prefs.remove(oauthKey(id))
            if (prefs[currentKey] == id) {
                remaining.firstOrNull()?.let { prefs[currentKey] = it.id } ?: prefs.remove(currentKey)
            }
        }
    }

    suspend fun setCurrent(id: String) {
        store.edit { it[currentKey] = id }
    }

    suspend fun token(id: String): String? = store.data.first()[tokenKey(id)]?.let(vault::decrypt)

    /** The stored ciphertext, which changes whenever the token does; cheap to read on every request. */
    suspend fun sealedToken(id: String): String? = store.data.first()[tokenKey(id)]

    fun unseal(sealed: String): String? = vault.decrypt(sealed)

    suspend fun refreshToken(id: String): String? = store.data.first()[refreshKey(id)]?.let(vault::decrypt)

    suspend fun oauthConfig(id: String): OAuthConfig? = store.data.first()[oauthKey(id)]?.let(vault::decrypt)?.let {
        runCatching { NotionJson.decodeFromString(StoredOAuth.serializer(), it) }.getOrNull()
    }?.toConfig()

    /** Remembers an in-flight browser sign-in so it can finish even if Android stops the app meanwhile. */
    suspend fun savePendingOAuth(config: OAuthConfig, state: String) {
        val pending = PendingOAuth(state, StoredOAuth.of(config), System.currentTimeMillis())
        store.edit { it[pendingOAuthKey] = vault.encrypt(NotionJson.encodeToString(PendingOAuth.serializer(), pending)) }
    }

    suspend fun takePendingOAuth(): Pair<OAuthConfig, String>? {
        val sealed = store.data.first()[pendingOAuthKey] ?: return null
        store.edit { it.remove(pendingOAuthKey) }
        val pending = vault.decrypt(sealed)?.let { runCatching { NotionJson.decodeFromString(PendingOAuth.serializer(), it) }.getOrNull() }
            ?: return null
        if (System.currentTimeMillis() - pending.startedAt > 30 * 60 * 1000) return null
        return pending.oauth.toConfig() to pending.state
    }

    suspend fun updateTokens(id: String, access: String, refresh: String?) {
        store.edit { prefs ->
            prefs[tokenKey(id)] = vault.encrypt(access)
            refresh?.let { prefs[refreshKey(id)] = vault.encrypt(it) }
        }
    }
}
