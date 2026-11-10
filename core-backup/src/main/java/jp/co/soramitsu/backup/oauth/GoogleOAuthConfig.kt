package jp.co.soramitsu.backup.oauth

/**
 * The public Google OAuth client Drive backup signs in with. Installed-app clients have no secret,
 * so the id, its custom-scheme redirect and the PKCE verifier are all a sign-in needs.
 *
 * Drive keeps wallet backups in the appDataFolder of the Cloud project that owns the client, so a
 * client from another project would show upgrading users an empty Drive. [create] therefore only
 * accepts clients of [DRIVE_BACKUP_PROJECT_NUMBER], the project Android, iOS and the browser
 * extension have always used.
 */
class GoogleOAuthConfig private constructor(val clientId: String) {

    /** Google's custom URI scheme for installed-app clients: the client id with its labels reversed. */
    val redirectScheme: String = REDIRECT_SCHEME_PREFIX + clientId.removeSuffix(CLIENT_ID_SUFFIX)

    val redirectUri: String = "$redirectScheme:$REDIRECT_PATH"

    override fun toString(): String = "GoogleOAuthConfig(clientId=$clientId)"

    override fun equals(other: Any?): Boolean = other is GoogleOAuthConfig && other.clientId == clientId

    override fun hashCode(): Int = clientId.hashCode()

    companion object {
        const val DRIVE_BACKUP_PROJECT_NUMBER = "621183184145"

        /**
         * The project's iOS client. Its custom-scheme redirect is accepted from any platform when the
         * request carries PKCE, so Android uses it until the owner registers an Android client with
         * custom URI schemes enabled (set GOOGLE_DRIVE_OAUTH_CLIENT_ID; see scripts/secrets.gradle).
         */
        const val DEFAULT_CLIENT_ID = "621183184145-jla1mrbl3soobt85btt2nebon8s3d30b.apps.googleusercontent.com"

        const val AUTHORIZATION_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
        const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
        const val REVOCATION_ENDPOINT = "https://oauth2.googleapis.com/revoke"

        /** The only scope Drive backup needs: files the app itself keeps in the hidden appDataFolder. */
        const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"

        const val REDIRECT_PATH = "/oauth2redirect"
        private const val REDIRECT_SCHEME_PREFIX = "com.googleusercontent.apps."
        private const val CLIENT_ID_SUFFIX = ".apps.googleusercontent.com"
        private val CLIENT_ID_PATTERN = Regex("^([0-9]{1,20})-[0-9a-z]{1,64}\\.apps\\.googleusercontent\\.com$")

        /** Accepts only a well-formed client id of the Drive backup project; blank means [DEFAULT_CLIENT_ID]. */
        fun create(clientId: String): GoogleOAuthConfig {
            val normalized = clientId.trim().ifEmpty { DEFAULT_CLIENT_ID }
            val match = requireNotNull(CLIENT_ID_PATTERN.matchEntire(normalized)) {
                "Google Drive OAuth client id is malformed"
            }
            require(match.groupValues[1] == DRIVE_BACKUP_PROJECT_NUMBER) {
                "Google Drive OAuth client must belong to project $DRIVE_BACKUP_PROJECT_NUMBER, " +
                    "where existing wallet backups are kept"
            }
            return GoogleOAuthConfig(normalized)
        }
    }
}
