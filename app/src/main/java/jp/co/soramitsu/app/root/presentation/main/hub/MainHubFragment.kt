package jp.co.soramitsu.app.root.presentation.main.hub

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.app.R
import jp.co.soramitsu.common.base.BaseComposeFragment
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.B0
import jp.co.soramitsu.common.compose.component.GrayButton
import jp.co.soramitsu.common.compose.component.H1
import jp.co.soramitsu.common.compose.component.H3
import jp.co.soramitsu.common.compose.theme.white50
import jp.co.soramitsu.polkaswap.api.presentation.PolkaswapRouter
import jp.co.soramitsu.polkaswap.api.domain.PolkaswapInteractor
import jp.co.soramitsu.polkamarkt.api.PolkamarktRouter
import jp.co.soramitsu.polkamarkt.api.PolkamarktInteractor
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.account.api.domain.model.hasChainAccount
import jp.co.soramitsu.common.data.network.config.ProductFeatureToggleStore
import jp.co.soramitsu.common.model.DeFiAccountRequirement
import jp.co.soramitsu.common.model.DeFiAvailability
import jp.co.soramitsu.common.model.DeFiCapability
import jp.co.soramitsu.common.model.DeFiSigningRequirement
import jp.co.soramitsu.liquiditypools.domain.interfaces.DemeterFarmingInteractor
import jp.co.soramitsu.liquiditypools.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.xcm.domain.CrossChainRouteCapability
import jp.co.soramitsu.xcm.domain.CrossChainRouteProviderRegistry
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigInteger

private const val ARG_HUB_TYPE = "hub_type"
private const val DEFI_STAKING = "staking"
private const val DEFI_POOLS = "liquidity-pools"
private const val DEFI_FARMING = "demeter-farming"
private const val DEFI_POLKAMARKT = "polkamarkt"
private const val DEFI_POLKASWAP = "polkaswap"

enum class MainHubType(val wireValue: String) {
    Defi("defi"),
    Polkaswap("polkaswap"),
    CrossChain("cross_chain");

    companion object {
        fun fromWireValue(value: String?): MainHubType =
            entries.firstOrNull { it.wireValue == value } ?: Polkaswap
    }
}

@HiltViewModel
class MainHubViewModel @Inject constructor(
    savedStateHandle: androidx.lifecycle.SavedStateHandle,
    private val walletRouter: WalletRouter,
    private val polkaswapRouter: PolkaswapRouter,
    private val polkamarktRouter: PolkamarktRouter,
    private val polkamarktInteractor: PolkamarktInteractor,
    private val polkaswapInteractor: PolkaswapInteractor,
    private val poolsInteractor: PoolsInteractor,
    private val demeterFarmingInteractor: DemeterFarmingInteractor,
    private val accountRepository: AccountRepository,
    private val chainRegistry: ChainRegistry,
    private val walletRepository: WalletRepository,
    private val featureToggleStore: ProductFeatureToggleStore,
    private val crossChainRouteProviders: CrossChainRouteProviderRegistry
) : BaseViewModel() {

    val hubType = MainHubType.fromWireValue(savedStateHandle[ARG_HUB_TYPE])
    private val mutablePolkaswapCapability = MutableStateFlow(
        PolkaswapCapability(DeFiCapability.checking(DEFI_POLKASWAP))
    )
    val polkaswapCapability: StateFlow<PolkaswapCapability> = mutablePolkaswapCapability
    private val mutableDefiCapabilities = MutableStateFlow(
        mapOf(
            DEFI_STAKING to DeFiCapability.checking(DEFI_STAKING),
            DEFI_POOLS to DeFiCapability.checking(DEFI_POOLS),
            DEFI_FARMING to DeFiCapability.checking(DEFI_FARMING),
            DEFI_POLKAMARKT to DeFiCapability.checking(DEFI_POLKAMARKT)
        )
    )
    val defiCapabilities: StateFlow<Map<String, DeFiCapability>> = mutableDefiCapabilities
    private val mutableDefiPositions = MutableStateFlow(DeFiPositionsSummary())
    val defiPositions: StateFlow<DeFiPositionsSummary> = mutableDefiPositions
    private val mutableCrossChainCapability = MutableStateFlow<CrossChainRouteCapability?>(null)
    val crossChainCapability: StateFlow<CrossChainRouteCapability?> = mutableCrossChainCapability

    init {
        when (hubType) {
            MainHubType.Polkaswap -> {
                refreshPolkaswapCapability()
                polkaswapInteractor.observeHasReadDisclaimer().onEach {
                    refreshPolkaswapCapability()
                }.launchIn(viewModelScope)
            }
            MainHubType.Defi -> {
                refreshDefiCapabilities()
                refreshDefiPositions()
            }
            MainHubType.CrossChain -> refreshCrossChainCapability()
        }
    }

    fun onPrimaryAction() {
        when (hubType) {
            MainHubType.Defi -> Unit
            MainHubType.Polkaswap -> {
                val capability = mutablePolkaswapCapability.value
                if (!capability.canOpenAction) return
                if (!capability.disclaimerAccepted) {
                    polkaswapRouter.openPolkaswapDisclaimerFromMainScreen()
                } else {
                    walletRouter.openSwapTokensScreen(
                        chainId = polkaswapInteractor.polkaswapChainId,
                        assetIdFrom = null,
                        assetIdTo = null
                    )
                }
            }

            MainHubType.CrossChain -> walletRouter.openCrossChainSend(assetPayload = null)
        }
    }

    fun canOpenStaking(): Boolean =
        mutableDefiCapabilities.value[DEFI_STAKING]?.canOpenDestination == true

    fun onSecondaryAction() {
        val canOpen = when (hubType) {
            MainHubType.Polkaswap -> mutablePolkaswapCapability.value.defiCapability.canOpenDestination
            MainHubType.Defi -> mutableDefiCapabilities.value[DEFI_POOLS]?.canOpenDestination == true
            MainHubType.CrossChain -> false
        }
        if (canOpen) {
            polkaswapRouter.openPools()
        }
    }

    fun onFarmingAction() {
        if (mutableDefiCapabilities.value[DEFI_FARMING]?.canOpenDestination == true) {
            polkaswapRouter.openDemeterFarming()
        }
    }

    fun onPolkamarktAction() {
        if (mutableDefiCapabilities.value[DEFI_POLKAMARKT]?.canOpenDestination == true) {
            polkamarktRouter.openPolkamarkt()
        }
    }

    private fun refreshPolkaswapCapability() {
        viewModelScope.launch {
            mutablePolkaswapCapability.value = runCatching {
                val context = loadSoraContext()
                val assetReason = when {
                    context.capability.availability != DeFiAvailability.Available -> null
                    context.assets.none { it.transferable > java.math.BigDecimal.ZERO } ->
                        "Fund a SORA asset before swapping."
                    context.assets.size < 2 -> "Add another supported SORA asset before swapping."
                    else -> null
                }
                val capability = if (assetReason == null) {
                    buildSoraCapability(
                        featureId = DEFI_POLKASWAP,
                        context = context,
                        mutationToggleEnabled = featureToggleStore.polkaswapMutationsEnabled,
                        disabledReason = "Polkaswap actions are temporarily disabled."
                    )
                } else {
                    context.capability.copy(
                        featureId = DEFI_POLKASWAP,
                        availability = DeFiAvailability.SetupRequired,
                        actionsEnabled = false,
                        userFacingReason = assetReason
                    )
                }
                PolkaswapCapability(
                    defiCapability = capability,
                    disclaimerAccepted = polkaswapInteractor.hasReadDisclaimer
                )
            }.getOrElse { error ->
                PolkaswapCapability(
                    defiCapability = unavailableSoraCapability(
                        DEFI_POLKASWAP,
                        error.message ?: "Polkaswap is unavailable."
                    )
                )
            }
        }
    }

    private fun refreshDefiCapabilities() {
        viewModelScope.launch {
            val stakingCapability = runCatching { loadStakingCapability() }.getOrElse { error ->
                unavailableStakingCapability(error.message ?: "Staking capability is unavailable.")
            }
            val soraCapabilities = runCatching {
                val context = loadSoraContext()
                mapOf(
                    DEFI_POOLS to buildSoraCapability(
                        featureId = DEFI_POOLS,
                        context = context,
                        mutationToggleEnabled = featureToggleStore.polkaswapMutationsEnabled,
                        disabledReason = "Liquidity pool actions are temporarily disabled."
                    ),
                    DEFI_FARMING to buildSoraCapability(
                        featureId = DEFI_FARMING,
                        context = context,
                        mutationToggleEnabled = featureToggleStore.demeterMutationsEnabled,
                        disabledReason = "Farming actions are temporarily disabled."
                    ),
                    DEFI_POLKAMARKT to buildSoraCapability(
                        featureId = DEFI_POLKAMARKT,
                        context = context,
                        mutationToggleEnabled = featureToggleStore.polkamarktMutationsEnabled,
                        disabledReason = "Polkamarkt trading actions are temporarily disabled."
                    )
                )
            }.getOrElse { error ->
                val reason = error.message ?: "SORA DeFi capability is unavailable."
                mapOf(
                    DEFI_POOLS to unavailableSoraCapability(DEFI_POOLS, reason),
                    DEFI_FARMING to unavailableSoraCapability(DEFI_FARMING, reason),
                    DEFI_POLKAMARKT to unavailableSoraCapability(DEFI_POLKAMARKT, reason)
                )
            }
            mutableDefiCapabilities.value = soraCapabilities + (DEFI_STAKING to stakingCapability)
        }
    }

    private suspend fun loadStakingCapability(): DeFiCapability {
        val wallet = accountRepository.getSelectedMetaAccount()
        val supportedChains = chainRegistry.getChains().filter { chain ->
            chain.ecosystem == Ecosystem.Substrate && chain.assets.any { asset ->
                asset.staking != Asset.StakingType.UNSUPPORTED || asset.supportStakingPool
            }
        }
        val chainsWithAccount = supportedChains.filter { wallet.accountId(it) != null }
        val recoveryRequired = accountRepository.isWalletRecoveryRequired(wallet.id)
        val hasSigningMaterial = chainsWithAccount.any { chain ->
            runCatching {
                if (wallet.hasChainAccount(chain.id)) {
                    accountRepository.getChainAccountSecrets(wallet.id, chain.id) != null
                } else {
                    accountRepository.getSubstrateSecrets(wallet.id) != null
                }
            }.getOrDefault(false)
        }
        val runtimeReady = chainsWithAccount.any { chain ->
            chainRegistry.getRuntimeOrNull(chain.id) != null
        }

        return buildStakingCapability(
            supportedNetworkIds = supportedChains.mapTo(mutableSetOf()) { it.id },
            hasSupportedAccount = chainsWithAccount.isNotEmpty(),
            recoveryRequired = recoveryRequired,
            hasSigningMaterial = hasSigningMaterial,
            runtimeReady = runtimeReady
        )
    }

    private fun refreshDefiPositions() {
        poolsInteractor.subscribePoolsCacheCurrentAccount()
            .onEach { pools ->
                val count = pools.count { pool ->
                    pool.user?.let { user ->
                        user.basePooled > java.math.BigDecimal.ZERO ||
                            user.targetPooled > java.math.BigDecimal.ZERO ||
                            user.poolProvidersBalance > java.math.BigDecimal.ZERO
                    } == true
                }
                mutableDefiPositions.update {
                    it.copy(liquidityPools = DeFiPositionCount.Ready(count))
                }
            }
            .catch { error ->
                mutableDefiPositions.update {
                    it.copy(
                        liquidityPools = DeFiPositionCount.Unavailable(
                            error.message ?: "Open Liquidity pools to refresh."
                        )
                    )
                }
            }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            runCatching { poolsInteractor.updateAccountPools() }
                .onFailure { error ->
                    mutableDefiPositions.update { current ->
                        if (current.liquidityPools !is DeFiPositionCount.Loading) current else {
                            current.copy(
                                liquidityPools = DeFiPositionCount.Unavailable(
                                    error.message ?: "Open Liquidity pools to refresh."
                                )
                            )
                        }
                    }
                }
        }
        viewModelScope.launch {
            val count = runCatching {
                val wallet = accountRepository.getSelectedMetaAccount()
                walletRepository.getAssets(wallet.id).count { asset ->
                    asset.bondedInPlanks.orZeroPositive() ||
                        asset.unbondingInPlanks.orZeroPositive() ||
                        asset.redeemableInPlanks.orZeroPositive()
                }
            }
            mutableDefiPositions.update {
                it.copy(
                    staking = count.fold(
                        onSuccess = DeFiPositionCount::Ready,
                        onFailure = { error ->
                            DeFiPositionCount.Unavailable(
                                error.message ?: "Open Staking to refresh."
                            )
                        }
                    )
                )
            }
        }
        viewModelScope.launch {
            val count = runCatching {
                val chainId = polkaswapInteractor.polkaswapChainId
                demeterFarmingInteractor.refresh(chainId)
                demeterFarmingInteractor.getFarmedPools(chainId).orEmpty().count { position ->
                    position.amount > java.math.BigDecimal.ZERO ||
                        position.amountReward > java.math.BigDecimal.ZERO
                }
            }
            mutableDefiPositions.update {
                it.copy(
                    farming = count.fold(
                        onSuccess = DeFiPositionCount::Ready,
                        onFailure = { error ->
                            DeFiPositionCount.Unavailable(
                                error.message ?: "Open Farming to refresh."
                            )
                        }
                    )
                )
            }
        }
        viewModelScope.launch {
            val count = runCatching {
                val snapshot = polkamarktInteractor.snapshot()
                (snapshot.positions.map { it.marketId } + snapshot.claimable.map { it.marketId })
                    .distinct()
                    .size
            }
            mutableDefiPositions.update {
                it.copy(
                    polkamarkt = count.fold(
                        onSuccess = DeFiPositionCount::Ready,
                        onFailure = { error ->
                            DeFiPositionCount.Unavailable(
                                error.message ?: "Open Polkamarkt to refresh."
                            )
                        }
                    )
                )
            }
        }
    }

    private fun refreshCrossChainCapability() {
        viewModelScope.launch {
            mutableCrossChainCapability.value = crossChainRouteProviders.bestCapability()
        }
    }

    private suspend fun loadSoraContext(): SoraCapabilityContext {
        val chain = chainRegistry.getChain(polkaswapInteractor.polkaswapChainId)
        val wallet = accountRepository.getSelectedMetaAccount()
        val hasAccount = wallet.accountId(chain) != null
        val recoveryRequired = accountRepository.isWalletRecoveryRequired(wallet.id)
        val hasSigningMaterial = hasAccount && !recoveryRequired && runCatching {
            if (wallet.hasChainAccount(chain.id)) {
                accountRepository.getChainAccountSecrets(wallet.id, chain.id) != null
            } else {
                accountRepository.getSubstrateSecrets(wallet.id) != null
            }
        }.getOrDefault(false)
        val reason = when {
            !hasAccount -> "Add a SORA account to continue."
            recoveryRequired -> "Recover this wallet's signing material to continue."
            !hasSigningMaterial -> "This SORA account is watch-only or needs an unsupported external signer."
            chainRegistry.getRuntimeOrNull(chain.id) == null -> "SORA runtime is unavailable."
            else -> null
        }
        val availability = when {
            !hasAccount || recoveryRequired || !hasSigningMaterial -> DeFiAvailability.SetupRequired
            reason != null -> DeFiAvailability.Unavailable
            else -> DeFiAvailability.Available
        }
        val capability = DeFiCapability(
            featureId = "sora-defi",
            availability = availability,
            accountRequirement = DeFiAccountRequirement.Sora,
            signingRequirement = DeFiSigningRequirement.SignableAccount,
            supportedNetworkIds = setOf(chain.id),
            destinationEnabled = availability == DeFiAvailability.Available,
            actionsEnabled = availability == DeFiAvailability.Available,
            userFacingReason = reason
        )
        val assets = walletRepository.getAssets(wallet.id).filter { it.chainId == chain.id }
        return SoraCapabilityContext(capability, assets)
    }

    private fun buildSoraCapability(
        featureId: String,
        context: SoraCapabilityContext,
        mutationToggleEnabled: Boolean,
        disabledReason: String
    ): DeFiCapability {
        val prerequisitesMet = context.capability.availability == DeFiAvailability.Available
        return context.capability.copy(
            featureId = featureId,
            destinationEnabled = prerequisitesMet || featureId == DEFI_POLKAMARKT,
            actionsEnabled = prerequisitesMet && mutationToggleEnabled,
            userFacingReason = context.capability.userFacingReason
                ?: disabledReason.takeUnless { mutationToggleEnabled }
        )
    }

    private fun unavailableSoraCapability(featureId: String, reason: String) = DeFiCapability(
        featureId = featureId,
        availability = DeFiAvailability.Unavailable,
        accountRequirement = DeFiAccountRequirement.Sora,
        signingRequirement = DeFiSigningRequirement.SignableAccount,
        supportedNetworkIds = emptySet(),
        destinationEnabled = false,
        actionsEnabled = false,
        userFacingReason = reason
    )

    private fun unavailableStakingCapability(reason: String) = buildStakingCapability(
        supportedNetworkIds = emptySet(),
        hasSupportedAccount = false,
        recoveryRequired = false,
        hasSigningMaterial = false,
        runtimeReady = false,
        lookupFailure = reason
    )
}

internal fun buildStakingCapability(
    supportedNetworkIds: Set<String>,
    hasSupportedAccount: Boolean,
    recoveryRequired: Boolean,
    hasSigningMaterial: Boolean,
    runtimeReady: Boolean,
    lookupFailure: String? = null
): DeFiCapability {
    val reason = when {
        lookupFailure != null -> lookupFailure
        supportedNetworkIds.isEmpty() -> "No supported staking network is available."
        !hasSupportedAccount -> "Add a Substrate account on a supported staking network."
        recoveryRequired -> "Recover this wallet's signing material to stake."
        !hasSigningMaterial -> "This wallet is watch-only or needs an unsupported external signer."
        !runtimeReady -> "The staking runtime is unavailable."
        else -> null
    }
    val availability = when {
        reason == null -> DeFiAvailability.Available
        !hasSupportedAccount || recoveryRequired || !hasSigningMaterial -> DeFiAvailability.SetupRequired
        else -> DeFiAvailability.Unavailable
    }
    return DeFiCapability(
        featureId = DEFI_STAKING,
        availability = availability,
        accountRequirement = DeFiAccountRequirement.AnySupported,
        signingRequirement = DeFiSigningRequirement.SignableAccount,
        supportedNetworkIds = supportedNetworkIds,
        destinationEnabled = availability == DeFiAvailability.Available,
        actionsEnabled = availability == DeFiAvailability.Available,
        userFacingReason = reason
    )
}

data class PolkaswapCapability(
    val defiCapability: DeFiCapability,
    val disclaimerAccepted: Boolean = false
) {
    val loading: Boolean get() = defiCapability.availability == DeFiAvailability.Checking
    val reason: String? get() = defiCapability.userFacingReason
    val canOpenAction: Boolean
        get() = if (disclaimerAccepted) {
            defiCapability.canPerformAction
        } else {
            true
        }
    val actionLabel: String get() = if (disclaimerAccepted) "Swap tokens" else "Review disclaimer"
}

private data class SoraCapabilityContext(
    val capability: DeFiCapability,
    val assets: List<jp.co.soramitsu.wallet.impl.domain.model.Asset>
)

sealed interface DeFiPositionCount {
    data object Loading : DeFiPositionCount
    data class Ready(val count: Int) : DeFiPositionCount
    data class Unavailable(val reason: String) : DeFiPositionCount
}

data class DeFiPositionsSummary(
    val staking: DeFiPositionCount = DeFiPositionCount.Loading,
    val liquidityPools: DeFiPositionCount = DeFiPositionCount.Loading,
    val farming: DeFiPositionCount = DeFiPositionCount.Loading,
    val polkamarkt: DeFiPositionCount = DeFiPositionCount.Loading
)

private fun BigInteger?.orZeroPositive(): Boolean = this != null && this > BigInteger.ZERO

@AndroidEntryPoint
class MainHubFragment : BaseComposeFragment<MainHubViewModel>() {

    override val viewModel: MainHubViewModel by viewModels()

    @OptIn(ExperimentalMaterialApi::class)
    @Composable
    override fun Content(
        padding: PaddingValues,
        scrollState: ScrollState,
        modalBottomSheetState: ModalBottomSheetState
    ) {
        val polkaswapCapability by viewModel.polkaswapCapability.collectAsStateWithLifecycle()
        val defiCapabilities by viewModel.defiCapabilities.collectAsStateWithLifecycle()
        val defiPositions by viewModel.defiPositions.collectAsStateWithLifecycle()
        val crossChainCapability by viewModel.crossChainCapability.collectAsStateWithLifecycle()
        MainHubScreen(
            hubType = viewModel.hubType,
            scrollState = scrollState,
            onPrimaryAction = {
                if (viewModel.hubType == MainHubType.Defi) {
                    if (viewModel.canOpenStaking()) {
                        findNavController().navigate(R.id.stakingFragment)
                    }
                } else {
                    viewModel.onPrimaryAction()
                }
            },
            onSecondaryAction = viewModel::onSecondaryAction,
            onFarmingAction = viewModel::onFarmingAction,
            onPolkamarktAction = viewModel::onPolkamarktAction,
            polkaswapCapability = polkaswapCapability,
            defiCapabilities = defiCapabilities,
            defiPositions = defiPositions,
            crossChainCapability = crossChainCapability
        )
    }
}

@Composable
fun MainHubScreen(
    hubType: MainHubType,
    scrollState: ScrollState,
    onPrimaryAction: () -> Unit,
    onSecondaryAction: () -> Unit,
    onFarmingAction: () -> Unit,
    onPolkamarktAction: () -> Unit,
    polkaswapCapability: PolkaswapCapability,
    defiCapabilities: Map<String, DeFiCapability>,
    defiPositions: DeFiPositionsSummary,
    crossChainCapability: CrossChainRouteCapability?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        when (hubType) {
            MainHubType.Defi -> {
                H1(text = stringResource(R.string.main_hub_defi_title))
                B0(
                    text = stringResource(R.string.main_hub_defi_description),
                    color = white50
                )
                H3(text = stringResource(R.string.main_hub_positions_title))
                B0(
                    text = stringResource(R.string.main_hub_positions_description),
                    color = white50
                )
                PositionCountRow(
                    label = stringResource(R.string.main_hub_staking_action),
                    state = defiPositions.staking
                )
                PositionCountRow(
                    label = stringResource(R.string.main_hub_pools_action),
                    state = defiPositions.liquidityPools
                )
                PositionCountRow(
                    label = stringResource(R.string.main_hub_farming_title),
                    state = defiPositions.farming
                )
                PositionCountRow(
                    label = stringResource(R.string.main_hub_polkamarkt_title),
                    state = defiPositions.polkamarkt
                )
                AccentButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.main_hub_staking_action),
                    enabled = defiCapabilities[DEFI_STAKING]?.canOpenDestination == true,
                    onClick = onPrimaryAction
                )
                defiCapabilities[DEFI_STAKING]?.userFacingReason?.let { reason ->
                    B0(text = reason, color = white50)
                }
                GrayButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.main_hub_pools_action),
                    enabled = defiCapabilities[DEFI_POOLS]?.canOpenDestination == true,
                    onClick = onSecondaryAction
                )
                defiCapabilities[DEFI_POOLS]?.userFacingReason?.let { reason ->
                    B0(text = reason, color = white50)
                }
                GrayButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.main_hub_farming_title),
                    enabled = defiCapabilities[DEFI_FARMING]?.canOpenDestination == true,
                    onClick = onFarmingAction
                )
                defiCapabilities[DEFI_FARMING]?.userFacingReason?.let { reason ->
                    B0(text = reason, color = white50)
                }
                GrayButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.main_hub_polkamarkt_title),
                    enabled = defiCapabilities[DEFI_POLKAMARKT]?.canOpenDestination == true,
                    onClick = onPolkamarktAction
                )
                defiCapabilities[DEFI_POLKAMARKT]?.userFacingReason?.let { reason ->
                    B0(text = reason, color = white50)
                }
            }

            MainHubType.Polkaswap -> {
                H1(text = stringResource(R.string.main_hub_polkaswap_title))
                B0(
                    text = stringResource(R.string.main_hub_polkaswap_description),
                    color = white50
                )
                AccentButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = polkaswapCapability.actionLabel,
                    enabled = polkaswapCapability.canOpenAction,
                    onClick = onPrimaryAction
                )
                polkaswapCapability.reason?.let { reason ->
                    B0(text = reason, color = white50)
                }
                GrayButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.main_hub_pools_action),
                    enabled = polkaswapCapability.defiCapability.canOpenDestination,
                    onClick = onSecondaryAction
                )
            }

            MainHubType.CrossChain -> {
                H1(text = stringResource(R.string.main_hub_cross_chain_title))
                B0(
                    text = stringResource(R.string.main_hub_cross_chain_description),
                    color = white50
                )
                crossChainCapability?.let { capability ->
                    B0(
                        text = capability.userFacingReason
                            ?: "Protocol: ${capability.protocol.displayName}",
                        color = white50
                    )
                }
                AccentButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.main_hub_cross_chain_action),
                    onClick = onPrimaryAction
                )
                H3(text = stringResource(jp.co.soramitsu.common.R.string.common_title_cross_chain))
                B0(
                    text = stringResource(R.string.main_hub_cross_chain_coverage),
                    color = white50
                )
            }
        }
    }
}

@Composable
private fun PositionCountRow(label: String, state: DeFiPositionCount) {
    val text = when (state) {
        DeFiPositionCount.Loading ->
            stringResource(R.string.main_hub_positions_loading, label)
        is DeFiPositionCount.Ready ->
            stringResource(R.string.main_hub_positions_count, label, state.count)
        is DeFiPositionCount.Unavailable ->
            stringResource(R.string.main_hub_positions_unavailable, label, state.reason)
    }
    B0(text = text, color = white50)
}
