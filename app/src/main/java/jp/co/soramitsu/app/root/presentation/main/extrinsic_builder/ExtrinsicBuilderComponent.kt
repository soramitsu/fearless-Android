package jp.co.soramitsu.app.root.presentation.main.extrinsic_builder

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope

@Subcomponent(
    modules = [
        ExtrinsicBuilderModule::class
    ]
)
@ScreenScope
interface ExtrinsicBuilderComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(@BindsInstance fragment: Fragment): ExtrinsicBuilderComponent
    }

    fun inject(fragment: ExtrinsicBuilderFragment)
}
