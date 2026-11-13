package jp.co.soramitsu.backup.oauth

import okhttp3.HttpUrl.Companion.toHttpUrl
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** What the Google sign-in page shows before it returns to the app. */
enum class GoogleOAuthPrompt(internal val parameter: String?) {
    /** Lets the user pick, or add, the Google account, as the old native account chooser did. */
    SELECT_ACCOUNT("select_account"),

    /** Asks again for Drive access the user turned off on the consent screen. */
    CONSENT("consent")
}

/**
 * One OAuth 2.0 authorization-code request with PKCE (RFC 7636, S256) and a random `state`.
 * [codeVerifier] and [state] never leave the app: the browser sees only the S256 challenge and the
 * state, and only the token endpoint ever receives the verifier.
 */
class GoogleOAuthAuthorizationRequest internal constructor(
    val clientId: String,
    val redirectUri: String,
    val scopes: List<String>,
    val state: String,
    val codeVerifier: String,
    val prompt: GoogleOAuthPrompt
) {
    init {
        require(scopes.isNotEmpty() && scopes.all { SCOPE.matches(it) }) { "Invalid OAuth scopes" }
        require(OPAQUE_VALUE.matches(state)) { "Invalid OAuth state" }
        require(GoogleOAuthPkce.isValidVerifier(codeVerifier)) { "Invalid PKCE code verifier" }
    }

    /** The browser URL: response_type=code, the S256 challenge and the state, nothing secret. */
    fun authorizationUrl(): String {
        return GoogleOAuthConfig.AUTHORIZATION_ENDPOINT.toHttpUrl().newBuilder()
            .addQueryParameter("client_id", clientId)
            .addQueryParameter("redirect_uri", redirectUri)
            .addQueryParameter("response_type", "code")
            .addQueryParameter("scope", scopes.joinToString(" "))
            .addQueryParameter("state", state)
            .addQueryParameter("code_challenge", GoogleOAuthPkce.challengeS256(codeVerifier))
            .addQueryParameter("code_challenge_method", GoogleOAuthPkce.CHALLENGE_METHOD)
            .apply { prompt.parameter?.let { addQueryParameter("prompt", it) } }
            .build()
            .toString()
    }

    /** For an activity's saved state, so the request survives the process being killed while the browser is open. */
    fun toMap(): Map<String, String> = mapOf(
        KEY_CLIENT_ID to clientId,
        KEY_REDIRECT_URI to redirectUri,
        KEY_SCOPES to scopes.joinToString(" "),
        KEY_STATE to state,
        KEY_CODE_VERIFIER to codeVerifier,
        KEY_PROMPT to prompt.name
    )

    override fun toString(): String =
        "GoogleOAuthAuthorizationRequest(clientId=$clientId, scopes=$scopes, prompt=$prompt)"

    companion object {
        private const val KEY_CLIENT_ID = "clientId"
        private const val KEY_REDIRECT_URI = "redirectUri"
        private const val KEY_SCOPES = "scopes"
        private const val KEY_STATE = "state"
        private const val KEY_CODE_VERIFIER = "codeVerifier"
        private const val KEY_PROMPT = "prompt"
        private const val STATE_BYTES = 32

        internal val OPAQUE_VALUE = Regex("^[A-Za-z0-9_-]{43,128}$")
        private val SCOPE = Regex("^[\\x21\\x23-\\x5B\\x5D-\\x7E]{1,256}$")

        val KEYS: Set<String> = setOf(KEY_CLIENT_ID, KEY_REDIRECT_URI, KEY_SCOPES, KEY_STATE, KEY_CODE_VERIFIER, KEY_PROMPT)

        fun create(
            config: GoogleOAuthConfig,
            prompt: GoogleOAuthPrompt,
            scopes: List<String> = listOf(GoogleOAuthConfig.DRIVE_APPDATA_SCOPE),
            random: SecureRandom = SecureRandom()
        ): GoogleOAuthAuthorizationRequest {
            return GoogleOAuthAuthorizationRequest(
                clientId = config.clientId,
                redirectUri = config.redirectUri,
                scopes = scopes.distinct(),
                state = randomOpaqueValue(random, STATE_BYTES),
                codeVerifier = GoogleOAuthPkce.newVerifier(random),
                prompt = prompt
            )
        }

        /** Null for anything this class did not write, which the caller treats as "no request pending". */
        fun fromMap(values: Map<String, String?>): GoogleOAuthAuthorizationRequest? {
            return runCatching {
                GoogleOAuthAuthorizationRequest(
                    clientId = requireNotNull(values[KEY_CLIENT_ID]),
                    redirectUri = requireNotNull(values[KEY_REDIRECT_URI]),
                    scopes = requireNotNull(values[KEY_SCOPES]).split(' '),
                    state = requireNotNull(values[KEY_STATE]),
                    codeVerifier = requireNotNull(values[KEY_CODE_VERIFIER]),
                    prompt = GoogleOAuthPrompt.valueOf(requireNotNull(values[KEY_PROMPT]))
                )
            }.getOrNull()
        }

        internal fun randomOpaqueValue(random: SecureRandom, bytes: Int): String {
            val buffer = ByteArray(bytes)
            random.nextBytes(buffer)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer).also { buffer.fill(0) }
        }
    }
}

/** RFC 7636 proof key: a 256-bit random verifier and its SHA-256 challenge. */
object GoogleOAuthPkce {
    const val CHALLENGE_METHOD = "S256"
    private const val VERIFIER_BYTES = 32
    private val VERIFIER = Regex("^[A-Za-z0-9._~-]{43,128}$")

    fun newVerifier(random: SecureRandom = SecureRandom()): String =
        GoogleOAuthAuthorizationRequest.randomOpaqueValue(random, VERIFIER_BYTES)

    fun isValidVerifier(verifier: String): Boolean = VERIFIER.matches(verifier)

    fun challengeS256(verifier: String): String {
        require(isValidVerifier(verifier)) { "Invalid PKCE code verifier" }
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }
}
