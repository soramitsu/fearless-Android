package jp.co.soramitsu.account.impl.presentation.google_signin

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.backup.GoogleSignInResult
import jp.co.soramitsu.backup.oauth.GoogleOAuthPrompt
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Google sign-in for Drive backup without Google Play services: opens Google's page in the user's
 * browser (never a WebView), receives the custom-scheme redirect through
 * [GoogleDriveSignInRedirectActivity], and finishes with RESULT_OK once the session is stored, or
 * RESULT_CANCELED with [GoogleSignInResult.EXTRA_FAILURE]. Not exported: only the app starts it.
 */
@AndroidEntryPoint
class GoogleDriveSignInActivity : ComponentActivity() {

    private val viewModel: GoogleDriveSignInViewModel by lazy {
        ViewModelProvider(this)[GoogleDriveSignInViewModel::class.java]
    }

    // The redirect delivered while the screen was in the background, handled on the next resume.
    private var pendingRedirect: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Started by a redirect with no sign-in of this app's pending: there is nothing to finish.
        if (savedInstanceState == null && intent?.data != null) {
            finish()
            return
        }
        setContentView(
            FrameLayout(this).apply {
                addView(ProgressBar(this@GoogleDriveSignInActivity), FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
            }
        )
        val prompt = intent?.getStringExtra(EXTRA_PROMPT)
            ?.let { name -> GoogleOAuthPrompt.entries.firstOrNull { it.name == name } }
            ?: GoogleOAuthPrompt.SELECT_ACCOUNT
        viewModel.start(prompt)

        lifecycleScope.launch {
            when (val outcome = viewModel.outcome.filterNotNull().first()) {
                GoogleDriveSignInViewModel.Outcome.Success -> setResult(Activity.RESULT_OK)
                is GoogleDriveSignInViewModel.Outcome.Failed ->
                    setResult(Activity.RESULT_CANCELED, GoogleSignInResult.failureData(outcome.failure))
            }
            finish()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.dataString?.let { pendingRedirect = it }
    }

    override fun onResume() {
        super.onResume()
        val redirect = pendingRedirect.also { pendingRedirect = null }
        viewModel.onResume(redirect)?.let(::openBrowser)
    }

    private fun openBrowser(url: String) {
        val browser = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
        try {
            startActivity(browser)
        } catch (ignored: ActivityNotFoundException) {
            viewModel.onBrowserUnavailable()
        }
    }

    companion object {
        private const val EXTRA_PROMPT = "jp.co.soramitsu.account.GOOGLE_DRIVE_SIGN_IN_PROMPT"
        private const val WRAP = FrameLayout.LayoutParams.WRAP_CONTENT

        /** For an activity result launcher. */
        fun intent(context: Context, prompt: GoogleOAuthPrompt): Intent =
            Intent(context, GoogleDriveSignInActivity::class.java).putExtra(EXTRA_PROMPT, prompt.name)

        /** Hands Google's redirect to the waiting screen; it is never the start of a new sign-in. */
        fun redirectIntent(context: Context, redirect: Uri): Intent =
            Intent(context, GoogleDriveSignInActivity::class.java)
                .setData(redirect)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
