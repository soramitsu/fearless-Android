package jp.co.soramitsu.feature_wallet_impl.di

import dagger.BindsInstance
import dagger.Component
import jp.co.soramitsu.common.di.CommonApi
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.core_db.di.DbApi
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_wallet_impl.presentation.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.balance.detail.di.BalanceDetailComponent
import jp.co.soramitsu.feature_wallet_impl.presentation.balance.list.di.BalanceListComponent
import jp.co.soramitsu.feature_wallet_impl.presentation.send.amount.di.ChooseAmountComponent
import jp.co.soramitsu.feature_wallet_impl.presentation.send.recipient.di.ChooseRecipientComponent

@Component(
    dependencies = [
        WalletFeatureDependencies::class
    ],
    modules = [
        WalletFeatureModule::class
    ]
)
@FeatureScope
interface WalletFeatureComponent : AccountFeatureApi {

    fun balanceListComponentFactory(): BalanceListComponent.Factory

    fun balanceDetailComponentFactory(): BalanceDetailComponent.Factory

    fun chooseRecipientComponentFactory(): ChooseRecipientComponent.Factory

    fun chooseAmountComponentFactory(): ChooseAmountComponent.Factory

    @Component.Factory
    interface Factory {

        fun create(
            @BindsInstance accountRouter: WalletRouter,
            deps: WalletFeatureDependencies
        ): WalletFeatureComponent
    }

    @Component(
        dependencies = [
            CommonApi::class,
            DbApi::class,
            AccountFeatureApi::class
        ]
    )
    interface WalletFeatureDependenciesComponent : WalletFeatureDependencies
}