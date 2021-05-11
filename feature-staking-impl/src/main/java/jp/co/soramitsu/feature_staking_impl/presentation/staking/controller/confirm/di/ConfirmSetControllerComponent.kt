package jp.co.soramitsu.feature_staking_impl.presentation.staking.controller.confirm.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_staking_impl.presentation.staking.controller.confirm.ConfirmSetControllerFragment
import jp.co.soramitsu.feature_staking_impl.presentation.staking.controller.confirm.ConfirmSetControllerPayload

@Subcomponent(
    modules = [
        ConfirmSetControllerModule::class
    ]
)
@ScreenScope
interface ConfirmSetControllerComponent {

    @Subcomponent.Factory
    interface Factory {
        fun create(
            @BindsInstance fragment: Fragment,
            @BindsInstance payload: ConfirmSetControllerPayload,
        ): ConfirmSetControllerComponent
    }

    fun inject(fragment: ConfirmSetControllerFragment)
}
