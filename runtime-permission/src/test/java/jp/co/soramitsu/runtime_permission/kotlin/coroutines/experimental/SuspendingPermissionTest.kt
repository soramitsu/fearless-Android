package jp.co.soramitsu.runtime_permission.kotlin.coroutines.experimental

import android.Manifest
import android.content.pm.PackageManager
import android.os.Looper
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import jp.co.soramitsu.runtime_permission.core.PermissionFragment
import jp.co.soramitsu.runtime_permission.core.PermissionResult
import jp.co.soramitsu.runtime_permission.kotlin.NoActivityException
import jp.co.soramitsu.runtime_permission.kotlin.PermissionException
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class SuspendingPermissionTest {

    private lateinit var activity: FragmentActivity

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    /** Starts [block] on the calling (main) thread and returns a reader of its outcome, null while it is suspended. */
    private fun <T> launch(block: suspend () -> T): () -> Result<T>? {
        var outcome: Result<T>? = null
        block.startCoroutine(Continuation(EmptyCoroutineContext) { outcome = it })
        return { outcome }
    }

    private fun answer(vararg grants: Pair<String, Int>) {
        idle()
        activity.supportFragmentManager.fragments.filterIsInstance<PermissionFragment>().single().onRequestPermissionsResult(
            23,
            grants.map { it.first }.toTypedArray(),
            grants.map { it.second }.toIntArray()
        )
        idle()
    }

    @Test
    fun activityResumesWithTheResultOfAGrantedPermission() {
        shadowOf(activity.application).grantPermissions(Manifest.permission.CAMERA)

        val outcome = launch { activity.askPermission(Manifest.permission.CAMERA) }

        assertEquals(listOf(Manifest.permission.CAMERA), outcome()!!.getOrThrow().accepted)
    }

    @Test
    fun activityWaitsForTheUserAndResumesWhenGranted() {
        val outcome = launch { activity.askPermission(Manifest.permission.CAMERA) }
        idle()
        assertNull(outcome())

        answer(Manifest.permission.CAMERA to PackageManager.PERMISSION_GRANTED)

        assertTrue(outcome()!!.getOrThrow().isAccepted)
    }

    @Test
    fun activityRefusalFailsWithAPermissionException() {
        val outcome = launch { activity.askPermission(Manifest.permission.CAMERA) }

        answer(Manifest.permission.CAMERA to PackageManager.PERMISSION_DENIED)

        val error = outcome()!!.exceptionOrNull() as PermissionException
        assertEquals(listOf(Manifest.permission.CAMERA), error.foreverDenied)
    }

    @Test
    fun fragmentResumesWithTheResult() {
        val host = Fragment()
        activity.supportFragmentManager.beginTransaction().add(host, "host").commitNow()

        val outcome = launch<PermissionResult> { host.askPermission(Manifest.permission.READ_CONTACTS) }
        answer(Manifest.permission.READ_CONTACTS to PackageManager.PERMISSION_GRANTED)

        assertEquals(listOf(Manifest.permission.READ_CONTACTS), outcome()!!.getOrThrow().accepted)
    }

    @Test
    fun fragmentRefusalFailsWithAPermissionException() {
        shadowOf(activity.packageManager).setShouldShowRequestPermissionRationale(Manifest.permission.CAMERA, true)
        val host = Fragment()
        activity.supportFragmentManager.beginTransaction().add(host, "host").commitNow()

        val outcome = launch<PermissionResult> { host.askPermission(Manifest.permission.CAMERA) }
        answer(Manifest.permission.CAMERA to PackageManager.PERMISSION_DENIED)

        val error = outcome()!!.exceptionOrNull() as PermissionException
        assertEquals(listOf(Manifest.permission.CAMERA), error.denied)
    }

    @Test
    fun detachedFragmentFailsWithNoActivity() {
        val outcome = launch<PermissionResult> { Fragment().askPermission(Manifest.permission.CAMERA) }

        assertTrue(outcome()!!.exceptionOrNull() is NoActivityException)
    }
}
