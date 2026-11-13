package jp.co.soramitsu.account.impl.presentation.google_signin

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.backup.GoogleSignInResult
import jp.co.soramitsu.backup.oauth.GoogleDriveAuthorization
import jp.co.soramitsu.backup.oauth.GoogleDriveSignInFlow
import jp.co.soramitsu.backup.oauth.GoogleOAuthPrompt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Runs one browser sign-in for [GoogleDriveSignInActivity]. The pending request lives in the saved
 * state, so it survives the process being killed while Google's page is open, and the code exchange
 * runs here so a recreated screen does not cancel it.
 */
@HiltViewModel
class GoogleDriveSignInViewModel @Inject constructor(
    authorization: GoogleDriveAuthorization,
    private val savedState: SavedStateHandle
) : ViewModel() {

    /** Set once: Success, or why the sign-in stopped. */
    sealed interface Outcome {
        data object Success : Outcome
        data class Failed(val failure: GoogleSignInResult.Failure) : Outcome
    }

    private val flow = GoogleDriveSignInFlow(authorization)
    private val mutableOutcome = MutableStateFlow<Outcome?>(null)
    val outcome: StateFlow<Outcome?> = mutableOutcome.asStateFlow()
    private var isExchanging = false

    init {
        val saved = savedState.keys().filter { it.startsWith(STATE_PREFIX) }
            .associate { it.removePrefix(STATE_PREFIX) to savedState.get<String>(it) }
        if (saved.isNotEmpty()) flow.restore(saved)
    }

    /** Starts the sign-in the first time the screen is created; a recreated screen continues the saved one. */
    fun start(prompt: GoogleOAuthPrompt) {
        if (savedState.get<Boolean>(KEY_STARTED) == true) return
        savedState[KEY_STARTED] = true
        flow.begin(prompt)
        persist()
    }

    /** Returns the URL to open in the browser, or null when there is nothing for the screen to do. */
    fun onResume(redirect: String?): String? {
        if (isExchanging || mutableOutcome.value != null) return null
        val step = flow.onResume(redirect)
        persist()
        when (step) {
            is GoogleDriveSignInFlow.Step.OpenBrowser -> return step.url
            is GoogleDriveSignInFlow.Step.Exchange -> {
                isExchanging = true
                viewModelScope.launch { finish(flow.exchange(step)) }
            }
            is GoogleDriveSignInFlow.Step.Finish -> finish(step.failure)
        }
        return null
    }

    /** No app on the device can show Google's page. */
    fun onBrowserUnavailable() {
        finish(GoogleSignInResult.Failure.CONFIGURATION)
    }

    private fun finish(failure: GoogleSignInResult.Failure?) {
        isExchanging = false
        mutableOutcome.value = failure?.let(Outcome::Failed) ?: Outcome.Success
    }

    private fun persist() {
        savedState.keys().filter { it.startsWith(STATE_PREFIX) }.forEach { savedState.remove<String>(it) }
        flow.save().forEach { (key, value) -> savedState[STATE_PREFIX + key] = value }
    }

    private companion object {
        const val STATE_PREFIX = "googleDriveSignIn."
        const val KEY_STARTED = "googleDriveSignInStarted"
    }
}
