package jp.co.soramitsu.app.root.domain

import jp.co.soramitsu.common.data.network.runtime.RuntimeHolder
import jp.co.soramitsu.common.data.network.runtime.RuntimeProvider
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.BuyTokenRegistry
import jp.co.soramitsu.feature_wallet_impl.data.buyToken.ExternalProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import java.util.Locale

enum class RuntimePreparationStatus {
    OK, OUTDATED, ERROR
}

class RootInteractor(
    private val accountRepository: AccountRepository,
    private val buyTokenRegistry: BuyTokenRegistry,
    private val walletRepository: WalletRepository,
    private val runtimeHolder: RuntimeHolder,
    private val runtimeProvider: RuntimeProvider
) {
    fun selectedNodeFlow() = accountRepository.selectedNodeFlow()

    suspend fun prepareRuntime() : RuntimePreparationStatus {
        val networkType = accountRepository.getSelectedNode().networkType

        val networkName = networkType.readableName.toLowerCase(Locale.ROOT)

        return try {
            runtimeHolder.invalidate()

            val prepared = runtimeProvider.prepareRuntime(networkName)
            runtimeHolder.set(prepared.runtime)

            if (prepared.isNewest) RuntimePreparationStatus.OK else RuntimePreparationStatus.OUTDATED
        } catch (_: Exception) {
            RuntimePreparationStatus.ERROR
        }
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
}