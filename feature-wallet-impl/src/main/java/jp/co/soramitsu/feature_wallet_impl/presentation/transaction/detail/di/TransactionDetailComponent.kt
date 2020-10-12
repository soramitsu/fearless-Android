package jp.co.soramitsu.feature_wallet_impl.presentation.transaction.detail.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_wallet_impl.presentation.model.TransactionModel
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.detail.TransactionDetailFragment

@Subcomponent(
    modules = [
        TransactionDetailModule::class
    ]
)
@ScreenScope
interface TransactionDetailComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(
            @BindsInstance fragment: Fragment,
            @BindsInstance transactionModel: TransactionModel
        ): TransactionDetailComponent
    }

    fun inject(fragment: TransactionDetailFragment)
}