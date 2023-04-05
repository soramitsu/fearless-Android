package jp.co.soramitsu.wallet.impl.presentation.cross_chain

import jp.co.soramitsu.common.compose.component.SelectorState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.runtime.ext.isValidAddress
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.balance.chainselector.ChainItemState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import jp.co.soramitsu.wallet.api.presentation.WalletRouter as WalletRouterApi

class ChainAssetsManager @Inject constructor(
    private val walletInteractor: WalletInteractor,
    resourceManager: ResourceManager,
    private val router: WalletRouter
) {
    private var chainAssetResultJob: Job? = null
    private val assetIdToOriginalChainIdFlow = MutableStateFlow<Pair<String, ChainId>?>(null)

    val assetIdFlow: Flow<String?> = assetIdToOriginalChainIdFlow.map { it?.first }
    val originalChainIdFlow: Flow<ChainId?> = assetIdToOriginalChainIdFlow.map { it?.second }
    val destinationChainIdFlow = MutableStateFlow<ChainId?>(value = null)

    val assetFlow: Flow<Asset?> =
        assetIdToOriginalChainIdFlow.map {
            it?.let { (assetId, chainId) ->
                try {
                    walletInteractor.getCurrentAsset(chainId, assetId)
                } catch (e: Exception) {
                    println("Exception: ${e.message}")
                    null
                }
            }
        }

    val destinationChainId: String? get() = destinationChainIdFlow.value

    val originalSelectedChain = originalChainIdFlow.map { chainId ->
        chainId?.let { walletInteractor.getChain(it) }
    }

    private val originalSelectedChainItem = originalSelectedChain.map { chain ->
        chain?.let {
            ChainItemState(
                id = chain.id,
                imageUrl = chain.icon,
                title = chain.name,
                isSelected = false,
                tokenSymbols = chain.assets.associate { it.id to it.symbolToShow }
            )
        }
    }

    private val destinationSelectedChain = destinationChainIdFlow.map { chainId ->
        chainId?.let { walletInteractor.getChain(it) }
    }

    private val destinationSelectedChainItem = destinationSelectedChain.map { chain ->
        chain?.let {
            ChainItemState(
                id = chain.id,
                imageUrl = chain.icon,
                title = chain.name,
                isSelected = false,
                tokenSymbols = chain.assets.associate { it.id to it.symbolToShow }
            )
        }
    }

    val originalChainSelectorStateFlow = originalSelectedChainItem.map {
        SelectorState(
            title = resourceManager.getString(R.string.common_original_network),
            subTitle = it?.title,
            iconUrl = it?.imageUrl
        )
    }

    val destinationChainSelectorStateFlow = destinationSelectedChainItem.map {
        SelectorState(
            title = resourceManager.getString(R.string.common_destination_network),
            subTitle = it?.title,
            iconUrl = it?.imageUrl
        )
    }

    private fun updateAssetId(assetId: String) {
        val chainId = assetIdToOriginalChainIdFlow.value?.second
        chainId?.let {
            assetIdToOriginalChainIdFlow.value = assetId to chainId
        }
    }

    private fun updateOriginalChainId(chainId: String) {
        val assetId = assetIdToOriginalChainIdFlow.value?.first
        assetId?.let {
            assetIdToOriginalChainIdFlow.value = assetId to chainId
        }
    }

    fun setInitialChainsAndAssetIds(chainId: ChainId, assetId: String) {
        updateOriginalChainIdAndAsset(
            chainId = chainId,
            assetId = assetId
        )
        updateDestinationChainId(
            chainId = chainId
        )
    }

    private fun updateDestinationChainId(chainId: String) {
        destinationChainIdFlow.value = chainId
    }

    private fun updateOriginalChainIdAndAsset(chainId: ChainId, assetId: String) {
        assetIdToOriginalChainIdFlow.value = assetId to chainId
    }

    private fun observeAssetIdResult(): Flow<String> {
        return router.observeResult<String>(WalletRouterApi.KEY_ASSET_ID)
            .onEach(::updateAssetId)
    }

    private fun observeChainIdResult(chainTypes: Array<out ChainType>): Flow<String> {
        return router.observeResult<String>(WalletRouterApi.KEY_CHAIN_ID)
            .onEach { chainId ->
                chainTypes.forEach { chainType ->
                    when (chainType) {
                        ChainType.Original -> updateOriginalChainId(chainId)
                        ChainType.Destination -> updateDestinationChainId(chainId)
                    }
                }
            }
    }

    fun observeChainIdAndAssetIdResult(
        scope: CoroutineScope,
        vararg chainTypes: ChainType,
        onError: (throwable: Throwable) -> Unit
    ) {
        val chainIdResultFlow = observeChainIdResult(chainTypes)
        val assetIdResultFlow = observeAssetIdResult()

        chainAssetResultJob?.cancel()
        chainAssetResultJob = combine(chainIdResultFlow, assetIdResultFlow) { _, _ ->
            chainAssetResultJob?.cancel()
        }
            .catch { onError(it) }
            .launchIn(scope)
    }

    suspend fun findChainsForAddress(address: String?, tokenCurrencyId: String?) {
        if (address.isNullOrEmpty()) return

        val chains = walletInteractor.getChains().first()
        val addressChains = chains.filter {
            it.isValidAddress(address)
        }
        when (addressChains.size) {
            1 -> {
                val chain = addressChains[0]
                val assets = chain.assets
                when (assets.size) {
                    1 -> {
                        setInitialChainsAndAssetIds(chain.id, assets[0].id)
                    }
                    else -> {
                        router.openSelectChainAsset(chain.id)
                    }
                }
            }
            else -> {
                router.openSelectChain(
                    filterChainIds = addressChains.map { it.id },
                    chooserMode = false,
                    currencyId = tokenCurrencyId,
                    showAllChains = false
                )
            }
        }
    }
}
