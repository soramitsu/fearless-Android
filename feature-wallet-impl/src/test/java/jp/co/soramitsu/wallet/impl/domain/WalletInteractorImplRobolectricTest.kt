package jp.co.soramitsu.wallet.impl.domain

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.io.File
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.presentation.exporting.ExportSource
import jp.co.soramitsu.common.compose.component.ChainSelectorViewStateWithFilters
import jp.co.soramitsu.common.data.model.CursorPage
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.domain.NetworkStateService
import jp.co.soramitsu.common.domain.SelectedFiat
import jp.co.soramitsu.common.domain.model.NetworkIssueType
import jp.co.soramitsu.common.interfaces.FileProvider
import jp.co.soramitsu.common.model.AssetBooleanState
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.coredb.model.AddressBookContact
import jp.co.soramitsu.coredb.model.AssetUpdateItem
import jp.co.soramitsu.coredb.model.PhishingLocal
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.wallet.impl.WalletTestModels
import jp.co.soramitsu.wallet.impl.data.repository.HistoryRepository
import jp.co.soramitsu.wallet.impl.domain.interfaces.AddressBookRepository
import jp.co.soramitsu.wallet.impl.domain.interfaces.AssetSorting
import jp.co.soramitsu.wallet.impl.domain.interfaces.TransactionFilter
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.AssetWithStatus
import jp.co.soramitsu.wallet.impl.domain.model.Operation
import jp.co.soramitsu.wallet.impl.domain.model.Transfer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WalletInteractorImplRobolectricTest {

    private val dot = WalletTestModels.asset(chainId = POLKADOT_ID, currencyId = "0x0200")
    private val polkadot = WalletTestModels.chain(id = POLKADOT_ID, assets = listOf(dot))
    private val meta = WalletTestModels.metaAccount()

    private val walletRepository = mockk<WalletRepository>(relaxed = true)
    private val addressBook = mockk<AddressBookRepository>(relaxed = true)
    private val accounts = mockk<AccountRepository>(relaxed = true) {
        coEvery { getSelectedMetaAccount() } returns meta
        every { selectedMetaAccountFlow() } returns flowOf(meta)
    }
    private val history = mockk<HistoryRepository>(relaxed = true)
    private val chainRegistry = mockk<ChainRegistry>(relaxed = true) {
        coEvery { getChain(POLKADOT_ID) } returns polkadot
        coEvery { chainWithAsset(POLKADOT_ID, "dot") } returns (polkadot to dot)
    }
    private val files = mockk<FileProvider>()
    private val stored = mutableMapOf<String, Any?>()
    private val preferences = mockk<Preferences>(relaxed = true) {
        every { putString(any(), any()) } answers { stored[firstArg()] = secondArg<String?>() }
        every { getString(any<String>()) } answers { stored[firstArg()] as String? }
        every { putBoolean(any(), any()) } answers { stored[firstArg()] = secondArg<Boolean>() }
        every { getBoolean(any(), any()) } answers { stored[firstArg()] as Boolean? ?: secondArg() }
        every { stringFlow(any(), any()) } answers { flowOf(stored[firstArg()] as String? ?: "") }
    }
    private val fiat = mockk<SelectedFiat>(relaxed = true) { coEvery { get() } returns "usd" }
    private val chains = mockk<ChainsRepository>(relaxed = true) {
        coEvery { getChain(POLKADOT_ID) } returns polkadot
        coEvery { chainWithAsset(POLKADOT_ID, "dot") } returns (polkadot to dot)
        every { chainsFlow() } returns flowOf(listOf(polkadot))
    }
    private val networkState = mockk<NetworkStateService> {
        every { networkIssuesFlow } returns flowOf(mapOf(POLKADOT_ID to NetworkIssueType.Node))
    }

    private val interactor = WalletInteractorImpl(
        walletRepository, addressBook, accounts, history, chainRegistry, files, preferences, fiat,
        mockk(relaxed = true), chains, networkState, mockk(relaxed = true)
    )

    @Test
    fun assetReadsUseTheSelectedAccountOnTheRequestedChain(): Unit = runBlocking {
        val asset = mockk<Asset>(relaxed = true)
        coEvery { walletRepository.getAsset(1L, WalletTestModels.substrateAccountId, dot, null) } returns asset
        every { walletRepository.assetFlow(1L, WalletTestModels.substrateAccountId, dot, null) } returns flowOf(asset)
        coEvery { chainRegistry.getRuntimeOrNull(POLKADOT_ID) } returns mockk(relaxed = true)

        assertEquals(asset, interactor.getCurrentAsset(POLKADOT_ID, "dot"))
        assertEquals(asset, interactor.getCurrentAssetOrNull(POLKADOT_ID, "dot"))
        assertEquals(asset, interactor.assetFlow(POLKADOT_ID, "dot").first())
        assertTrue(interactor.canUseAsset(POLKADOT_ID, "dot"))
    }

    @Test
    fun assetsFlowFirstRevokesThenPublishesTheWalletAssets(): Unit = runBlocking {
        val asset = mockk<Asset>(relaxed = true)
        every { walletRepository.assetsFlow(meta) } returns flowOf(listOf(AssetWithStatus(asset, true, false)))

        val emitted = interactor.assetsFlowAndAccount().toList()

        assertEquals(1L to emptyList<Any>(), emitted.first())
        assertEquals(1L, emitted.last().first)
        assertEquals(1, emitted.last().second.size)
    }

    @Test
    fun ratesSyncReportsFailureAsAResult(): Unit = runBlocking {
        coEvery { walletRepository.syncAssetsRates("usd") } throws IllegalStateException("down")

        assertTrue(interactor.syncAssetsRates().isFailure)
    }

    @Test
    fun historyCallsPassTheAccountAndChain(): Unit = runBlocking {
        val page = CursorPage<Operation>("next", emptyList())
        every { history.operationsFirstPageFlow(WalletTestModels.substrateAccountId, polkadot, dot, any()) } returns flowOf(page, page)
        coEvery { history.getOperations(10, "c", any(), WalletTestModels.substrateAccountId, polkadot, dot, any()) } returns page

        val changes = interactor.operationsFirstPageFlow(POLKADOT_ID, "dot").toList()
        val sync = interactor.syncOperationsFirstPage(POLKADOT_ID, "dot", 10, setOf(TransactionFilter.TRANSFER))
        val operations = interactor.getOperations(POLKADOT_ID, "dot", 10, "c", setOf(TransactionFilter.REWARD))

        assertEquals(listOf(true, false), changes.map { it.accountChanged })
        assertTrue(sync.isSuccess)
        assertEquals("next", operations.getOrThrow().nextCursor)
    }

    @Test
    fun accountAndAddressQueries(): Unit = runBlocking {
        val account = interactor.selectedAccountFlow(POLKADOT_ID).first()

        assertEquals("Main", account.name)
        assertEquals(account.address, interactor.getChainAddressForSelectedMetaAccount(POLKADOT_ID))
        assertEquals(account.address, interactor.polkadotAddressForSelectedAccountFlow().first())
        assertTrue(interactor.validateSendAddress(POLKADOT_ID, account.address))
        assertFalse(interactor.validateSendAddress(POLKADOT_ID, "not an address"))
        assertEquals(meta, interactor.selectedMetaAccountFlow().first())
        assertEquals(meta, interactor.selectedLightMetaAccountFlow().first())
        assertEquals(listOf(polkadot), interactor.getChains().first())
    }

    @Test
    fun phishingTransferAndClaimCallsAreDelegated(): Unit = runBlocking {
        val transfer = mockk<Transfer>(relaxed = true) { every { chainAsset } returns dot }
        coEvery { walletRepository.isAddressFromPhishingList("bad") } returns true
        coEvery { walletRepository.getPhishingInfo("bad") } returns PhishingLocal("bad", "Scam", "scam", null)
        coEvery { walletRepository.getTransferMaxAmount(polkadot, transfer) } returns BigDecimal.TEN
        coEvery { walletRepository.observeTransferFee(polkadot, transfer) } returns flowOf(BigDecimal.ONE)
        coEvery { walletRepository.performTransfer(WalletTestModels.substrateAccountId, polkadot, transfer) } returns "0xhash"
        coEvery { walletRepository.estimateClaimRewardsFee(POLKADOT_ID) } returns BigInteger.TWO
        coEvery { walletRepository.getVestingLockedAmount(POLKADOT_ID) } returns BigInteger.ONE
        coEvery { walletRepository.claimRewards(polkadot, WalletTestModels.substrateAccountId) } returns Result.success("0xclaim")
        coEvery { chainRegistry.getRuntimeOrNull(POLKADOT_ID) } returns null

        assertTrue(interactor.isAddressFromPhishingList("bad"))
        assertEquals("Scam", interactor.getPhishingInfo("bad")!!.name)
        assertEquals(BigDecimal.TEN, interactor.getTransferMaxAmount(transfer))
        assertEquals(BigDecimal.ONE, interactor.observeTransferFee(transfer).first())
        assertEquals("0xhash", interactor.performTransfer(transfer).getOrThrow())
        assertEquals(BigInteger.TWO, interactor.estimateClaimRewardsFee(POLKADOT_ID))
        assertEquals(BigInteger.ONE, interactor.getVestingLockedAmount(POLKADOT_ID))
        assertEquals("0xclaim", interactor.claimRewards(POLKADOT_ID).getOrThrow())
        assertFalse(interactor.checkClaimSupport(POLKADOT_ID))
        assertFalse(interactor.canUseAsset(POLKADOT_ID, "dot"))
    }

    @Test
    fun failedTransferIsAFailureResult(): Unit = runBlocking {
        val transfer = mockk<Transfer>(relaxed = true) { every { chainAsset } returns dot }
        coEvery { walletRepository.performTransfer(any(), any(), any()) } throws IllegalStateException("rejected")

        assertTrue(interactor.performTransfer(transfer).isFailure)
    }

    @Test
    fun soraQrStringCarriesAddressKeyNameTokenAndAmount(): Unit = runBlocking {
        val withAmount = interactor.getQrCodeSharingSoraString(POLKADOT_ID, "dot", BigDecimal("1.5"))
        val withoutAmount = interactor.getQrCodeSharingSoraString(POLKADOT_ID, "dot", null)
        val unknownAsset = interactor.getQrCodeSharingSoraString(POLKADOT_ID, "missing", null)

        assertTrue(withAmount.startsWith("substrate:"))
        assertTrue(withAmount.endsWith(":Main:0x0200:1.5"))
        assertTrue(withoutAmount.endsWith(":Main:0x0200"))
        assertEquals(withAmount.split(":")[1], unknownAsset)
    }

    @Test
    fun qrParsingReadsSoraTonEthAndAmounts() {
        val sora = interactor.tryReadSoraFormat("substrate:addr:0xkey:Bob:0x02:3")

        assertEquals("addr", sora!!.address)
        assertEquals("3", sora.amount)
        assertNull(interactor.tryReadSoraFormat("ethereum:addr"))
        assertNull(interactor.tryReadSoraFormat("substrate:addr"))
        assertEquals("addr", interactor.tryReadAddressFromSoraFormat("substrate:addr:k"))
        assertEquals("EQabc_-1", interactor.extractTonAddress("ton://transfer/EQabc_-1?amount=5"))
        assertNull(interactor.extractTonAddress("no address"))
        assertEquals("0x" + "a".repeat(40), interactor.extractEthAddress("ethereum:0x${"a".repeat(40)}@1"))
        assertNull(interactor.extractEthAddress("0x123"))
        assertEquals(BigDecimal("2.5"), interactor.tryReadAmountFromQrContent("pay?x=1&amount=2.5"))
        assertNull(interactor.tryReadAmountFromQrContent("pay"))
    }

    @Test
    fun tempFileComesFromTheFileProvider(): Unit = runBlocking {
        coEvery { files.getFileInExternalCacheStorage("qr.png") } returns File("qr.png")

        assertEquals("qr.png", interactor.createFileInTempStorageAndRetrieveAsset("qr.png").getOrThrow().name)
    }

    @Test
    fun hidingAndShowingAssetsWritesUpdateItemsForTheSelectedWallet(): Unit = runBlocking {
        val items = slot<List<AssetUpdateItem>>()
        coEvery { walletRepository.updateAssetsHidden(capture(items)) } returns Unit

        interactor.markAssetAsHidden(POLKADOT_ID, "dot")
        assertEquals(false, items.captured.single().enabled)
        interactor.markAssetAsShown(POLKADOT_ID, "dot")
        assertEquals(true, items.captured.single().enabled)
        assertEquals(1L, items.captured.single().metaId)

        interactor.updateAssetsHiddenState(listOf(AssetBooleanState(POLKADOT_ID, "dot", true)), expectedMetaId = 99L)
        coVerify(exactly = 2) { walletRepository.updateAssetsHidden(any()) }
    }

    @Test
    fun addressBookAndHistoryAddresses(): Unit = runBlocking {
        every { addressBook.observeAddressBook(POLKADOT_ID) } returns flowOf(listOf(AddressBookContact(" addr ", "Bob", POLKADOT_ID)))
        every { history.getOperationAddressWithChainIdFlow(POLKADOT_ID, 5) } returns flowOf(setOf("a"))
        coEvery { history.getOperationAddressWithChainId(POLKADOT_ID, 5) } returns setOf("b")

        interactor.saveAddress("Bob", "addr", POLKADOT_ID)

        coVerify { addressBook.saveAddress("Bob", "addr", POLKADOT_ID) }
        assertEquals("addr", interactor.observeAddressBook(POLKADOT_ID).first().single().address)
        assertEquals(setOf("a"), interactor.getOperationAddressWithChainIdFlow(POLKADOT_ID, 5).first())
        assertEquals(setOf("b"), interactor.getOperationAddressWithChainId(POLKADOT_ID, 5))
    }

    @Test
    fun preferencesKeepChainFilterSortingAndIntro(): Unit = runBlocking {
        interactor.saveChainId(1L, POLKADOT_ID)
        assertEquals(POLKADOT_ID, interactor.getSavedChainId(1L))
        coEvery { chainRegistry.getChain("gone") } throws NoSuchElementException()
        interactor.saveChainId(2L, "gone")
        assertNull(interactor.getSavedChainId(2L))

        assertEquals(ChainSelectorViewStateWithFilters.Filter.All, interactor.observeSelectedAccountChainSelectFilter().first())
        interactor.saveChainSelectFilter(1L, ChainSelectorViewStateWithFilters.Filter.Favorite.name)
        assertEquals(ChainSelectorViewStateWithFilters.Filter.Favorite, interactor.observeSelectedAccountChainSelectFilter().first())

        assertFalse(interactor.getAssetManagementIntroPassed())
        interactor.saveAssetManagementIntroPassed()
        assertTrue(interactor.getAssetManagementIntroPassed())

        assertEquals(AssetSorting.FiatBalance, interactor.observeAssetSorting().first())
        interactor.applyAssetSorting(AssetSorting.Name)
        assertEquals(AssetSorting.Name, interactor.observeAssetSorting().first())
    }

    @Test
    fun passThroughFlowsAndTokens(): Unit = runBlocking {
        every { walletRepository.observeChainsPerAsset(1L, "dot") } returns flowOf(mapOf(polkadot to null))

        assertEquals(mapOf(polkadot to null), interactor.observeChainsPerAsset(1L, "dot").first())
        assertEquals(mapOf(polkadot to null), interactor.observeCurrentAccountChainsPerAsset("dot").first())
        assertEquals(NetworkIssueType.Node, interactor.networkIssuesFlow().first()[POLKADOT_ID])
        assertEquals(polkadot, interactor.getChain(POLKADOT_ID))
        assertEquals(meta, interactor.getSelectedMetaAccount())
        interactor.getToken(dot)
        interactor.getEquilibriumAssetRates(dot)
        interactor.getEquilibriumAccountInfo(dot, WalletTestModels.substrateAccountId)
        coVerify { walletRepository.getEquilibriumAssetRates(dot) }
    }

    @Test
    fun controllerDeprecationsNeedARelayStakingRuntime(): Unit = runBlocking {
        coEvery { chains.getChainsById() } returns mapOf(POLKADOT_ID to polkadot)

        assertTrue(interactor.checkControllerDeprecations().isEmpty())
    }

    @Test
    fun exportSourcesFollowTheStoredSecrets(): Unit = runBlocking {
        val ton = WalletTestModels.chain(id = "ton", ecosystem = Ecosystem.Ton)
        coEvery { chains.getChain("ton") } returns ton
        coEvery { accounts.getTonSecrets(1L) } returns mockk(relaxed = true)
        coEvery { accounts.getSubstrateSecrets(1L) } returns null

        assertEquals(setOf(ExportSource.Mnemonic), interactor.getExportSourceTypes("ton", 1L).toSet())
        assertTrue(interactor.getExportSourceTypes(POLKADOT_ID, 1L).isEmpty())
    }

    private companion object {
        const val POLKADOT_ID = "91b171bb158e2d3848fa23a9f1c25182fb8e20313b2c1eb49219da7a70ce90c3"
    }
}
