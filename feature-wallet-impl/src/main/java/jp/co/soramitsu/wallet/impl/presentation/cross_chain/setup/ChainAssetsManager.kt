package jp.co.soramitsu.wallet.impl.presentation.cross_chain.setup

import jp.co.soramitsu.common.compose.component.SelectorState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.balance.chainselector.ChainItemState
import jp.co.soramitsu.xcm_impl.domain.XcmEntitiesFetcher
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
    private val router: WalletRouter,
    private val xcmEntitiesFetcher: XcmEntitiesFetcher
) {
    private var chainAssetResultJob: Job? = null
    val assetIdFlow = MutableStateFlow<String?>(null)
    val originChainIdFlow = MutableStateFlow<ChainId?>(null)
    val destinationChainIdFlow = MutableStateFlow<ChainId?>(null)

    private var lastAsset: Asset? = null
    val assetFlow: Flow<Asset?> = combine(originChainIdFlow, assetIdFlow) { chainId, assetId ->
        if (chainId == null || assetId == null) return@combine null

        runCatching { walletInteractor.getCurrentAsset(chainId, assetId) }
            .onFailure { println("Exception: ${it.message}") }
            .getOrNull()
    }
        .onEach { lastAsset = it }
        .onEach {
            val asset = it ?: return@onEach
            val originalChainId = originChainIdFlow.value ?: return@onEach
            val actualDestinationChainId = getActualDestinationChainId(
                originalChainId = originalChainId,
                asset = asset,
                destinationChainId = null
            )
            updateDestinationChainId(actualDestinationChainId)
        }
    val assetSymbol: String? get() = lastAsset?.token?.configuration?.symbol

    val destinationChainId: String? get() = destinationChainIdFlow.value

    val originalSelectedChain = originChainIdFlow.map { chainId ->
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
            iconUrl = it?.imageUrl,
            actionIcon = null,
            clickable = false
        )
    }

    val destinationChainSelectorStateFlow = destinationSelectedChainItem.map {
        SelectorState(
            title = resourceManager.getString(R.string.common_destination_network),
            subTitle = it?.title,
            iconUrl = it?.imageUrl
        )
    }

    private fun updateAssetId(assetId: String?) {
        assetIdFlow.value = assetId
    }

    private suspend fun updateOriginalChainId(chainId: ChainId) {
        if (originChainIdFlow.value == chainId) return

        assetIdFlow.value = getActualAssetId(
            originalChainId = chainId,
            assetId = assetIdFlow.value
        )
        originChainIdFlow.value = chainId
        destinationChainIdFlow.value = getActualDestinationChainId(
            originalChainId = chainId,
            asset = lastAsset,
            destinationChainId = destinationChainId
        )
    }

    private suspend fun getActualAssetId(originalChainId: ChainId, assetId: String?): String? {
        val supportedXcmAssetSymbols = xcmEntitiesFetcher.getAvailableAssets(
            originalChainId = originalChainId,
            destinationChainId = null
        ).map { it.uppercase() }

        val xcmAssets = walletInteractor.assetsFlow().first()
            .map { it.asset.token.configuration }
            .filter { it.chainId == originalChainId && it.symbol.uppercase() in supportedXcmAssetSymbols }
        val xcmAssetIds = xcmAssets.map { it.id }
        val utilityXcmAssetId = xcmAssets.firstOrNull { it.isUtility }?.id

        return if (assetId in xcmAssetIds) {
            assetId
        } else {
            utilityXcmAssetId
        }
    }

    private suspend fun getActualDestinationChainId(
        originalChainId: ChainId,
        asset: Asset?,
        destinationChainId: ChainId?
    ): ChainId? {
        val availableDestinationChainIds = xcmEntitiesFetcher.getAvailableDestinationChains(
            originalChainId = originalChainId,
            assetSymbol = asset?.token?.configuration?.symbol?.uppercase()
        )

        return destinationChainId.takeIf {
            destinationChainId in availableDestinationChainIds
        } ?: availableDestinationChainIds.firstOrNull()
    }

    suspend fun setInitialIds(chainId: ChainId, assetId: String) {
        updateOriginalChainId(chainId)
        updateAssetId(assetId)
    }

    private fun updateDestinationChainId(chainId: String?) {
        destinationChainIdFlow.value = chainId
    }

    private fun observeAssetIdResult(): Flow<String> {
        return router.observeResult<String>(WalletRouterApi.KEY_ASSET_ID)
            .onEach(::updateAssetId)
    }

    private fun observeChainIdResult(chainType: ChainType): Flow<String> {
        return router.observeResult<String>(WalletRouterApi.KEY_CHAIN_ID)
            .onEach { chainId ->
                when (chainType) {
                    ChainType.Original -> {
                        updateOriginalChainId(chainId)
                    }
                    ChainType.Destination -> {
                        updateDestinationChainId(chainId)
                    }
                }
            }
    }

    fun observeChainIdAndAssetIdResult(
        scope: CoroutineScope,
        chainType: ChainType,
        onError: (throwable: Throwable) -> Unit
    ) {
        val chainIdResultFlow = observeChainIdResult(chainType)
        val assetIdResultFlow = observeAssetIdResult()

        chainAssetResultJob?.cancel()
        chainAssetResultJob = combine(chainIdResultFlow, assetIdResultFlow) { _, _ ->
            chainAssetResultJob?.cancel()
        }
            .catch { onError(it) }
            .launchIn(scope)
    }
}
