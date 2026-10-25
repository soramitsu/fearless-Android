package jp.co.soramitsu.backup

import android.app.Activity
import org.junit.Assert.assertEquals
import org.junit.Test

class GoogleSignInResultTest {

    @Test
    fun `signed in account is success`() {
        assertEquals(GoogleSignInResult.Success, GoogleSignInResult.fromStatus(Activity.RESULT_OK, signedIn = true, statusCode = null))
    }

    @Test
    fun `canceled results carry the reason the user must see`() {
        val cases = mapOf(
            null to GoogleSignInResult.Cancelled,
            12_501 to GoogleSignInResult.Cancelled,
            10 to GoogleSignInResult.ConfigurationError,
            7 to GoogleSignInResult.Network,
            12_500 to GoogleSignInResult.Other(12_500)
        )

        cases.forEach { (statusCode, expected) ->
            assertEquals("$statusCode", expected, GoogleSignInResult.fromStatus(Activity.RESULT_CANCELED, signedIn = false, statusCode = statusCode))
        }
    }

    @Test
    fun `a result without an account is not success`() {
        assertEquals(GoogleSignInResult.Network, GoogleSignInResult.fromStatus(Activity.RESULT_OK, signedIn = false, statusCode = 7))
    }
}
