package jp.co.soramitsu.backup.oauth

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Where the signed-in Google session lives between app starts. The app backs it with its encrypted
 * preferences; nothing else may hold the refresh token. Calls may block on disk.
 */
interface GoogleOAuthTokenStore {
    fun read(): String?

    /** Must be durable when it returns. */
    fun write(value: String)

    fun clear()
}

/** The persisted part of a sign-in: the refresh token, bound to the client that obtained it. */
class GoogleOAuthSession(
    val clientId: String,
    val refreshToken: String,
    val grantedScopes: Set<String>,
    val createdAtMillis: Long
) {
    override fun toString(): String = "GoogleOAuthSession(clientId=$clientId, scopes=$grantedScopes, redacted)"

    fun encode(): String = JsonObject().apply {
        addProperty(KEY_VERSION, VERSION)
        addProperty(KEY_CLIENT_ID, clientId)
        addProperty(KEY_REFRESH_TOKEN, refreshToken)
        add(KEY_SCOPES, JsonArray().apply { grantedScopes.sorted().forEach(::add) })
        addProperty(KEY_CREATED_AT, createdAtMillis)
    }.toString()

    companion object {
        private const val VERSION = 1
        private const val KEY_VERSION = "version"
        private const val KEY_CLIENT_ID = "clientId"
        private const val KEY_REFRESH_TOKEN = "refreshToken"
        private const val KEY_SCOPES = "scopes"
        private const val KEY_CREATED_AT = "createdAtMillis"

        /** Null for anything unreadable; the caller then treats the user as signed out. */
        fun decode(value: String): GoogleOAuthSession? = runCatching {
            val json = JsonParser.parseString(value).asJsonObject
            require(json.get(KEY_VERSION).asInt == VERSION)
            GoogleOAuthSession(
                clientId = json.get(KEY_CLIENT_ID).asString,
                refreshToken = json.get(KEY_REFRESH_TOKEN).asString.also { require(it.isNotBlank()) },
                grantedScopes = json.getAsJsonArray(KEY_SCOPES).map { it.asString }.toSet(),
                createdAtMillis = json.get(KEY_CREATED_AT).asLong
            )
        }.getOrNull()
    }
}
