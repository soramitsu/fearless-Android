@file:Suppress("DEPRECATION") // GoogleSignIn: see GoogleDriveStorage.

package jp.co.soramitsu.backup

import android.app.Activity
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException

/**
 * The outcome of the Google sign-in screen. Its activity result is RESULT_CANCELED not only when the
 * user backs out but also for configuration and network failures, which callers must explain.
 */
sealed interface GoogleSignInResult {
    data object Success : GoogleSignInResult

    data object Cancelled : GoogleSignInResult

    /** DEVELOPER_ERROR: this build's signing certificate or OAuth client is not registered. */
    data object ConfigurationError : GoogleSignInResult

    data object Network : GoogleSignInResult

    data class Other(val statusCode: Int) : GoogleSignInResult

    companion object {
        private const val NETWORK_ERROR = 7
        private const val DEVELOPER_ERROR = 10
        private const val SIGN_IN_CANCELLED = 12_501

        fun parse(resultCode: Int, data: Intent?): GoogleSignInResult {
            val task = data?.let(GoogleSignIn::getSignedInAccountFromIntent)
            val statusCode = (task?.exception as? ApiException)?.statusCode
            return fromStatus(resultCode, signedIn = task?.isSuccessful == true, statusCode = statusCode)
        }

        internal fun fromStatus(
            resultCode: Int,
            signedIn: Boolean,
            statusCode: Int?
        ): GoogleSignInResult {
            if (resultCode == Activity.RESULT_OK && signedIn) return Success

            return when (statusCode) {
                null, SIGN_IN_CANCELLED -> Cancelled
                DEVELOPER_ERROR -> ConfigurationError
                NETWORK_ERROR -> Network
                else -> Other(statusCode)
            }
        }
    }
}
