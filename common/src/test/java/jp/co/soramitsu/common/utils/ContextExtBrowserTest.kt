package jp.co.soramitsu.common.utils

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/** Google sign-in for Drive backup is offered only where some app can open Google's https page. */
class ContextExtBrowserTest {

    private val intent = mock(Intent::class.java)

    @Test
    fun `an installed browser makes Google sign-in available`() {
        val packageManager = mock(PackageManager::class.java)
        `when`(packageManager.queryIntentActivities(intent, 0)).thenReturn(listOf(mock(ResolveInfo::class.java)))

        assertTrue(packageManager.canOpenHttpsLinks(intent))
    }

    @Test
    fun `without a browser Google sign-in is not offered`() {
        val packageManager = mock(PackageManager::class.java)
        `when`(packageManager.queryIntentActivities(intent, 0)).thenReturn(emptyList())

        assertFalse(packageManager.canOpenHttpsLinks(intent))
    }
}
