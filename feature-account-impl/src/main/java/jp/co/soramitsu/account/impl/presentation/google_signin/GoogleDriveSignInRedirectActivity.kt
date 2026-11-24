package jp.co.soramitsu.account.impl.presentation.google_signin

import android.app.Activity
import android.os.Bundle

/**
 * Receives Google's `<reversed client id>:/oauth2redirect` redirect from the browser. Any app can send
 * this exported activity a URI, so it forwards nothing but the URI to [GoogleDriveSignInActivity],
 * which acts on it only when its `state` matches the sign-in pending there, and then only once.
 */
class GoogleDriveSignInRedirectActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.data?.let { redirect ->
            startActivity(GoogleDriveSignInActivity.redirectIntent(this, redirect))
        }
        finish()
    }
}
