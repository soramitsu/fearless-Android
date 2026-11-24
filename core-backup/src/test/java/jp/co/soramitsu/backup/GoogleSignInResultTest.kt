package jp.co.soramitsu.backup

import android.app.Activity
import org.junit.Assert.assertEquals
import org.junit.Test

class GoogleSignInResultTest {

    @Test
    fun `a stored session is success`() {
        assertEquals(GoogleSignInResult.Success, GoogleSignInResult.fromFailure(Activity.RESULT_OK, failureName = null))
    }

    @Test
    fun `canceled results carry the reason the user must see`() {
        val cases = mapOf(
            null to GoogleSignInResult.Cancelled,
            "CANCELLED" to GoogleSignInResult.Cancelled,
            "CONFIGURATION" to GoogleSignInResult.ConfigurationError,
            "NETWORK" to GoogleSignInResult.Network,
            "DRIVE_ACCESS_DENIED" to GoogleSignInResult.DriveAccessDenied,
            "INVALID_RESPONSE" to GoogleSignInResult.Other(8),
            "STORAGE" to GoogleSignInResult.Other(13),
            "SOMETHING_NEW" to GoogleSignInResult.Cancelled
        )

        cases.forEach { (failure, expected) ->
            assertEquals("$failure", expected, GoogleSignInResult.fromFailure(Activity.RESULT_CANCELED, failure))
        }
    }

    @Test
    fun `a result that names a failure is never success`() {
        assertEquals(GoogleSignInResult.Network, GoogleSignInResult.fromFailure(Activity.RESULT_OK, "NETWORK"))
    }

    @Test
    fun `failure codes stay distinct`() {
        val codes = GoogleSignInResult.Failure.entries.map { it.code }

        assertEquals(codes.size, codes.toSet().size)
    }
}
