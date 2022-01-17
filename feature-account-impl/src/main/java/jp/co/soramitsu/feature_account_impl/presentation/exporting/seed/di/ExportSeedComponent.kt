package jp.co.soramitsu.feature_account_impl.presentation.exporting.seed.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_account_impl.presentation.exporting.seed.ExportSeedFragment
import jp.co.soramitsu.feature_account_impl.presentation.exporting.seed.ExportSeedPayload

@Subcomponent(
    modules = [
        ExportSeedModule::class
    ]
)
@ScreenScope
interface ExportSeedComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(
            @BindsInstance fragment: Fragment,
            @BindsInstance payload: ExportSeedPayload
        ): ExportSeedComponent
    }

    fun inject(fragment: ExportSeedFragment)
}
