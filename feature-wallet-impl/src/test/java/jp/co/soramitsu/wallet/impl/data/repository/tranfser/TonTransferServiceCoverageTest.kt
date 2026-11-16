package jp.co.soramitsu.wallet.impl.data.repository.tranfser

import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.data.network.ton.AccountAddress
import jp.co.soramitsu.common.data.network.ton.AccountStatus
import jp.co.soramitsu.common.data.network.ton.EmulateMessageToWalletRequest
import jp.co.soramitsu.common.data.network.ton.JettonBalance
import jp.co.soramitsu.common.data.network.ton.JettonPreview
import jp.co.soramitsu.common.data.network.ton.JettonsBalances
import jp.co.soramitsu.common.data.network.ton.MessageConsequences
import jp.co.soramitsu.common.data.network.ton.SendBlockchainMessageRequest
import jp.co.soramitsu.common.data.network.ton.TonAccountData
import jp.co.soramitsu.common.data.network.ton.Trace
import jp.co.soramitsu.common.data.network.ton.Transaction
import jp.co.soramitsu.common.utils.tonAccountId
import jp.co.soramitsu.common.utils.v4r2tonAddress
import jp.co.soramitsu.core.extrinsic.keypair_provider.KeypairProvider
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.coredb.dao.AssetDao
import jp.co.soramitsu.coredb.model.cache.CachedAssetRead
import jp.co.soramitsu.coredb.model.cache.CachedAssetWithPriceRead
import jp.co.soramitsu.coredb.model.cache.CachedNumericState
import jp.co.soramitsu.fearless_utils.encrypt.keypair.BaseKeypair
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.TonRemoteSource
import jp.co.soramitsu.wallet.impl.domain.model.TonTransferParams
import jp.co.soramitsu.wallet.impl.domain.model.Transfer
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fee emulation and submission of [TonTransferService] with local TON indexer doubles; no network is used. */
class TonTransferServiceCoverageTest {

    private val publicKey = ByteArray(32) { 1 }
    private val senderAccountId = publicKey.tonAccountId(false)
    private val utility = mockk<Asset>(relaxed = true) {
        every { id } returns "ton"
        every { chainId } returns CHAIN_ID
        every { isUtility } returns true
        every { type } returns ChainAssetType.Normal
        every { precision } returns 9
    }
    private val jetton = mockk<Asset>(relaxed = true) {
        every { id } returns JETTON_MASTER
        every { name } returns "Fixture jetton"
        every { chainId } returns CHAIN_ID
        every { type } returns ChainAssetType.Jetton
        every { precision } returns 9
    }
    private val accounts = mockk<AccountRepository>()
    private val keys = mockk<KeypairProvider>()
    private val remote = mockk<TonRemoteSource>()
    private val dao = mockk<AssetDao>()

    init {
        val account = mockk<MetaAccount> {
            every { id } returns 7L
            every { tonPublicKey } returns publicKey
        }
        coEvery { accounts.getSelectedMetaAccount() } returns account
        coEvery { dao.getAsset(7, publicKey, CHAIN_ID, "ton") } returns feeBalance(BigInteger.valueOf(5_000_000_000))
        coEvery { remote.getRawTime(any(), any()) } returns 1_700_000_000
        coEvery { remote.loadAccountData(any(), any()) } returns accountData(ByteArray(32) { 2 }.tonAccountId(false), AccountStatus.active)
    }

    @Test
    fun `fee of an undeployed wallet is emulated with its state init`(): Unit = runBlocking {
        val chain = chain(utility, jetton)
        coEvery { remote.getSeqno(chain, senderAccountId) } returns 0
        val request = stubEmulation(chain, BigInteger.valueOf(1_234_567))

        val fee = service(chain).getTransferFee(transfer(utility))

        assertEquals(BigDecimal("0.001234567"), fee)
        assertTrue(request.captured.boc.isNotBlank())
        assertEquals(1, request.captured.params?.size)
    }

    @Test
    fun `observed fee is emulated even when the indexer clock is unavailable`(): Unit = runBlocking {
        val chain = chain(utility, jetton)
        coEvery { remote.getSeqno(chain, senderAccountId) } returns 4
        coEvery { remote.getRawTime(chain, any()) } throws IllegalStateException("clock unavailable")
        stubEmulation(chain, BigInteger.valueOf(2_000_000))

        val fees = service(chain).observeTransferFee(transfer(utility)).toList()

        assertEquals(listOf(BigDecimal("0.002000000")), fees)
    }

    @Test
    fun `failed emulation of an uninitialized wallet reports the wallet as not initialized`(): Unit = runBlocking {
        val chain = chain(utility, jetton)
        coEvery { remote.getSeqno(chain, senderAccountId) } returns 1
        coEvery { remote.emulateBlockchainMessageRequest(chain, any()) } throws IllegalStateException("emulator down")
        coEvery { remote.loadAccountData(chain, senderAccountId) } returns accountData(senderAccountId, AccountStatus.uninit)

        val error = runCatching { service(chain).getTransferFee(transfer(utility)) }.exceptionOrNull()

        assertTrue(error is NotInitializedTonAccountException)
        coVerify(exactly = 1) { remote.loadAccountData(chain, senderAccountId) }
    }

    @Test
    fun `failed emulation of an active wallet reports a generic failure`(): Unit = runBlocking {
        val chain = chain(utility, jetton)
        coEvery { remote.getSeqno(chain, senderAccountId) } returns 1
        coEvery { remote.emulateBlockchainMessageRequest(chain, any()) } throws IllegalStateException("emulator down")
        coEvery { remote.loadAccountData(chain, senderAccountId) } returns accountData(senderAccountId, AccountStatus.active)

        val error = runCatching { service(chain).getTransferFee(transfer(utility)) }.exceptionOrNull()

        assertEquals("Something went wrong", error?.message)
        assertFalse(error is NotInitializedTonAccountException)
    }

    @Test
    fun `jetton fee is emulated for a transfer through the sender jetton wallet`(): Unit = runBlocking {
        val chain = chain(utility, jetton)
        coEvery { remote.getSeqno(chain, senderAccountId) } returns 3
        coEvery { remote.loadJettonBalances(chain, any()) } returns jettonWallets(JETTON_MASTER)
        stubEmulation(chain, BigInteger.valueOf(40_000_000))

        val fee = service(chain).getTransferFee(transfer(jetton, TonTransferParams(comment = "order 12")))

        assertEquals(BigDecimal("0.040000000"), fee)
        coVerify(exactly = 1) { remote.loadJettonBalances(chain, any()) }
    }

    @Test
    fun `jetton fee fails when the sender holds no wallet for that jetton`(): Unit = runBlocking {
        val chain = chain(utility, jetton)
        coEvery { remote.getSeqno(chain, senderAccountId) } returns 3
        coEvery { remote.loadJettonBalances(chain, any()) } returns jettonWallets("another-master")

        val error = runCatching { service(chain).getTransferFee(transfer(jetton)) }.exceptionOrNull()

        assertEquals("Cannot find jetton (Fixture jetton) wallet address", error?.message)
        coVerify(exactly = 0) { remote.emulateBlockchainMessageRequest(any(), any()) }
    }

    @Test
    fun `fee needs a utility asset on the chain`(): Unit = runBlocking {
        val chain = chain(jetton)

        val error = runCatching { service(chain).getTransferFee(transfer(jetton)) }.exceptionOrNull()

        assertEquals("Can't find utility asset for TON fixture", error?.message)
        coVerify(exactly = 0) { remote.getSeqno(any(), any()) }
    }

    @Test
    fun `jetton transfer from an undeployed wallet is signed and submitted`(): Unit = runBlocking {
        val chain = chain(utility, jetton)
        coEvery { remote.getSeqno(chain, senderAccountId) } returns 0
        coEvery { remote.loadJettonBalances(chain, any()) } returns jettonWallets(JETTON_MASTER)
        coEvery { keys.getKeypairFor(chain, publicKey) } returns BaseKeypair(ByteArray(32) { 7 }, publicKey)
        val sent = slot<SendBlockchainMessageRequest>()
        coEvery { remote.sendBlockchainMessage(chain, capture(sent)) } returns "accepted"

        val hash = service(chain).transfer(transfer(jetton, TonTransferParams(comment = null)))

        assertTrue(Regex("[0-9a-fA-F]{64}").matches(hash))
        assertTrue(sent.captured.boc.orEmpty().isNotBlank())
        coVerify(exactly = 1) { keys.getKeypairFor(chain, publicKey) }
    }

    private fun service(chain: Chain) = TonTransferService(chain, keys, accounts, remote, dao)

    private fun chain(vararg assets: Asset): Chain = mockk {
        every { id } returns CHAIN_ID
        every { name } returns "TON fixture"
        every { isTestNet } returns false
        every { this@mockk.assets } returns assets.toList()
    }

    private fun transfer(asset: Asset, params: TonTransferParams? = null) = Transfer(
        sender = publicKey.v4r2tonAddress(false),
        recipient = ByteArray(32) { 2 }.v4r2tonAddress(false),
        amount = BigDecimal.ONE,
        chainAsset = asset,
        additionalParams = params
    )

    private fun stubEmulation(chain: Chain, fees: BigInteger): CapturingSlot<EmulateMessageToWalletRequest> {
        val transaction = mockk<Transaction>()
        every { transaction.totalFees } returns fees
        val trace = mockk<Trace>()
        every { trace.transaction } returns transaction
        val consequences = mockk<MessageConsequences>()
        every { consequences.trace } returns trace
        val request = slot<EmulateMessageToWalletRequest>()
        coEvery { remote.emulateBlockchainMessageRequest(chain, capture(request)) } returns consequences
        return request
    }

    private fun jettonWallets(master: String) = JettonsBalances(
        listOf(
            JettonBalance(
                balance = "1000000000",
                walletAddress = AccountAddress(ByteArray(32) { 3 }.tonAccountId(false), false, true),
                jetton = JettonPreview(master, "Fixture jetton", "FJT", 9, "", "whitelist"),
                price = null
            )
        )
    )

    private fun feeBalance(free: BigInteger) = CachedAssetWithPriceRead(
        CachedAssetRead(
            "ton", CHAIN_ID, publicKey, 7, null, 0, true, false, null, null,
            CachedNumericState.Known(free), CachedNumericState.Missing, CachedNumericState.Missing, CachedNumericState.Missing,
            CachedNumericState.Missing, CachedNumericState.Missing, CachedNumericState.Missing
        ),
        null
    )

    private fun accountData(address: String, status: AccountStatus) =
        TonAccountData(address, 10, 0, status, emptyList(), true)

    private companion object {
        const val CHAIN_ID = "ton-fixture"
        const val JETTON_MASTER = "jetton-fixture"
    }
}
