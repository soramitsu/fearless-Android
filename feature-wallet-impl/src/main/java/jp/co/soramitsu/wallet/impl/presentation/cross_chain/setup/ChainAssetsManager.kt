package jp.co.soramitsu.wallet.impl.presentation.cross_chain.setup

import javax.inject.Inject
import jp.co.soramitsu.common.compose.component.SelectorState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.core.utils.removedXcPrefix
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.balance.chainselector.ChainSelectScreenContract
import jp.co.soramitsu.xcm.domain.CrossChainRouteCapability
import jp.co.soramitsu.xcm.domain.CrossChainRouteProviderRegistry
import jp.co.soramitsu.xcm.domain.CrossChainRouteQuery
import jp.co.soramitsu.xcm.domain.XcmEntitiesFetcher
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
import jp.co.soramitsu.wallet.api.presentation.WalletRouter as WalletRouterApi

class ChainAssetsManager @Inject constructor(
    private val walletInteractor: WalletInteractor,
    resourceManager: ResourceManager,
    private val router: WalletRouter,
    private val xcmEntitiesFetcher: XcmEntitiesFetcher,
    private val routeProviderRegistry: CrossChainRouteProviderRegistry
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
            val originChainId = originChainIdFlow.value ?: return@onEach
            val actualDestinationChainId = getActualDestinationChainId(
                originChainId = originChainId,
                asset = asset,
                destinationChainId = null
            )
            updateDestinationChainId(actualDestinationChainId)
        }
    val assetSymbol: String? get() = lastAsset?.token?.configuration?.symbol

    val destinationChainId: String? get() = destinationChainIdFlow.value

    suspend fun routeCapability(): CrossChainRouteCapability = routeProviderRegistry.bestCapability(
        CrossChainRouteQuery(
            originNetworkId = originChainIdFlow.value,
            destinationNetworkId = destinationChainIdFlow.value,
            originAssetId = assetIdFlow.value,
            assetSymbol = assetSymbol
        )
    )

    suspend fun hasAvailableRoute(): Boolean = routeCapability().hasRoute

    suspend fun routeInventory(): List<CrossChainRouteInventoryItem> =
        buildCrossChainRouteInventory(
            capabilities = routeProviderRegistry.inventoryCapabilities(),
            networkName = { chainId ->
                runCatching { walletInteractor.getChain(chainId).name }
                    .getOrDefault(chainId)
            }
        )

    val originSelectedChain = originChainIdFlow.map { chainId ->
        chainId?.let { walletInteractor.getChain(it) }
    }

    private val originSelectedChainItem = originSelectedChain.map { chain ->
        chain?.let {
            ChainSelectScreenContract.State.ItemState.Impl(
                id = chain.id,
                imageUrl = chain.icon,
                title = chain.name,
                isSelected = false,
                tokenSymbols = chain.assets.associate { it.id to it.symbol }
            )
        }
    }

    val destinationSelectedChainFlow = destinationChainIdFlow.map { chainId ->
        chainId?.let { walletInteractor.getChain(it) }
    }

    private val destinationSelectedChainItem = destinationSelectedChainFlow.map { chain ->
        chain?.let {
            ChainSelectScreenContract.State.ItemState.Impl(
                id = chain.id,
                imageUrl = chain.icon,
                title = chain.name,
                isSelected = false,
                tokenSymbols = chain.assets.associate { it.id to it.symbol }
            )
        }
    }

    val originChainSelectorStateFlow = originSelectedChainItem.map {
        SelectorState(
            title = resourceManager.getString(R.string.common_origin_network),
            subTitle = it?.title,
            iconUrl = it?.imageUrl,
            actionIcon = null
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

    private suspend fun updateOriginChainId(chainId: ChainId) {
        if (originChainIdFlow.value == chainId) return

        assetIdFlow.value = getActualAssetId(
            originChainId = chainId,
            assetId = assetIdFlow.value
        )
        originChainIdFlow.value = chainId
        destinationChainIdFlow.value = getActualDestinationChainId(
            originChainId = chainId,
            asset = lastAsset,
            destinationChainId = destinationChainId
        )
    }

    private suspend fun getActualAssetId(originChainId: ChainId, assetId: String?): String? {
        val supportedXcmAssets = xcmEntitiesFetcher.getAvailableAssets(
            originChainId = originChainId,
            destinationChainId = null
        )
        val providerAssets = routeProviderRegistry.capabilities(
            CrossChainRouteQuery(originNetworkId = originChainId)
        ).filter { it.hasRoute }.flatMap { it.supportedAssets }

        val xcmAssets = walletInteractor.assetsFlow().first()
            .map { it.asset.token.configuration }
            .filter { asset ->
                asset.chainId == originChainId && (
                    supportedXcmAssets.any { approved ->
                        asset.chainId == approved.originChainId &&
                            asset.id == approved.originAssetId &&
                            asset.precision == approved.originAssetPrecision &&
                            asset.symbol.normalizedXcmSymbol() == approved.symbol
                    } || providerAssets.any { reviewed ->
                        reviewed.originNetworkId == asset.chainId && reviewed.originAssetId == asset.id &&
                            reviewed.symbol == asset.symbol.uppercase()
                    }
                )
            }
        val xcmAssetIds = xcmAssets.map { it.id }
        val utilityXcmAssetId = xcmAssets.firstOrNull { it.isUtility }?.id

        return selectReachableCrossChainAssetId(assetId, xcmAssetIds, utilityXcmAssetId)
    }

    private fun String.normalizedXcmSymbol(): String = trim()
        .lowercase()
        .removedXcPrefix()
        .uppercase()

    private suspend fun getActualDestinationChainId(
        originChainId: ChainId,
        asset: Asset?,
        destinationChainId: ChainId?
    ): ChainId? {
        val availableDestinationChainIds = xcmEntitiesFetcher.getAvailableDestinationChains(
            originChainId = originChainId,
            assetSymbol = asset?.token?.configuration?.symbol?.uppercase(),
            originAssetId = asset?.token?.configuration?.id
        )
        val providerDestinationChainIds = routeProviderRegistry.capabilities(
            CrossChainRouteQuery(
                originNetworkId = originChainId,
                originAssetId = asset?.token?.configuration?.id,
                assetSymbol = asset?.token?.configuration?.symbol
            )
        ).filter { it.hasRoute }.flatMap { it.supportedDestinationNetworkIds }
        val availableChainIds = (availableDestinationChainIds + providerDestinationChainIds).distinct()

        return destinationChainId.takeIf {
            destinationChainId in availableChainIds
        } ?: availableChainIds.firstOrNull()
    }

    suspend fun setInitialIds(chainId: ChainId, assetId: String) {
        updateOriginChainId(chainId)
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
                    ChainType.Origin -> {
                        updateOriginChainId(chainId)
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

internal fun selectReachableCrossChainAssetId(
    requestedAssetId: String?,
    supportedAssetIds: List<String>,
    supportedUtilityAssetId: String?
): String? = requestedAssetId.takeIf { it in supportedAssetIds }
    ?: supportedUtilityAssetId.takeIf { it in supportedAssetIds }
    ?: supportedAssetIds.firstOrNull()
