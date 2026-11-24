package jp.co.soramitsu.backup

import android.app.Activity
import android.content.Intent

/**
 * The outcome of the Google sign-in screen. Its activity result is RESULT_CANCELED not only when the
 * user backs out but also for configuration and network failures, which callers must explain.
 */
sealed interface GoogleSignInResult {
    data object Success : GoogleSignInResult

    data object Cancelled : GoogleSignInResult

    /** Google refused this build's OAuth client or redirect, or no browser can show its page. */
    data object ConfigurationError : GoogleSignInResult

    data object Network : GoogleSignInResult

    /** The user signed in but turned Drive access off on Google's consent page. */
    data object DriveAccessDenied : GoogleSignInResult

    data class Other(val statusCode: Int) : GoogleSignInResult

    /** Why the sign-in screen finished without a session; written to its result as [EXTRA_FAILURE]. */
    enum class Failure(val code: Int) {
        CANCELLED(CANCELLED_CODE),
        CONFIGURATION(CONFIGURATION_CODE),
        NETWORK(NETWORK_CODE),

        /** Google answered, but not with a usable response for the pending request. */
        INVALID_RESPONSE(INVALID_RESPONSE_CODE),

        /** The user signed in but turned Drive access off on the consent page. */
        DRIVE_ACCESS_DENIED(DRIVE_ACCESS_DENIED_CODE),

        /** The session could not be stored on this device. */
        STORAGE(STORAGE_CODE)
    }

    companion object {
        const val EXTRA_FAILURE = "jp.co.soramitsu.backup.GOOGLE_SIGN_IN_FAILURE"

        private const val CANCELLED_CODE = 12_501
        private const val CONFIGURATION_CODE = 10
        private const val NETWORK_CODE = 7
        private const val INVALID_RESPONSE_CODE = 8
        private const val DRIVE_ACCESS_DENIED_CODE = 4
        private const val STORAGE_CODE = 13

        fun parse(resultCode: Int, data: Intent?): GoogleSignInResult {
            return fromFailure(resultCode, data?.getStringExtra(EXTRA_FAILURE))
        }

        /** The data the sign-in screen finishes with for [failure]. */
        fun failureData(failure: Failure): Intent = Intent().putExtra(EXTRA_FAILURE, failure.name)

        internal fun fromFailure(resultCode: Int, failureName: String?): GoogleSignInResult {
            val failure = Failure.entries.firstOrNull { it.name == failureName }
            if (resultCode == Activity.RESULT_OK && failure == null) return Success

            return when (failure) {
                null, Failure.CANCELLED -> Cancelled
                Failure.CONFIGURATION -> ConfigurationError
                Failure.NETWORK -> Network
                Failure.DRIVE_ACCESS_DENIED -> DriveAccessDenied
                Failure.INVALID_RESPONSE, Failure.STORAGE -> Other(failure.code)
            }
        }
    }
}
