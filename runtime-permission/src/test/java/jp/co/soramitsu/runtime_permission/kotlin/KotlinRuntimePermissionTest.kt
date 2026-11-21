package jp.co.soramitsu.runtime_permission.kotlin

import android.Manifest
import android.content.pm.PackageManager
import android.os.Looper
import android.provider.Settings
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import jp.co.soramitsu.runtime_permission.core.PermissionFragment
import jp.co.soramitsu.runtime_permission.core.PermissionResult
import jp.co.soramitsu.runtime_permission.core.RuntimePermission
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class KotlinRuntimePermissionTest {

    private lateinit var activity: FragmentActivity

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun deliver(permission: String, grant: Int) {
        idle()
        val fragment = activity.supportFragmentManager.fragments.filterIsInstance<PermissionFragment>().single()
        fragment.onRequestPermissionsResult(23, arrayOf(permission), intArrayOf(grant))
        idle()
    }

    @Test
    fun activityAskAcceptsAGrantedPermissionImmediately() {
        shadowOf(activity.application).grantPermissions(Manifest.permission.CAMERA)
        var accepted: PermissionResult? = null

        val request = activity.askPermission(Manifest.permission.CAMERA) { accepted = it }

        assertEquals(listOf(Manifest.permission.CAMERA), accepted!!.accepted)
        val replacement = RuntimePermission(activity)
        request.runtimePermission = replacement
        assertSame(replacement, request.runtimePermission)
    }

    @Test
    fun declinedBlockRunsOnlyForARefusal() {
        shadowOf(activity.packageManager).setShouldShowRequestPermissionRationale(Manifest.permission.CAMERA, true)
        var accepted = false
        var declined: PermissionResult? = null

        activity.askPermission(Manifest.permission.CAMERA) { accepted = true }
            .onDeclined { declined = it }
        deliver(Manifest.permission.CAMERA, PackageManager.PERMISSION_DENIED)

        assertFalse(accepted)
        assertEquals(listOf(Manifest.permission.CAMERA), declined!!.denied)
    }

    @Test
    fun declinedBlockIsSkippedWhenTheUserGrants() {
        var accepted: PermissionResult? = null
        var declined = false

        activity.askPermission(Manifest.permission.CAMERA) { accepted = it }
            .onDeclined { declined = true }
        deliver(Manifest.permission.CAMERA, PackageManager.PERMISSION_GRANTED)

        assertEquals(listOf(Manifest.permission.CAMERA), accepted!!.accepted)
        assertFalse(declined)
    }

    @Test
    fun foreverDeniedCountsAsDeclined() {
        var declined: PermissionResult? = null

        activity.askPermission(Manifest.permission.READ_CONTACTS) { }
            .onDeclined { declined = it }
        deliver(Manifest.permission.READ_CONTACTS, PackageManager.PERMISSION_DENIED)

        assertEquals(listOf(Manifest.permission.READ_CONTACTS), declined!!.foreverDenied)
        assertTrue(declined!!.denied.isEmpty())
    }

    @Test
    fun fragmentAskUsesItsActivity() {
        shadowOf(activity.application).grantPermissions(Manifest.permission.CAMERA)
        val host = Fragment()
        activity.supportFragmentManager.beginTransaction().add(host, "host").commitNow()
        var accepted: PermissionResult? = null

        host.askPermission(Manifest.permission.CAMERA) { accepted = it }

        assertEquals(listOf(Manifest.permission.CAMERA), accepted!!.accepted)
    }

    @Test
    fun permissionExceptionExposesTheRefusal() {
        shadowOf(activity.packageManager).setShouldShowRequestPermissionRationale(Manifest.permission.CAMERA, true)
        var declined: PermissionResult? = null
        activity.askPermission(Manifest.permission.CAMERA, Manifest.permission.READ_CONTACTS) { }
            .onDeclined { declined = it }
        idle()
        activity.supportFragmentManager.fragments.filterIsInstance<PermissionFragment>().single().onRequestPermissionsResult(
            23,
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.READ_CONTACTS),
            intArrayOf(PackageManager.PERMISSION_DENIED, PackageManager.PERMISSION_DENIED)
        )
        idle()

        val exception = PermissionException(declined!!)

        assertSame(declined, exception.permissionResult)
        assertEquals(listOf(Manifest.permission.CAMERA), exception.denied)
        assertEquals(listOf(Manifest.permission.READ_CONTACTS), exception.foreverDenied)
        assertTrue(exception.accepted.isEmpty())
        assertSame(declined!!.runtimePermission, exception.runtimePermission)
        assertFalse(exception.isAccepted())
        assertTrue(exception.hasDenied())
        assertTrue(exception.hasForeverDenied())

        exception.goToSettings()
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, shadowOf(activity).nextStartedActivity.action)

        shadowOf(activity.application).grantPermissions(Manifest.permission.CAMERA, Manifest.permission.READ_CONTACTS)
        var acceptedAfterRetry: PermissionResult? = null
        exception.runtimePermission.onAccepted { acceptedAfterRetry = it }
        exception.askAgain()
        assertEquals(listOf(Manifest.permission.CAMERA, Manifest.permission.READ_CONTACTS), acceptedAfterRetry!!.accepted)
        assertNull(activity.supportFragmentManager.fragments.filterIsInstance<PermissionFragment>().firstOrNull())
    }

    @Test
    fun noActivityExceptionIsAnException() {
        val exception: Exception = NoActivityException()
        assertNull(exception.message)
    }
}
