package jp.co.soramitsu.app.root.di

import jp.co.soramitsu.common.data.network.AppLinksProvider
import jp.co.soramitsu.common.mixin.api.NetworkStateMixin
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.updater.UpdateSystem
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_crowdloan_api.data.repository.CrowdloanRepository
import jp.co.soramitsu.feature_staking_api.domain.api.StakingRepository
import jp.co.soramitsu.feature_wallet_api.di.Wallet
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.BuyTokenRegistry
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.connection.ChainConnection
import kotlinx.coroutines.flow.MutableStateFlow

interface RootDependencies {

    fun crowdloanRepository(): CrowdloanRepository

    fun networkStateMixin(): NetworkStateMixin

    fun externalRequirementsFlow(): MutableStateFlow<ChainConnection.ExternalRequirement>

    fun accountRepository(): AccountRepository

    fun walletRepository(): WalletRepository

    fun appLinksProvider(): AppLinksProvider

    fun buyTokenRegistry(): BuyTokenRegistry

    fun resourceManager(): ResourceManager

    @Wallet
    fun walletUpdateSystem(): UpdateSystem

    fun stakingRepository(): StakingRepository

    fun chainRegistry(): ChainRegistry
}
