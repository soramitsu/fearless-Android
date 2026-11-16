package jp.co.soramitsu.account.impl.testutil

import android.R
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.AbstractComposeView
import androidx.lifecycle.Lifecycle
import org.robolectric.Shadows.shadowOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import dagger.hilt.android.flags.FragmentGetContextFix
import org.mockito.Mockito.mockStatic
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController

private val openFragmentHosts = mutableListOf<ActivityController<FragmentActivity>>()

/** Supplies the view model directly while exercising the fragment's real view and lifecycle. */
fun hostFragment(fragment: Fragment, viewModel: ViewModel? = null): ActivityController<FragmentActivity> {
    if (viewModel != null) {
        fragment.javaClass.getDeclaredField("viewModel\$delegate").apply { isAccessible = true }
            .set(fragment, lazyOf(viewModel))
        // Injection is supplied by this fixture. Hilt's context/lifecycle implementation still runs.
        generateSequence(fragment.javaClass.superclass) { it.superclass }
            .firstOrNull { it.simpleName.startsWith("Hilt_") }
            ?.getDeclaredField("injected")?.apply { isAccessible = true }?.setBoolean(fragment, true)
    }
    val controller = Robolectric.buildActivity(FragmentActivity::class.java)
    openFragmentHosts += controller
    controller.get().setTheme(jp.co.soramitsu.common.R.style.Theme_Soramitsu_Fearless)
    controller.setup().visible()
    // This application has no Hilt graph; the flag defaults to false in the test host.
    mockStatic(FragmentGetContextFix::class.java).use {
        controller.get().supportFragmentManager.beginTransaction().add(R.id.content, fragment).commitNow()
    }
    return controller
}

/** Disposes hosted compositions before destroying the activity and drains posted dialog callbacks. */
fun ActivityController<FragmentActivity>.closeFragmentHost() {
    openFragmentHosts.remove(this)
    if (get().lifecycle.currentState == Lifecycle.State.DESTROYED) return
    val manager = get().supportFragmentManager
    manager.fragments.forEach { fragment ->
        disposeFragmentCompositions(fragment)
        manager.beginTransaction().remove(fragment).commitNowAllowingStateLoss()
    }
    disposeViewCompositions(get().window.decorView)
    pause().stop().destroy()
    shadowOf(Looper.getMainLooper()).idle()
}

/** Also runs from each host test's teardown, so a failed assertion cannot leak a composition. */
fun closeFragmentHosts() {
    openFragmentHosts.toList().forEach { it.closeFragmentHost() }
}

private fun disposeFragmentCompositions(fragment: Fragment) {
    fragment.childFragmentManager.fragments.forEach(::disposeFragmentCompositions)
    fragment.view?.let(::disposeViewCompositions)
}

private fun disposeViewCompositions(view: View) {
    if (view is AbstractComposeView) view.disposeComposition()
    if (view is ViewGroup) {
        (0 until view.childCount).map(view::getChildAt).forEach(::disposeViewCompositions)
    }
}
