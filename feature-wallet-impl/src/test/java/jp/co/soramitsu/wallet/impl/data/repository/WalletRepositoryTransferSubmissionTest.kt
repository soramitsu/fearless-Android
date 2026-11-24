package jp.co.soramitsu.wallet.impl.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.core.extrinsic.keypair_provider.KeypairProvider
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.coredb.dao.OperationDao
import jp.co.soramitsu.coredb.model.OperationLocal
import jp.co.soramitsu.fearless_utils.encrypt.keypair.BaseKeypair
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.impl.data.network.blockchain.EthereumRemoteSource
import jp.co.soramitsu.wallet.impl.data.network.blockchain.SubstrateRemoteSource
import jp.co.soramitsu.wallet.impl.data.repository.tranfser.TransferServiceProvider
import jp.co.soramitsu.wallet.impl.domain.SendPausePolicy
import jp.co.soramitsu.wallet.impl.domain.SendPausedException
import jp.co.soramitsu.wallet.impl.domain.model.SubstrateTransferParams
import jp.co.soramitsu.wallet.impl.domain.model.Transfer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.core.models.Asset as CoreAsset

/** The fee and send boundary of [WalletRepositoryImpl], driven through the real transfer services. */
class WalletRepositoryTransferSubmissionTest {
    private class Call(
        val accountId: ByteArray?,
        val transfer: Transfer,
        val additional: (suspend ExtrinsicBuilder.() -> Unit)?,
        val batchAll: Boolean
    )

    private fun asset(chainId: String) = CoreAsset(
        "dot", "Reviewed token", "DOT", "", chainId, chainId, null, false, null, 2,
        CoreAsset.StakingType.UNSUPPORTED, null, false, true, ChainAssetType.Normal, null, null, null, true
    )

    private fun chain(ecosystem: Ecosystem, history: Chain.ExternalApi.Section?) = Chain(
        id = CHAIN_ID, paraId = null, rank = null, name = "Reviewed chain", minSupportedVersion = null,
        assets = listOf(asset(CHAIN_ID)), nodes = emptyList(), explorers = emptyList(),
        externalApi = history?.let { Chain.ExternalApi(staking = null, history = it, crowdloans = null) },
        icon = "", addressPrefix = 42, isEthereumBased = ecosystem != Ecosystem.Substrate, isTestNet = false,
        hasCrowdloans = false, parentId = null, supportStakingPool = false,
        isEthereumChain = ecosystem == Ecosystem.Ethereum, chainlinkProvider = false, supportNft = false,
        isUsesAppId = false, identityChain = null, ecosystem = ecosystem, remoteAssetsSource = null
    )

    private inner class Fixture(
        ecosystem: Ecosystem = Ecosystem.Substrate,
        history: Chain.ExternalApi.Section? = null,
        pausePolicy: SendPausePolicy = SendPausePolicy.Unrestricted
    ) {
        private val hexAccounts = ecosystem != Ecosystem.Substrate
        val chain = chain(ecosystem, history)
        val account = ByteArray(if (hexAccounts) 20 else 32) { 1 }
        fun address(accountId: ByteArray) = if (hexAccounts) accountId.toHexString(true) else accountId.toAddress(42)
        val sender = address(account)
        val recipient = address(ByteArray(account.size) { 2 })
        val substrate = mockk<SubstrateRemoteSource>()
        val ethereum = mockk<EthereumRemoteSource>()
        val keypairs = mockk<KeypairProvider>()
        val operations = mockk<OperationDao>()
        val fees = mutableListOf<Call>()
        val sent = mutableListOf<Call>()
        val keyRequests = mutableListOf<ByteArray>()
        val stored = mutableListOf<OperationLocal>()
        val repository = WalletRepositoryImpl(
            substrateSource = substrate, ethereumSource = ethereum, operationDao = operations,
            httpExceptionHandler = mockk(), phishingApi = mockk(), assetDao = mockk(), walletConstants = mockk(),
            phishingDao = mockk(), coingeckoApi = mockk(), chainRegistry = mockk(), remoteConfigFetcher = mockk(),
            accountRepository = mockk(), chainsRepository = mockk(), extrinsicService = mockk(),
            remoteStorageSource = mockk(), pricesSyncService = mockk(),
            transferServiceProvider = TransferServiceProvider(
                substrateSource = substrate, ethereumRemoteSource = ethereum, keyPairRepository = keypairs,
                accountRepository = mockk(), tonRemoteSource = mockk(), assetDao = mockk(),
                bitcoinIndexerClient = mockk(), solanaRpcClient = mockk(), solanaBalanceSync = mockk(),
                irohaToriiClient = mockk(), sendPausePolicy = pausePolicy
            ),
            tonRemoteSource = mockk(), portfolioVisibility = mockk(), currentAccountAssetReader = mockk()
        )

        init {
            coEvery { substrate.getTransferFee(any(), any(), any(), any()) } answers {
                fees += Call(null, secondArg(), thirdArg(), arg(3))
                BigInteger.valueOf(55)
            }
            coEvery { substrate.performTransfer(any(), any(), any(), any(), any()) } answers {
                sent += Call(firstArg<ByteArray>().copyOf(), thirdArg(), arg(3), arg(4))
                HASH
            }
            every { ethereum.listenGas(any(), any()) } returns flowOf(BigInteger.valueOf(55))
            coEvery { keypairs.getKeypairFor(any(), any()) } answers {
                keyRequests += secondArg<ByteArray>().copyOf()
                BaseKeypair(ByteArray(32) { 3 }, ByteArray(32) { 4 })
            }
            coEvery { ethereum.performTransfer(any(), any(), any()) } answers {
                sent += Call(null, secondArg(), null, false)
                Result.success(HASH)
            }
            coEvery { operations.insert(any()) } answers { stored += firstArg<OperationLocal>() }
        }

        fun transfer(from: String = sender, chainId: String = CHAIN_ID) = Transfer(
            sender = from, recipient = recipient, amount = BigDecimal("12.34"), chainAsset = asset(chainId),
            estimateFee = BigDecimal("0.55"), additionalParams = SubstrateTransferParams(null, null)
        )

        suspend fun send(
            transfer: Transfer = transfer(),
            additional: (suspend ExtrinsicBuilder.() -> Unit)? = null,
            batchAll: Boolean = false
        ) = repository.performTransfer(account, chain, transfer, additional, batchAll)
    }

    private suspend fun failure(block: suspend () -> Unit): Throwable {
        var observed: Throwable? = null
        try { block() } catch (error: Throwable) { observed = error }
        return requireNotNull(observed) { "Expected the call to fail" }
    }

    @Test fun acceptedTransferIsSentOnceAndRecordedAsPending() = runTest {
        val f = Fixture()
        assertEquals(HASH, f.send())
        val sent = f.sent.single()
        assertArrayEquals(f.account, sent.accountId)
        assertEquals(null, sent.additional)
        assertEquals(false, sent.batchAll)
        val row = f.stored.single()
        assertEquals(HASH, row.hash)
        assertEquals(CHAIN_ID, row.chainId)
        assertEquals(f.sender, row.address)
        assertEquals(f.sender, row.sender)
        assertEquals(f.recipient, row.receiver)
        assertEquals(BigInteger.valueOf(1234), row.amount)
        assertEquals(BigInteger.valueOf(55), row.fee)
        assertEquals(OperationLocal.Status.PENDING, row.status)
        assertEquals(OperationLocal.Source.APP, row.source)
    }

    @Test fun historyWriteFailureAfterBroadcastStillReturnsTheAcceptedHash() = runTest {
        val f = Fixture()
        coEvery { f.operations.insert(any()) } throws IllegalStateException("database unavailable")
        // A failure here would let the user confirm again and send the same payment twice.
        assertEquals(HASH, f.send())
        assertEquals(1, f.sent.size)
        assertTrue(f.stored.isEmpty())
    }

    @Test fun historyWriteFinishesWhenTheCallerIsCancelledAfterBroadcast() = runTest {
        val f = Fixture()
        val writing = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        coEvery { f.operations.insert(any()) } coAnswers {
            writing.complete(Unit)
            release.await()
            f.stored += firstArg<OperationLocal>()
        }
        var result: String? = null
        val send = launch { result = f.send() }
        writing.await()
        send.cancel()
        release.complete(Unit)
        send.join()
        assertEquals(HASH, result)
        assertEquals(1, f.sent.size)
        assertEquals(HASH, f.stored.single().hash)
    }

    @Test fun substrateSenderOtherThanTheReviewedAccountIsRefusedBeforeSigning() = runTest {
        val f = Fixture()
        val other = f.transfer(from = f.address(ByteArray(32) { 9 }))
        assertTrue(failure { f.send(other) } is IllegalArgumentException)
        assertTrue(f.sent.isEmpty())
        assertTrue(f.stored.isEmpty())
    }

    @Test fun ethereumSenderOtherThanTheReviewedAccountIsRefusedBeforeKeyAccess() = runTest {
        val f = Fixture(Ecosystem.Ethereum)
        val other = f.transfer(from = f.address(ByteArray(20) { 9 }))
        assertTrue(failure { f.send(other) } is IllegalArgumentException)
        assertTrue(f.keyRequests.isEmpty())
        assertTrue(f.sent.isEmpty())
        assertTrue(f.stored.isEmpty())
    }

    @Test fun ethereumTransferSignsWithTheReviewedAccount() = runTest {
        val f = Fixture(Ecosystem.Ethereum)
        assertEquals(HASH, f.send())
        assertArrayEquals(f.account, f.keyRequests.single())
        assertEquals(1, f.sent.size)
        assertEquals(f.sender, f.stored.single().sender)
    }

    @Test fun assetOfAnotherChainIsRefusedBeforeSigning() = runTest {
        val f = Fixture()
        assertTrue(failure { f.send(f.transfer(chainId = "other-chain")) } is IllegalArgumentException)
        assertTrue(f.sent.isEmpty())
        assertTrue(f.stored.isEmpty())
    }

    @Test fun ethereumBasedTransferSignsWithTheReviewedHexAccount() = runTest {
        val f = Fixture(Ecosystem.EthereumBased)
        assertEquals(HASH, f.send())
        assertArrayEquals(f.account, f.sent.single().accountId)
        assertEquals(f.sender, f.stored.single().address)
        assertEquals(f.recipient, f.stored.single().receiver)
    }

    @Test fun substrateFeeAndSendKeepTheReviewedAdditionalCallsAndBatchPolicy() = runTest {
        val f = Fixture()
        val additional: suspend ExtrinsicBuilder.() -> Unit = {}
        assertEquals(BigDecimal("0.55"), f.repository.getTransferFee(f.chain, f.transfer(), additional, true))
        assertSame(additional, f.fees.single().additional)
        assertTrue(f.fees.single().batchAll)
        assertEquals(HASH, f.send(additional = additional, batchAll = true))
        assertSame(additional, f.sent.single().additional)
        assertTrue(f.sent.single().batchAll)
    }

    @Test fun otherProtocolsRefuseAdditionalCallsInsteadOfDroppingThem() = runTest {
        val f = Fixture(Ecosystem.Ethereum)
        val additional: suspend ExtrinsicBuilder.() -> Unit = {}
        for ((calls, batchAll) in listOf(additional to false, null to true)) {
            val fee = failure { f.repository.getTransferFee(f.chain, f.transfer(), calls, batchAll) }
            assertTrue(fee is IllegalArgumentException)
            assertTrue(failure { f.send(additional = calls, batchAll = batchAll) } is IllegalArgumentException)
        }
        verify(exactly = 0) { f.ethereum.listenGas(any(), any()) }
        assertTrue(f.keyRequests.isEmpty())
        assertTrue(f.sent.isEmpty())
        assertTrue(f.stored.isEmpty())
    }

    @Test fun pausedNetworkStaysPausedWhenAdditionalCallsArePassed() = runTest {
        val paused = SendPausePolicy(
            bitcoinSendEnabled = { false },
            solanaSendEnabled = { true },
            pausedErrorFactory = { SendPausedException("Sending paused", "Sending on ${it.name} is paused") }
        )
        val bitcoin = Chain.ExternalApi.Section(Chain.ExternalApi.Section.Type.BITCOIN, "https://indexer.invalid")
        val f = Fixture(history = bitcoin, pausePolicy = paused)
        val additional: suspend ExtrinsicBuilder.() -> Unit = {}
        val fee = failure { f.repository.getTransferFee(f.chain, f.transfer(), additional, false) }
        assertTrue(fee is SendPausedException)
        assertTrue(failure { f.send(additional = additional) } is SendPausedException)
        coVerify(exactly = 0) { f.substrate.getTransferFee(any(), any(), any(), any()) }
        assertTrue(f.sent.isEmpty())
        assertTrue(f.stored.isEmpty())
    }

    private companion object {
        const val CHAIN_ID = "reviewed-chain"
        const val HASH = "0xaccepted"
    }
}
