package jp.co.soramitsu.app.root.di

import jp.co.soramitsu.common.data.network.AppLinksProvider
import jp.co.soramitsu.common.data.network.rpc.ConnectionManager
import jp.co.soramitsu.common.mixin.api.NetworkStateMixin
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.updaters.CrowdloanUpdaters
import jp.co.soramitsu.feature_crowdloan_api.data.repository.CrowdloanRepository
import jp.co.soramitsu.feature_staking_api.di.StakingUpdaters
import jp.co.soramitsu.feature_staking_api.domain.api.StakingRepository
import jp.co.soramitsu.feature_wallet_api.di.WalletUpdaters
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.BuyTokenRegistry
import jp.co.soramitsu.runtime.RuntimeUpdater

interface RootDependencies {

    fun crowdloanRepository(): CrowdloanRepository

    fun networkStateMixin(): NetworkStateMixin

    fun connectionManager(): ConnectionManager

    fun accountRepository(): AccountRepository

    fun walletRepository(): WalletRepository

    fun appLinksProvider(): AppLinksProvider

    fun buyTokenRegistry(): BuyTokenRegistry

    fun resourceManager(): ResourceManager

    fun walletUpdaters(): WalletUpdaters

    fun stakingUpdaters(): StakingUpdaters

    fun crowdloanUpdaters(): CrowdloanUpdaters

    fun runtimeProperty(): SuspendableProperty<RuntimeSnapshot>

    fun stakingRepository(): StakingRepository

    fun runtimeUpdater(): RuntimeUpdater

    fun socketService(): SocketService
}
