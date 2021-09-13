package jp.co.soramitsu.feature_wallet_impl.presentation.transaction.filter.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.filter.TransactionHistoryFilterFragment

@Subcomponent(
    modules = [
        TransactionHistoryFilterModule::class
    ]
)
@ScreenScope
interface TransactionHistoryFilterComponent {

    @Subcomponent.Factory
    interface Factory {
        fun create(
            @BindsInstance fragment: Fragment
        ): TransactionHistoryFilterComponent
    }

    fun inject(fragment: TransactionHistoryFilterFragment)
}
