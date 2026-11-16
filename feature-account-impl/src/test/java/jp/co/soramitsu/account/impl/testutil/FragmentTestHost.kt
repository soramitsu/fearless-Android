package jp.co.soramitsu.account.impl.testutil

import android.R
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import dagger.hilt.android.flags.FragmentGetContextFix
import org.mockito.Mockito.mockStatic
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController

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
    controller.get().setTheme(jp.co.soramitsu.common.R.style.Theme_Soramitsu_Fearless)
    controller.setup().visible()
    // This application has no Hilt graph; the flag defaults to false in the test host.
    mockStatic(FragmentGetContextFix::class.java).use {
        controller.get().supportFragmentManager.beginTransaction().add(R.id.content, fragment).commitNow()
    }
    return controller
}
