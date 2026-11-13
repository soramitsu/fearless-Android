package jp.co.soramitsu.backup.oauth

import jp.co.soramitsu.backup.GoogleSignInResult.Failure
import kotlinx.coroutines.CancellationException
import java.io.IOException

/**
 * What the sign-in screen does at each step of one browser sign-in, kept free of Android so it can be
 * tested. The screen opens the browser once, then waits for the redirect; resuming without one means
 * the user left Google's page. A redirect is checked against the one pending request and consumed,
 * so it can never be replayed.
 */
class GoogleDriveSignInFlow(private val authorization: GoogleDriveAuthorization) {

    sealed interface Step {
        class OpenBrowser(val url: String) : Step {
            override fun toString(): String = "OpenBrowser"
        }

        class Exchange(
            val request: GoogleOAuthAuthorizationRequest,
            val code: GoogleOAuthAuthorizationResponse.Code
        ) : Step {
            override fun toString(): String = "Exchange"
        }

        /** [failure] is null when a session was stored. */
        data class Finish(val failure: Failure?) : Step
    }

    private var request: GoogleOAuthAuthorizationRequest? = null
    private var browserOpened = false

    val hasPendingRequest: Boolean
        get() = request != null

    fun begin(prompt: GoogleOAuthPrompt) {
        request = authorization.newAuthorizationRequest(prompt)
        browserOpened = false
    }

    /** For the screen's saved instance state; holds the PKCE verifier, so it stays in this app's process state only. */
    fun save(): Map<String, String> = request?.toMap().orEmpty() + (KEY_BROWSER_OPENED to browserOpened.toString())

    fun restore(state: Map<String, String?>) {
        request = GoogleOAuthAuthorizationRequest.fromMap(state)
        browserOpened = state[KEY_BROWSER_OPENED] == true.toString()
    }

    /** Called whenever the screen resumes, with the redirect delivered since, if any. */
    fun onResume(redirect: String?): Step {
        val pending = request ?: return Step.Finish(Failure.INVALID_RESPONSE)
        if (redirect != null) {
            request = null
            return stepFor(pending, GoogleOAuthAuthorizationResponse.parse(pending, redirect))
        }
        if (!browserOpened) {
            browserOpened = true
            return Step.OpenBrowser(pending.authorizationUrl())
        }
        request = null
        return Step.Finish(Failure.CANCELLED)
    }

    /** Exchanges the code and stores the session; returns why it failed, or null. */
    suspend fun exchange(step: Step.Exchange): Failure? {
        return try {
            authorization.completeAuthorization(step.request, step.code)
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failureFor(e)
        }
    }

    companion object {
        private const val KEY_BROWSER_OPENED = "browserOpened"
        private val CONFIGURATION_ERRORS = setOf(
            "invalid_request",
            "invalid_client",
            "unauthorized_client",
            "unsupported_response_type",
            "invalid_scope",
            "redirect_uri_mismatch"
        )
        private val TEMPORARY_ERRORS = setOf("server_error", "temporarily_unavailable")

        internal fun stepFor(
            request: GoogleOAuthAuthorizationRequest,
            response: GoogleOAuthAuthorizationResponse
        ): Step = when (response) {
            is GoogleOAuthAuthorizationResponse.Code -> Step.Exchange(request, response)
            GoogleOAuthAuthorizationResponse.Denied -> Step.Finish(Failure.CANCELLED)
            is GoogleOAuthAuthorizationResponse.Error -> Step.Finish(
                when (response.error) {
                    in CONFIGURATION_ERRORS -> Failure.CONFIGURATION
                    in TEMPORARY_ERRORS -> Failure.NETWORK
                    else -> Failure.INVALID_RESPONSE
                }
            )
            is GoogleOAuthAuthorizationResponse.Rejected -> Step.Finish(Failure.INVALID_RESPONSE)
        }

        internal fun failureFor(error: Exception): Failure = when (error) {
            is GoogleDriveScopeNotGrantedException -> Failure.DRIVE_ACCESS_DENIED
            is GoogleOAuthException -> when (error.kind) {
                GoogleOAuthException.Kind.CLIENT_CONFIGURATION -> Failure.CONFIGURATION
                // The code expired or was already used: signing in again starts a new one.
                GoogleOAuthException.Kind.INVALID_GRANT -> Failure.INVALID_RESPONSE
                GoogleOAuthException.Kind.SERVER -> Failure.NETWORK
            }
            is IOException -> Failure.NETWORK
            is IllegalArgumentException -> Failure.INVALID_RESPONSE
            // The encrypted preferences could not keep the session.
            else -> Failure.STORAGE
        }
    }
}
