package jp.co.soramitsu.app.root.domain

import jp.co.soramitsu.app.root.data.runtime.RuntimePreparationStatus
import jp.co.soramitsu.app.root.data.runtime.RuntimeUpdater
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.BuyTokenRegistry
import jp.co.soramitsu.feature_wallet_impl.data.buyToken.ExternalProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import java.util.Locale


class RootInteractor(
    private val accountRepository: AccountRepository,
    private val buyTokenRegistry: BuyTokenRegistry,
    private val walletRepository: WalletRepository,
    private val runtimeUpdater: RuntimeUpdater
) {
    fun selectedNodeFlow() = accountRepository.selectedNodeFlow()

    suspend fun listenForRuntimeUpdates(networkType: Node.NetworkType): Flow<RuntimePreparationStatus> {
        return runtimeUpdater.listenRuntimeChanges(networkType.networkName())
    }

    suspend fun manualRuntimeUpdate(): RuntimePreparationStatus {
        return runtimeUpdater.manualRuntimeUpdate(networkNameForRuntimeUpdate())
    }

    suspend fun listenForAccountUpdates(networkType: Node.NetworkType) {
        accountRepository.selectedAccountFlow()
            .filter { it.network.type == networkType }
            .distinctUntilChanged { old, new -> old.address == new.address }
            .flowOn(Dispatchers.IO)
            .collectLatest { walletRepository.listenForUpdates(it) }
    }

    fun isBuyProviderRedirectLink(link: String) = buyTokenRegistry.availableProviders
        .filterIsInstance<ExternalProvider>()
        .any { it.redirectLink == link }

    private suspend fun networkNameForRuntimeUpdate(): String {
        val networkType = accountRepository.getSelectedNode().networkType

        return networkType.networkName()
    }

    private fun Node.NetworkType.networkName() = readableName.toLowerCase(Locale.ROOT)
}