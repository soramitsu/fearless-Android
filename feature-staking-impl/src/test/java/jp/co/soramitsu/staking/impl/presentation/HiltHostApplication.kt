package jp.co.soramitsu.staking.impl.presentation

import android.app.Application
import dagger.hilt.android.flags.FragmentGetContextFix
import dagger.hilt.internal.GeneratedComponent
import dagger.hilt.internal.GeneratedComponentManager

/** Supplies Hilt's application flag entry point while the harness supplies the fragment dependencies. */
class HiltHostApplication : Application(), GeneratedComponent, GeneratedComponentManager<Any>,
    FragmentGetContextFix.FragmentGetContextFixEntryPoint {

    override fun generatedComponent(): Any = this

    override fun getDisableFragmentGetContextFix(): Set<Boolean> = emptySet()
}
