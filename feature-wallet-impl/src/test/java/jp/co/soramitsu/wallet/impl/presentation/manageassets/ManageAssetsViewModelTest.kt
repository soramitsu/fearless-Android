package jp.co.soramitsu.wallet.impl.presentation.manageassets

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.LightMetaAccount
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.compose.component.ChainSelectorViewStateWithFilters
import jp.co.soramitsu.common.model.AssetBooleanState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.impl.domain.ChainInteractor
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.AssetWithStatus
import jp.co.soramitsu.wallet.impl.domain.model.Token
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import java.math.BigInteger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import jp.co.soramitsu.wallet.impl.domain.model.Asset as WalletAsset

@OptIn(ExperimentalCoroutinesApi::class)
class ManageAssetsViewModelTest {

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    @Test
    fun `auto renders from portfolio eligibility and only toggles persist explicit choices`() = runTest {
        val visibleAuto = coreAsset(id = "holding", symbol = "HOLD")
        val hiddenAuto = coreAsset(id = "empty", symbol = "EMPTY")
        val chain = chain(listOf(visibleAuto, hiddenAuto))
        val assets = listOf(
            walletAsset(visibleAuto, free = BigInteger.TEN),
            walletAsset(hiddenAuto, free = BigInteger.ZERO)
        )
        val walletInteractor = mockk<WalletInteractor>(relaxed = true)
        val accountInteractor = mockk<AccountInteractor>()
        val chainInteractor = mockk<ChainInteractor>()
        val router = mockk<WalletRouter>(relaxed = true)

        every { walletInteractor.assetsFlow() } returns flowOf(assets)
        every { walletInteractor.selectedMetaAccountFlow() } returns flowOf(metaAccount())
        every { walletInteractor.observeSelectedAccountChainSelectFilter() } returns
            flowOf(ChainSelectorViewStateWithFilters.Filter.All)
        coEvery { walletInteractor.getSavedChainId(WALLET_ID) } returns null
        every { chainInteractor.getChainsFlow() } returns flowOf(listOf(chain))
        coEvery { accountInteractor.selectedLightMetaAccount() } returns
            mockk<LightMetaAccount> { every { id } returns WALLET_ID }
        every { router.chainSelectorPayloadFlow } returns emptyFlow()

        val viewModel = ManageAssetsViewModel(
            walletRouter = router,
            walletInteractor = walletInteractor,
            accountInteractor = accountInteractor,
            chainInteractor = chainInteractor,
            resourceManager = mockk<ResourceManager>(relaxed = true)
        )
        advanceUntilIdle()

        val items = viewModel.state.value.assets.orEmpty().values.flatten().associateBy { it.id }
        assertTrue(items.getValue(visibleAuto.id).isChecked)
        assertFalse(items.getValue(hiddenAuto.id).isChecked)

        viewModel.onDialogClose()
        advanceUntilIdle()
        coVerify(exactly = 0) { walletInteractor.updateAssetsHiddenState(any()) }

        viewModel.onChecked(items.getValue(visibleAuto.id), checked = false)
        viewModel.onChecked(items.getValue(hiddenAuto.id), checked = true)
        viewModel.onDialogClose()
        advanceUntilIdle()

        val persisted = slot<List<AssetBooleanState>>()
        coVerify(exactly = 1) { walletInteractor.updateAssetsHiddenState(capture(persisted)) }
        assertTrue(
            AssetBooleanState(CHAIN_ID, visibleAuto.id, false) in persisted.captured
        )
        assertTrue(
            AssetBooleanState(CHAIN_ID, hiddenAuto.id, true) in persisted.captured
        )
    }

    private fun metaAccount() = MetaAccount(
        id = WALLET_ID,
        chainAccounts = emptyMap(),
        favoriteChains = emptyMap(),
        substratePublicKey = ByteArray(32) { 1 },
        substrateCryptoType = CryptoType.SR25519,
        substrateAccountId = ByteArray(32) { 1 },
        ethereumAddress = null,
        ethereumPublicKey = null,
        tonPublicKey = null,
        isSelected = true,
        isBackedUp = true,
        googleBackupAddress = null,
        name = "wallet",
        initialized = true
    )

    private fun coreAsset(id: String, symbol: String) = Asset(
        id = id,
        name = id,
        symbol = symbol,
        iconUrl = "",
        chainId = CHAIN_ID,
        chainName = "Dormant network",
        chainIcon = null,
        isTestNet = false,
        priceId = null,
        precision = 0,
        staking = Asset.StakingType.UNSUPPORTED,
        purchaseProviders = emptyList(),
        supportStakingPool = false,
        isUtility = false,
        type = ChainAssetType.Normal,
        currencyId = id,
        existentialDeposit = null,
        color = null,
        isNative = false
    )

    private fun walletAsset(asset: Asset, free: BigInteger) = AssetWithStatus(
        asset = WalletAsset(
            metaId = WALLET_ID,
            token = Token(asset, fiatRate = null, fiatSymbol = "$", recentRateChange = null),
            accountId = byteArrayOf(1),
            freeInPlanks = free,
            reservedInPlanks = BigInteger.ZERO,
            miscFrozenInPlanks = BigInteger.ZERO,
            feeFrozenInPlanks = BigInteger.ZERO,
            bondedInPlanks = null,
            redeemableInPlanks = null,
            unbondingInPlanks = null,
            sortIndex = 0,
            enabled = null,
            minSupportedVersion = null,
            chainAccountName = null,
            markedNotNeed = false,
            status = null
        ),
        hasAccount = true,
        hasChainAccount = true
    )

    private fun chain(assets: List<Asset>) = Chain(
        id = CHAIN_ID,
        paraId = null,
        rank = null,
        name = "Dormant network",
        minSupportedVersion = null,
        assets = assets,
        nodes = emptyList(),
        explorers = emptyList(),
        externalApi = null,
        icon = "",
        addressPrefix = 0,
        isEthereumBased = false,
        isTestNet = false,
        hasCrowdloans = false,
        parentId = null,
        supportStakingPool = false,
        isEthereumChain = false,
        chainlinkProvider = false,
        supportNft = false,
        isUsesAppId = false,
        identityChain = null,
        ecosystem = Ecosystem.Substrate,
        remoteAssetsSource = null
    )

    private companion object {
        const val WALLET_ID = 42L
        const val CHAIN_ID = "dormant-chain"
    }
}
