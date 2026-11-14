package jp.co.soramitsu.walletconnect.impl.presentation

import co.jp.soramitsu.walletconnect.model.WalletConnectMetadata
import co.jp.soramitsu.walletconnect.model.WalletConnectProposal
import co.jp.soramitsu.walletconnect.model.WalletConnectProposalNamespace
import co.jp.soramitsu.walletconnect.model.WalletConnectUriException
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The interactor's session operations: what it approves, and how it answers and reports. */
class WalletConnectSessionOperationsTest {
    private val evmAddress = "0x8ba1f109551bd432803012645ac136ddd64dba72"
    private val sessionClient = FakeSessionClient()
    private val chainsRepository = mockk<ChainsRepository> {
        coEvery { getChains() } returns listOf(ethereumChain, polkadotChain)
    }
    private val wallet = mockk<MetaAccount>(relaxed = true) {
        every { id } returns 1L
        every { chainAccounts } returns emptyMap()
        every { ethereumAddress } returns evmAddress.fromHex()
        every { substrateAccountId } returns null
    }
    private val accountRepository = mockk<AccountRepository> {
        coEvery { allMetaAccounts() } returns listOf(wallet)
    }
    private val interactor = WalletConnectInteractorImpl(
        chainsRepository = chainsRepository,
        ethereumSource = mockk(),
        accountRepository = accountRepository,
        keypairProvider = mockk(),
        runtimeSource = { null },
        transactionSender = mockk(),
        featureToggles = featureToggleStore(),
        sessionClient = sessionClient,
        scope = CoroutineScope(Dispatchers.Unconfined)
    )

    @Test
    fun `approval offers the selected wallet's accounts and only the methods the wallet signs`() {
        var approved = false
        runBlocking {
            interactor.approveSession(proposal(), setOf(1L), setOf(ethereumChain.caip2id), onSuccess = {
                approved = true
            }, onError = { throw it })
        }

        val namespace = sessionClient.approved.single().getValue("eip155")
        assertTrue(approved)
        assertEquals(listOf("eip155:1:$evmAddress"), namespace.accounts)
        assertEquals(listOf("personal_sign", "eth_sendTransaction"), namespace.methods)
        assertEquals(listOf("eip155:1"), namespace.chains)
    }

    @Test
    fun `rejection uses the user rejected code and silent rejection the unsupported chains code`() {
        interactor.rejectSession(proposal(), onError = { throw it })
        interactor.silentRejectSession(proposal(), onError = { throw it })

        assertEquals(listOf(7L to 5000L, 7L to 5100L), sessionClient.rejected)
    }

    @Test
    fun `pairing and disconnect failures reach the caller`() {
        sessionClient.operationFailure = WalletConnectUriException("v1")
        var pairError: Throwable? = null
        var disconnectError: Throwable? = null

        interactor.pair("wc:abc@1", onError = { pairError = it })
        interactor.disconnectSession("topic", onError = { disconnectError = it })

        assertTrue(pairError is WalletConnectUriException)
        assertTrue(disconnectError is WalletConnectUriException)
    }

    @Test
    fun `pairing and disconnect report success`() {
        var paired = false
        var disconnected = false

        interactor.pair("wc:uri", onSuccess = { paired = true })
        interactor.disconnectSession("topic", onSuccess = { disconnected = true }, onError = { throw it })

        assertTrue(paired && disconnected)
        assertEquals(listOf("wc:uri"), sessionClient.paired)
        assertEquals(listOf("topic"), sessionClient.disconnected)
    }

    @Test
    fun `rejecting a request answers it with the user rejected error`() {
        var rejected = false
        interactor.rejectSessionRequest(TEST_TOPIC, 9, onSuccess = { rejected = true }, onError = { throw it })

        assertTrue(rejected)
        assertEquals(WalletConnectResponse.Error(9, 4001, "User rejected request"), sessionClient.responses.single())
    }

    @Test
    fun `signable methods follow the namespace`() {
        assertEquals(
            setOf("eth_signTransaction", "eth_sendTransaction", "personal_sign", "eth_signTypedData", "eth_sign", "eth_signTypedData_v4"),
            WalletConnectInteractorImpl.signableMethods("eip155")
        )
        assertEquals(setOf("polkadot_signTransaction", "polkadot_signMessage"), WalletConnectInteractorImpl.signableMethods("polkadot"))
        assertTrue(WalletConnectInteractorImpl.signableMethods("solana").isEmpty())
    }

    @Test
    fun `lookups go to the session client`() {
        sessionClient.activeSession = session("eip155:1:$evmAddress")
        sessionClient.pending = listOf(sessionRequest(1, "personal_sign", "[]", "eip155:1"))

        assertEquals(TEST_TOPIC, interactor.getActiveSessionByTopic(TEST_TOPIC)?.topic)
        assertEquals(1, interactor.getPendingListOfSessionRequests(TEST_TOPIC).size)
        assertEquals(null, interactor.getVerifyContext(TEST_TOPIC, 1))
    }

    private fun proposal() = WalletConnectProposal(
        id = 7,
        pairingTopic = "pairing",
        proposer = WalletConnectMetadata("dApp", "", "https://app.example.org", emptyList()),
        proposerPublicKey = "00".repeat(32),
        requiredNamespaces = emptyMap(),
        optionalNamespaces = mapOf(
            "eip155" to WalletConnectProposalNamespace(
                chains = listOf("eip155:1"),
                methods = listOf("personal_sign", "eth_sendTransaction", "wallet_switchEthereumChain"),
                events = listOf("accountsChanged")
            )
        ),
        relayProtocol = "irn",
        expiryTimestamp = 0
    )
}
