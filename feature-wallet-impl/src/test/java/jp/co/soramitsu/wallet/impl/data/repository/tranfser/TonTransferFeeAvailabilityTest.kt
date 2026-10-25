package jp.co.soramitsu.wallet.impl.data.repository.tranfser

import io.mockk.*
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.data.network.ton.AccountStatus
import jp.co.soramitsu.common.data.network.ton.TonAccountData
import jp.co.soramitsu.common.utils.tonAccountId
import jp.co.soramitsu.common.utils.v4r2tonAddress
import jp.co.soramitsu.core.extrinsic.keypair_provider.KeypairProvider
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.coredb.dao.AssetDao
import jp.co.soramitsu.coredb.model.cache.*
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.TonRemoteSource
import jp.co.soramitsu.wallet.impl.domain.model.Transfer
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

/** Actual fee service with local read/RPC doubles. No signing key or remote submission is used. */
class TonTransferFeeAvailabilityTest {
    private class FeeCancelled(val marker: Any) : CancellationException("fee request cancelled")
    private class ReachedRpc(val marker: Any) : RuntimeException("public RPC boundary")
    private class Fixture {
        val publicKey = ByteArray(32) { 1 }
        val utility = mockk<Asset>(relaxed = true) {
            every { id } returns "ton"
            every { chainId } returns "ton-fixture"
            every { isUtility } returns true
            every { type } returns ChainAssetType.Normal
            every { precision } returns 9
        }
        val jetton = mockk<Asset>(relaxed = true) {
            every { id } returns "jetton-fixture"
            every { chainId } returns "ton-fixture"
            every { type } returns ChainAssetType.Jetton
            every { precision } returns 9
        }
        val chain = mockk<Chain> {
            every { id } returns "ton-fixture"
            every { name } returns "TON fixture"
            every { isTestNet } returns false
            every { assets } returns listOf(utility, jetton)
        }
        val account = mockk<MetaAccount> {
            every { id } returns 7L
            every { tonPublicKey } returns publicKey
        }
        val accounts = mockk<AccountRepository>()
        val keys = mockk<KeypairProvider>()
        val remote = mockk<TonRemoteSource>()
        val dao = mockk<AssetDao>()
        val service = TonTransferService(chain, keys, accounts, remote, dao)
        init { coEvery { accounts.getSelectedMetaAccount() } returns account }
        fun transfer(config: Asset = jetton) = Transfer(
            sender = publicKey.v4r2tonAddress(false), recipient = ByteArray(32) { 2 }.v4r2tonAddress(false),
            amount = BigDecimal.ONE, chainAsset = config
        )
        fun row(free: CachedNumericState<BigInteger>) = CachedAssetWithPriceRead(
            CachedAssetRead("ton", "ton-fixture", publicKey, 7, null, 0, true, false, null, null,
                free, CachedNumericState.Missing, CachedNumericState.Missing, CachedNumericState.Missing,
                CachedNumericState.Missing, CachedNumericState.Missing, CachedNumericState.Missing), null
        )
        fun cache(row: CachedAssetWithPriceRead?) {
            coEvery { dao.getAsset(7, publicKey, "ton-fixture", "ton") } returns row
        }
        fun prepareNativeFee() {
            cache(null)
            coEvery { remote.getSeqno(chain, any()) } returns 1
            coEvery { remote.getRawTime(chain, any()) } returns 1_700_000_000
            coEvery { remote.loadAccountData(chain, any()) } returns TonAccountData(
                publicKey.tonAccountId(false), 10, 0, AccountStatus.active, emptyList(), true
            )
        }
    }

    @Test fun missingMalformedAndNegativeJettonFeeBalancesStopBeforeRpc() = runBlocking {
        val malformed = CachedNumericDecoder.integer(CachedNumericCell("text", 3, "bad".toByteArray()))
        for (state in listOf(null, CachedNumericState.Missing, malformed, CachedNumericState.Known(BigInteger.valueOf(-1)))) {
            val f = Fixture(); f.cache(state?.let(f::row))
            val error = runCatching { f.service.getTransferFee(f.transfer()) }.exceptionOrNull()
            assertEquals("TON fee balance is unavailable", error?.message)
            coVerify(exactly = 0) { f.remote.getSeqno(any(), any()) }
            verify { f.keys wasNot Called }
        }
    }

    @Test fun explicitZeroRetainsInsufficientFeeBalanceInsteadOfUnknown() = runBlocking {
        val f = Fixture(); f.cache(f.row(CachedNumericState.Known(BigInteger.ZERO)))
        val error = runCatching { f.service.getTransferFee(f.transfer()) }.exceptionOrNull()
        assertEquals("Can't calculate fee: Not enough tokens for fee", error?.message)
        coVerify(exactly = 0) { f.remote.getSeqno(any(), any()) }
    }

    @Test fun positiveKnownJettonFeeBalanceAdmitsTheExistingRpcPath() = runBlocking {
        val f = Fixture(); f.cache(f.row(CachedNumericState.Known(BigInteger.ONE)))
        val boundary = ReachedRpc(Any())
        coEvery { f.remote.getSeqno(f.chain, any()) } throws boundary
        assertSame(boundary, runCatching { f.service.getTransferFee(f.transfer()) }.exceptionOrNull())
        coVerify(exactly = 1) { f.remote.getSeqno(f.chain, f.publicKey.tonAccountId(false)) }
        verify { f.keys wasNot Called }
    }

    @Test fun nativeTonFeeEstimationDoesNotRequireAJettonFeeCacheRow() = runBlocking {
        val f = Fixture(); f.cache(null)
        val boundary = ReachedRpc(Any())
        coEvery { f.remote.getSeqno(f.chain, any()) } throws boundary
        assertSame(boundary, runCatching { f.service.getTransferFee(f.transfer(f.utility)) }.exceptionOrNull())
    }

    @Test fun callerCancellationReachesHeldCacheReadAndRetainsItsCause() = runBlocking {
        val f = Fixture(); val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        var cleaned = false
        coEvery { f.dao.getAsset(any(), any(), any(), any()) } coAnswers {
            try { entered.complete(Unit); release.await(); null } finally { cleaned = true }
        }
        val reason = CancellationException("fee screen closed")
        var completion: Throwable? = null
        val job = launch { f.service.getTransferFee(f.transfer()) }
        job.invokeOnCompletion { completion = it }
        try {
            withTimeout(5_000) { entered.await() }
            job.cancel(reason)
            withTimeout(5_000) { job.join() }
            assertTrue(cleaned)
            assertSame(reason, completion)
            coVerify(exactly = 0) { f.remote.getSeqno(any(), any()) }
        } finally { release.complete(Unit); job.cancelAndJoin() }
    }

    @Test fun rawTimeCancellationDoesNotBecomeAClockFallback() = runBlocking {
        val f = Fixture(); f.prepareNativeFee()
        val reason = FeeCancelled(Any())
        coEvery { f.remote.getRawTime(any(), any()) } throws reason
        assertSame(reason, runCatching { f.service.getTransferFee(f.transfer(f.utility)) }.exceptionOrNull())
        coVerify(exactly = 0) { f.remote.emulateBlockchainMessageRequest(any(), any()) }
    }

    @Test fun emulationCancellationDoesNotBecomeAnAccountStatusLookupOrGenericError() = runBlocking {
        val f = Fixture(); f.prepareNativeFee()
        val reason = FeeCancelled(Any())
        coEvery { f.remote.emulateBlockchainMessageRequest(any(), any()) } throws reason
        assertSame(reason, runCatching { f.service.getTransferFee(f.transfer(f.utility)) }.exceptionOrNull())
        coVerify(exactly = 1) { f.remote.loadAccountData(any(), any()) }
        coVerify(exactly = 0) { f.remote.sendBlockchainMessage(any(), any()) }
        verify { f.keys wasNot Called }
    }
}
