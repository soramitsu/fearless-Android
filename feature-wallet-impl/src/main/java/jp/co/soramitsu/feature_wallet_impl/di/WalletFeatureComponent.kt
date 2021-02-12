package jp.co.soramitsu.feature_wallet_impl.di

import dagger.BindsInstance
import dagger.Component
import jp.co.soramitsu.common.di.CommonApi
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.core_db.di.DbApi
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.feature_wallet_impl.presentation.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.balance.detail.di.BalanceDetailComponent
import jp.co.soramitsu.feature_wallet_impl.presentation.balance.list.di.BalanceListComponent
import jp.co.soramitsu.feature_wallet_impl.presentation.receive.di.ReceiveComponent
import jp.co.soramitsu.feature_wallet_impl.presentation.send.amount.di.ChooseAmountComponent
import jp.co.soramitsu.feature_wallet_impl.presentation.send.confirm.di.ConfirmTransferComponent
import jp.co.soramitsu.feature_wallet_impl.presentation.send.recipient.di.ChooseRecipientComponent
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.detail.di.TransactionDetailComponent
import jp.co.soramitsu.runtime.di.RuntimeApi

@Component(
    dependencies = [
        WalletFeatureDependencies::class
    ],
    modules = [
        WalletFeatureModule::class
    ]
)
@FeatureScope
interface WalletFeatureComponent : WalletFeatureApi {

    fun balanceListComponentFactory(): BalanceListComponent.Factory

    fun balanceDetailComponentFactory(): BalanceDetailComponent.Factory

    fun chooseRecipientComponentFactory(): ChooseRecipientComponent.Factory

    fun chooseAmountComponentFactory(): ChooseAmountComponent.Factory

    fun confirmTransferComponentFactory(): ConfirmTransferComponent.Factory

    fun transactionDetailComponentFactory(): TransactionDetailComponent.Factory

    fun receiveComponentFactory(): ReceiveComponent.Factory

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
            RuntimeApi::class,
            AccountFeatureApi::class
        ]
    )
    interface WalletFeatureDependenciesComponent : WalletFeatureDependencies
}