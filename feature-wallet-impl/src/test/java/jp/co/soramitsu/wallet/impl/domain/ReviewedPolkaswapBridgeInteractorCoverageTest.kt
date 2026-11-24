package jp.co.soramitsu.wallet.impl.domain

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.ConcurrentHashMap
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.core.extrinsic.keypair_provider.KeypairProvider
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.core.rpc.FinalizedExtrinsicReader
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.wallet.impl.WalletTestModels
import jp.co.soramitsu.wallet.impl.data.repository.CrossChainSubmissionJournal
import jp.co.soramitsu.xcm.ReviewedBridgeQuote
import jp.co.soramitsu.xcm.ReviewedBridgeTransferRequest
import jp.co.soramitsu.xcm.ReviewedPolkaswapBridgeExecutor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

/** Bridge quotes and tracked submissions built from the selected, locally signable wallet. */
class ReviewedPolkaswapBridgeInteractorCoverageTest {

    private val executor = mockk<ReviewedPolkaswapBridgeExecutor>()
    private val chainRegistry = mockk<ChainRegistry>()
    private val accountInteractor = mockk<AccountInteractor>()
    private val signingProvider = mockk<KeypairProvider>()

    private val values = ConcurrentHashMap<String, String>()
    private val preferences = mockk<Preferences> {
        every { durableStorageIdentity } returns Any()
        every { getString(any()) } answers { values[firstArg()] }
        every { replaceStringsDurably(any(), any()) } answers {
            secondArg<Set<String>>().forEach(values::remove)
            values.putAll(firstArg<Map<String, String>>())
            true
        }
    }
    private val reader = mockk<FinalizedExtrinsicReader> { coEvery { anchor(ORIGIN_ID) } returns ANCHOR }
    private val submissions = CrossChainSubmissions(CrossChainSubmissionJournal(preferences), reader)

    private val lld = WalletTestModels.asset(id = "lld", symbol = "LLD", chainId = ORIGIN_ID, precision = 12)
    private val origin = WalletTestModels.chain(id = ORIGIN_ID, assets = listOf(lld))
    private val destination = WalletTestModels.chain(id = DESTINATION_ID)
    private val sender = ByteArray(32) { 1 }

    init {
        coEvery { chainRegistry.getChain(ORIGIN_ID) } returns origin
        coEvery { chainRegistry.getChain(DESTINATION_ID) } returns destination
        coEvery { accountInteractor.selectedMetaAccount() } returns wallet()
    }

    @Test
    fun `a quote request carries the selected account and a quote-only key provider`() = runBlocking {
        val request = slot<ReviewedBridgeTransferRequest>()
        coEvery { executor.quote(capture(request)) } returns QUOTE

        val quote = interactor(signing = null).quote(
            PROVIDER, ROUTE, ORIGIN_ID, DESTINATION_ID, lld, "recipient", BigDecimal("1.5")
        )

        assertSame(QUOTE, quote)
        with(request.captured) {
            assertEquals(PROVIDER, providerId)
            assertEquals(ROUTE, routeId)
            assertEquals(origin, originChain)
            assertEquals(destination, destinationChain)
            assertEquals(lld, asset)
            assertArrayEquals(sender, senderAccountId)
            assertEquals("recipient", recipientAddress)
            assertEquals(BigInteger("1500000000000"), amountInPlanks)
            assertNull(submission)
            assertEquals(CryptoType.SR25519, keypairProvider.getCryptoTypeFor(origin, sender))
            val failure = assertThrows(IllegalStateException::class.java) {
                runBlocking { keypairProvider.getKeypairFor(origin, sender) }
            }
            assertEquals("An authorized signing provider is required", failure.message)
        }
    }

    @Test
    fun `a quote uses the authorized signing provider when there is one`() = runBlocking {
        val request = slot<ReviewedBridgeTransferRequest>()
        coEvery { executor.quote(capture(request)) } returns QUOTE

        interactor().quote(PROVIDER, ROUTE, ORIGIN_ID, DESTINATION_ID, lld, "recipient", BigDecimal.ONE)

        assertSame(signingProvider, request.captured.keypairProvider)
    }

    @Test
    fun `a wallet that cannot sign for Substrate gets no quote`() {
        coEvery { accountInteractor.selectedMetaAccount() } returns wallet(cryptoType = null)

        val failure = assertThrows(IllegalArgumentException::class.java) {
            runBlocking { interactor().quote(PROVIDER, ROUTE, ORIGIN_ID, DESTINATION_ID, lld, "recipient", BigDecimal.ONE) }
        }

        assertEquals("cross_chain_signable_account_required", failure.message)
        coVerify(exactly = 0) { executor.quote(any()) }
    }

    @Test
    fun `a wallet without an account on the origin network gets no quote`() {
        coEvery { accountInteractor.selectedMetaAccount() } returns wallet(accountId = null)

        val failure = assertThrows(IllegalArgumentException::class.java) {
            runBlocking { interactor().quote(PROVIDER, ROUTE, ORIGIN_ID, DESTINATION_ID, lld, "recipient", BigDecimal.ONE) }
        }

        assertEquals("cross_chain_selected_account_unavailable", failure.message)
    }

    @Test
    fun `a submission is signed through a tracked submission of the quoted wallet`() = runBlocking {
        val request = slot<ReviewedBridgeTransferRequest>()
        coEvery { executor.submit(capture(request), QUOTE) } returns "0xhash"

        val result = interactor().submit(
            WALLET_ID, PROVIDER, ROUTE, ORIGIN_ID, DESTINATION_ID, lld, "recipient", BigDecimal("1.5"), QUOTE
        )

        assertEquals("0xhash", result.getOrThrow())
        val submission = requireNotNull(request.captured.submission)
        assertEquals(WALLET_ID, submission.walletId)
        assertSame(signingProvider, submission.keypairProvider)
        assertEquals(BigInteger("1500000000000"), request.captured.amountInPlanks)
    }

    @Test
    fun `a submission needs tracking and an authorized signing provider`() = runBlocking {
        val untracked = interactor(submissions = null).submit(
            WALLET_ID, PROVIDER, ROUTE, ORIGIN_ID, DESTINATION_ID, lld, "recipient", BigDecimal.ONE, QUOTE
        )
        val unsigned = interactor(signing = null).submit(
            WALLET_ID, PROVIDER, ROUTE, ORIGIN_ID, DESTINATION_ID, lld, "recipient", BigDecimal.ONE, QUOTE
        )

        assertEquals("Cross-chain submissions cannot be tracked", untracked.exceptionOrNull()?.message)
        assertEquals("An authorized signing provider is required", unsigned.exceptionOrNull()?.message)
        coVerify(exactly = 0) { executor.submit(any(), any()) }
    }

    private fun interactor(
        signing: KeypairProvider? = signingProvider,
        submissions: CrossChainSubmissions? = this.submissions
    ) = ReviewedPolkaswapBridgeInteractor(
        executor = executor,
        chainRegistry = chainRegistry,
        accountInteractor = accountInteractor,
        signingKeypairProvider = signing,
        crossChainSubmissions = submissions
    )

    private fun wallet(cryptoType: CryptoType? = CryptoType.SR25519, accountId: ByteArray? = sender) = MetaAccount(
        id = WALLET_ID,
        chainAccounts = emptyMap(),
        favoriteChains = emptyMap(),
        substratePublicKey = accountId,
        substrateCryptoType = cryptoType,
        substrateAccountId = accountId,
        ethereumAddress = null,
        ethereumPublicKey = null,
        tonPublicKey = null,
        isSelected = true,
        isBackedUp = true,
        googleBackupAddress = null,
        name = "Wallet",
        initialized = true
    )

    private companion object {
        const val WALLET_ID = 3L
        const val PROVIDER = "liberland-bridge"
        const val ROUTE = "route"
        val ORIGIN_ID = "6b".repeat(32)
        val DESTINATION_ID = "7e".repeat(32)
        val ANCHOR = FinalizedExtrinsicReader.Anchor("0x$ORIGIN_ID", BigInteger.valueOf(1_000), "0x" + "bb".repeat(32))
        val QUOTE = ReviewedBridgeQuote(
            providerId = PROVIDER,
            routeId = ROUTE,
            runtimeFingerprint = "runtime",
            executionFingerprint = "execution",
            originFeeInPlanks = BigInteger.ONE,
            destinationFeeInPlanks = BigInteger.ZERO,
            effectiveMinimumInPlanks = BigInteger.ONE
        )
    }
}
