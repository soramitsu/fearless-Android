package jp.co.soramitsu.feature_wallet_impl.presentation.send.confirm.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_wallet_impl.presentation.send.TransferDraft
import jp.co.soramitsu.feature_wallet_impl.presentation.send.confirm.ConfirmTransferFragment

@Subcomponent(
    modules = [
        ConfirmTransferModule::class
    ]
)
@ScreenScope
interface ConfirmTransferComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(
            @BindsInstance fragment: Fragment,
            @BindsInstance transferDraft: TransferDraft
        ): ConfirmTransferComponent
    }

    fun inject(fragment: ConfirmTransferFragment)
}