package jp.co.soramitsu.feature_wallet_impl.presentation.send.recipient.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_wallet_impl.presentation.send.recipient.ChooseRecipientFragment

@Subcomponent(
    modules = [
        ChooseRecipientModule::class
    ]
)
@ScreenScope
interface ChooseRecipientComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(
            @BindsInstance fragment: Fragment
        ): ChooseRecipientComponent
    }

    fun inject(fragment: ChooseRecipientFragment)
}