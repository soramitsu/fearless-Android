package jp.co.soramitsu.feature_crowdloan_impl.presentation.main.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_crowdloan_impl.presentation.main.CrowdloanFragment

@Subcomponent(
    modules = [
        CrowdloanModule::class
    ]
)
@ScreenScope
interface CrowdloanComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(
            @BindsInstance fragment: Fragment,
        ): CrowdloanComponent
    }

    fun inject(fragment: CrowdloanFragment)
}
