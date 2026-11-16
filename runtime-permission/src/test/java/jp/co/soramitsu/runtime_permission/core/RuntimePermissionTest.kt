package jp.co.soramitsu.runtime_permission.core

import android.Manifest
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.net.Uri
import android.os.Looper
import android.provider.Settings
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import jp.co.soramitsu.runtime_permission.core.callbacks.PermissionListener
import jp.co.soramitsu.runtime_permission.core.callbacks.ResponseCallback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
class RuntimePermissionTest {

    private lateinit var activity: FragmentActivity

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun grant(vararg permissions: String) = shadowOf(activity.application).grantPermissions(*permissions)

    private fun showRationale(permission: String, show: Boolean) =
        shadowOf(activity.packageManager).setShouldShowRequestPermissionRationale(permission, show)

    private fun permissionFragment(): PermissionFragment? =
        activity.supportFragmentManager.findFragmentByTag(FRAGMENT_TAG) as PermissionFragment?

    private class RecordingListener : PermissionListener {
        val accepted = mutableListOf<List<String>>()
        val denied = mutableListOf<Pair<List<String>, List<String>>>()
        override fun onAccepted(permissionResult: PermissionResult, accepted: List<String>) {
            this.accepted += accepted
        }

        override fun onDenied(permissionResult: PermissionResult, denied: List<String>, foreverDenied: List<String>) {
            this.denied += denied to foreverDenied
        }
    }

    @Test
    fun grantedPermissionsAreAcceptedAtOnceWithoutAskingTheSystem() {
        grant(Manifest.permission.CAMERA)
        var accepted: PermissionResult? = null
        var denied = false
        var response: PermissionResult? = null
        val listener = RecordingListener()

        RuntimePermission.askPermission(activity, Manifest.permission.CAMERA)
            .onAccepted { accepted = it }
            .onDenied { denied = true }
            .onForeverDenied { denied = true }
            .onResponse(ResponseCallback { response = it })
            .onResponse(listener)
            .ask()
        idle()

        assertEquals(listOf(Manifest.permission.CAMERA), accepted!!.accepted)
        assertTrue(accepted!!.isAccepted)
        assertSame(accepted, response)
        assertFalse(denied)
        assertEquals(listOf(listOf(Manifest.permission.CAMERA)), listener.accepted)
        assertTrue(listener.denied.isEmpty())
        assertNull(permissionFragment())
        assertNull(shadowOf(activity).lastRequestedPermission)
    }

    @Test
    fun missingPermissionsAreRequestedThroughAHeadlessFragment() {
        RuntimePermission.askPermission(activity, Manifest.permission.CAMERA, Manifest.permission.READ_CONTACTS).ask()
        idle()

        val fragment = permissionFragment()
        assertNotNull(fragment)
        assertEquals(
            arrayListOf(Manifest.permission.CAMERA, Manifest.permission.READ_CONTACTS),
            fragment!!.requireArguments().getStringArrayList(PermissionFragment.LIST_PERMISSIONS)
        )
        val request = shadowOf(activity).lastRequestedPermission
        assertNotNull(request)
        assertEquals(
            listOf(Manifest.permission.CAMERA, Manifest.permission.READ_CONTACTS),
            request!!.requestedPermissions.toList()
        )
    }

    @Test
    fun grantedResultReachesTheCallbacksAndRemovesTheFragment() {
        var accepted: PermissionResult? = null
        var response: PermissionResult? = null
        RuntimePermission.askPermission(activity, Manifest.permission.CAMERA)
            .onAccepted { accepted = it }
            .ask(ResponseCallback { response = it })
        idle()

        permissionFragment()!!.onRequestPermissionsResult(
            REQUEST_CODE,
            arrayOf(Manifest.permission.CAMERA),
            intArrayOf(PackageManager.PERMISSION_GRANTED)
        )
        idle()

        assertEquals(listOf(Manifest.permission.CAMERA), accepted!!.accepted)
        assertSame(accepted, response)
        assertNull(permissionFragment())
    }

    @Test
    fun refusalWithRationaleIsDeniedAndWithoutRationaleIsForeverDenied() {
        showRationale(Manifest.permission.CAMERA, true)
        showRationale(Manifest.permission.READ_CONTACTS, false)
        var accepted = false
        var denied: PermissionResult? = null
        var foreverDenied: PermissionResult? = null
        val listener = RecordingListener()
        RuntimePermission.askPermission(activity)
            .request(listOf(Manifest.permission.CAMERA, Manifest.permission.READ_CONTACTS, Manifest.permission.RECORD_AUDIO))
            .onAccepted { accepted = true }
            .onDenied { denied = it }
            .onForeverDenied { foreverDenied = it }
            .ask(listener)
        idle()

        permissionFragment()!!.onRequestPermissionsResult(
            REQUEST_CODE,
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.READ_CONTACTS, Manifest.permission.RECORD_AUDIO),
            intArrayOf(PackageManager.PERMISSION_DENIED, PackageManager.PERMISSION_DENIED, PackageManager.PERMISSION_GRANTED)
        )
        idle()

        assertFalse(accepted)
        val result = denied!!
        assertSame(result, foreverDenied)
        assertFalse(result.isAccepted)
        assertTrue(result.hasDenied())
        assertTrue(result.hasForeverDenied())
        assertEquals(listOf(Manifest.permission.RECORD_AUDIO), result.accepted)
        assertEquals(listOf(Manifest.permission.CAMERA), result.denied)
        assertEquals(listOf(Manifest.permission.READ_CONTACTS), result.foreverDenied)
        assertTrue(listener.accepted.isEmpty())
        assertEquals(
            listOf(listOf(Manifest.permission.CAMERA) to listOf(Manifest.permission.READ_CONTACTS)),
            listener.denied
        )
        assertNull(permissionFragment())
    }

    @Test
    fun askingAgainAfterARefusalUsesTheSameRequest() {
        showRationale(Manifest.permission.CAMERA, true)
        var denials = 0
        var deniedResult: PermissionResult? = null
        var accepted: PermissionResult? = null
        val permission = RuntimePermission.askPermission(activity, Manifest.permission.CAMERA)
            .onDenied {
                denials++
                deniedResult = it
            }
            .onAccepted { accepted = it }
        permission.ask()
        idle()
        permissionFragment()!!.onRequestPermissionsResult(
            REQUEST_CODE,
            arrayOf(Manifest.permission.CAMERA),
            intArrayOf(PackageManager.PERMISSION_DENIED)
        )
        idle()
        assertEquals(1, denials)

        grant(Manifest.permission.CAMERA)
        val firstResult = deniedResult!!
        assertSame(permission, firstResult.runtimePermission)
        firstResult.askAgain()

        assertEquals(listOf(Manifest.permission.CAMERA), accepted!!.accepted)
        assertEquals(1, denials)
    }

    @Test
    fun aSecondRequestWhileOneIsPendingTakesOverTheExistingFragment() {
        var firstCalled = false
        var second: PermissionResult? = null
        RuntimePermission.askPermission(activity, Manifest.permission.CAMERA).onResponse(ResponseCallback { firstCalled = true }).ask()
        idle()
        val fragment = permissionFragment()

        RuntimePermission.askPermission(activity, Manifest.permission.CAMERA).onResponse(ResponseCallback { second = it }).ask()
        idle()
        assertSame(fragment, permissionFragment())
        assertEquals(1, activity.supportFragmentManager.fragments.count { it is PermissionFragment })

        fragment!!.onRequestPermissionsResult(
            REQUEST_CODE,
            arrayOf(Manifest.permission.CAMERA),
            intArrayOf(PackageManager.PERMISSION_GRANTED)
        )
        idle()

        assertFalse(firstCalled)
        assertEquals(listOf(Manifest.permission.CAMERA), second!!.accepted)
    }

    @Test
    fun fragmentIgnoresForeignRequestCodesAndEmptyResults() {
        var responses = 0
        RuntimePermission.askPermission(activity, Manifest.permission.CAMERA).onResponse(ResponseCallback { responses++ }).ask()
        idle()
        val fragment = permissionFragment()!!

        fragment.onRequestPermissionsResult(REQUEST_CODE + 1, arrayOf(Manifest.permission.CAMERA), intArrayOf(PackageManager.PERMISSION_GRANTED))
        fragment.onRequestPermissionsResult(REQUEST_CODE, emptyArray(), IntArray(0))
        idle()
        assertEquals(0, responses)
        assertSame(fragment, permissionFragment())

        fragment.setListener(null)
        fragment.onRequestPermissionsResult(REQUEST_CODE, arrayOf(Manifest.permission.CAMERA), intArrayOf(PackageManager.PERMISSION_GRANTED))
        idle()
        assertEquals(0, responses)
        assertSame(fragment, permissionFragment())
    }

    @Test
    fun fragmentWithoutPermissionsRemovesItself() {
        val fragment = PermissionFragment.newInstance(emptyList())
        activity.supportFragmentManager.beginTransaction().add(fragment, "empty").commitNow()
        idle()

        assertNull(activity.supportFragmentManager.findFragmentByTag("empty"))
        assertNull(shadowOf(activity).lastRequestedPermission)
    }

    @Test
    fun fragmentWithoutArgumentsHasNothingToRequest() {
        val fragment = PermissionFragment()
        activity.supportFragmentManager.beginTransaction().add(fragment, "bare").commitNow()
        idle()

        assertNull(activity.supportFragmentManager.findFragmentByTag("bare"))
    }

    @Test
    fun withoutExplicitPermissionsTheManifestDecides() {
        installRequestedPermissions(
            arrayOf(Manifest.permission.CAMERA),
            intArrayOf(PackageInfo.REQUESTED_PERMISSION_GRANTED)
        )
        var accepted: PermissionResult? = null

        RuntimePermission(activity).onAccepted { accepted = it }.ask()

        assertTrue(accepted!!.accepted.isEmpty())
        assertTrue(accepted!!.isAccepted)
        assertNull(permissionFragment())
    }

    @Test
    fun manifestFinderListsOnlyUngrantedPermissionsThatBelongToAGroup() {
        installRequestedPermissions(
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.READ_CONTACTS, "jp.co.soramitsu.permission.NO_GROUP"),
            intArrayOf(0, PackageInfo.REQUESTED_PERMISSION_GRANTED, 0)
        )
        val packageManager = shadowOf(activity.packageManager)
        packageManager.addPermissionInfo(PermissionInfo().apply {
            name = Manifest.permission.CAMERA
            group = "android.permission-group.CAMERA"
        })
        packageManager.addPermissionInfo(PermissionInfo().apply {
            name = Manifest.permission.READ_CONTACTS
            group = "android.permission-group.CONTACTS"
        })

        assertEquals(
            listOf(Manifest.permission.CAMERA),
            PermissionManifestFinder.findNeededPermissionsFromManifest(activity)
        )
    }

    @Test
    fun goToSettingsOpensThisApplicationsDetails() {
        RuntimePermission(activity).goToSettings()

        val intent = shadowOf(activity).nextStartedActivity
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
        assertEquals(Uri.fromParts("package", activity.packageName, null), intent.data)
    }

    @Test
    fun withoutAnActivityNothingIsAskedOrOpened() {
        var responses = 0
        val permission = RuntimePermission.askPermission(null as FragmentActivity?, Manifest.permission.CAMERA)
            .onResponse(ResponseCallback { responses++ })
        permission.ask()
        permission.goToSettings()
        RuntimePermission.askPermission(null as Fragment?, Manifest.permission.CAMERA)
            .onResponse(ResponseCallback { responses++ })
            .ask()

        assertEquals(0, responses)
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun aFinishingActivityIsNotAsked() {
        var responses = 0
        activity.finish()

        RuntimePermission.askPermission(activity, Manifest.permission.CAMERA).onResponse(ResponseCallback { responses++ }).ask()
        idle()

        assertEquals(0, responses)
        assertNull(permissionFragment())
    }

    @Test
    fun aFragmentAsksThroughItsActivity() {
        grant(Manifest.permission.CAMERA)
        val host = Fragment()
        activity.supportFragmentManager.beginTransaction().add(host, "host").commitNow()
        var accepted: PermissionResult? = null

        RuntimePermission.askPermission(host, Manifest.permission.CAMERA).onAccepted { accepted = it }.ask()

        assertEquals(listOf(Manifest.permission.CAMERA), accepted!!.accepted)
    }

    @Test
    fun nullCallbacksAndPermissionListsAreIgnored() {
        grant(Manifest.permission.CAMERA)
        var accepted: PermissionResult? = null
        val permission = RuntimePermission.askPermission(activity, Manifest.permission.CAMERA)
            .request(null as List<String>?)
            .onAccepted(null)
            .onDenied(null)
            .onForeverDenied(null)
            .onResponse(null as ResponseCallback?)
            .onResponse(null as PermissionListener?)
        permission.askAgain()
        assertNull(permissionFragment())

        permission.onAccepted { accepted = it }
        permission.ask(null as ResponseCallback?)
        assertEquals(listOf(Manifest.permission.CAMERA), accepted!!.accepted)

        accepted = null
        permission.ask(null as PermissionListener?)
        assertEquals(listOf(Manifest.permission.CAMERA), accepted!!.accepted)
    }

    @Test
    fun resultGoToSettingsOpensTheDetailsPage() {
        grant(Manifest.permission.CAMERA)
        var accepted: PermissionResult? = null
        RuntimePermission.askPermission(activity, Manifest.permission.CAMERA).onAccepted { accepted = it }.ask()

        accepted!!.goToSettings()

        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, shadowOf(activity).nextStartedActivity.action)
    }

    private fun installRequestedPermissions(permissions: Array<String>, flags: IntArray) {
        val info = activity.packageManager.getPackageInfo(activity.packageName, PackageManager.GET_PERMISSIONS)
        info.requestedPermissions = permissions
        info.requestedPermissionsFlags = flags
        shadowOf(activity.packageManager).installPackage(info)
    }

    private companion object {
        const val FRAGMENT_TAG = "PERMISSION_FRAGMENT_WEEEEE"
        const val REQUEST_CODE = 23
    }
}
