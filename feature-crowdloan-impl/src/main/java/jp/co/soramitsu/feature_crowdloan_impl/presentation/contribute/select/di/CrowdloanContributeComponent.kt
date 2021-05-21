package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.select.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.select.CrowdloanContributeFragment
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.select.parcel.ContributePayload

@Subcomponent(
    modules = [
        CrowdloanContributeModule::class
    ]
)
@ScreenScope
interface CrowdloanContributeComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(
            @BindsInstance fragment: Fragment,
            @BindsInstance payload: ContributePayload
        ): CrowdloanContributeComponent
    }

    fun inject(fragment: CrowdloanContributeFragment)
}
