package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.confirm.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.confirm.ConfirmContributeFragment
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.confirm.parcel.ConfirmContributePayload

@Subcomponent(
    modules = [
        ConfirmContributeModule::class
    ]
)
@ScreenScope
interface ConfirmContributeComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(
            @BindsInstance fragment: Fragment,
            @BindsInstance payload: ConfirmContributePayload
        ): ConfirmContributeComponent
    }

    fun inject(fragment: ConfirmContributeFragment)
}
