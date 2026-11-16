package jp.co.soramitsu.walletconnect.impl.presentation

import co.jp.soramitsu.walletconnect.model.WalletConnectMetadata
import co.jp.soramitsu.walletconnect.model.WalletConnectProposal
import co.jp.soramitsu.walletconnect.model.WalletConnectProposalNamespace
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sessions for proposals with required namespaces, and the check that the wallet has every required network. */
class WalletConnectRequiredNamespacesTest {
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
    fun requiredAndSelectedOptionalPermissionsAreMergedWithoutDuplicates(): Unit = runBlocking {
        var approved = false

        interactor.approveSession(
            proposal(),
            setOf(1L, 99L),
            setOf("eip155:1"),
            onSuccess = { approved = true },
            onError = { throw it }
        )

        val namespace = sessionClient.approved.single().getValue("eip155")
        assertTrue(approved)
        assertEquals(listOf("eip155:1"), namespace.chains)
        assertEquals(listOf("eip155:1:$evmAddress"), namespace.accounts)
        assertEquals(listOf("chainChanged", "accountsChanged"), namespace.events)
        assertEquals(listOf("personal_sign", "eth_signTypedData_v4"), namespace.methods)
    }

    @Test
    fun withoutSelectedOptionalNetworksOnlyTheRequiredNamespaceIsApproved(): Unit = runBlocking {
        interactor.approveSession(proposal(), setOf(1L), emptySet(), onError = { throw it })

        val namespaces = sessionClient.approved.single()
        assertEquals(setOf("eip155"), namespaces.keys)
        assertEquals(listOf("chainChanged"), namespaces.getValue("eip155").events)
        assertEquals(listOf("personal_sign"), namespaces.getValue("eip155").methods)
    }

    @Test
    fun failureToReadWalletsIsReportedInsteadOfApproving(): Unit = runBlocking {
        coEvery { accountRepository.allMetaAccounts() } throws IllegalStateException("database closed")
        var failure: Throwable? = null

        interactor.approveSession(proposal(), setOf(1L), emptySet(), onError = { failure = it })

        assertEquals("database closed", failure?.message)
        assertTrue(sessionClient.approved.isEmpty())
    }

    @Test
    fun proposalsAreSupportedOnlyWhenEveryRequiredNetworkIsKnown(): Unit = runBlocking {
        assertTrue(interactor.checkChainsSupported(proposal()).getOrThrow())
        assertTrue(interactor.checkChainsSupported(proposal().copy(requiredNamespaces = emptyMap())).getOrThrow())

        val unknownNetwork = proposal().copy(
            requiredNamespaces = mapOf(
                "eip155" to WalletConnectProposalNamespace(listOf("eip155:1", "eip155:56"), listOf("personal_sign"), emptyList())
            )
        )
        assertFalse(interactor.checkChainsSupported(unknownNetwork).getOrThrow())
    }

    private fun proposal() = WalletConnectProposal(
        id = 11,
        pairingTopic = "pairing",
        proposer = WalletConnectMetadata("dApp", "", "https://app.example.org", emptyList()),
        proposerPublicKey = "00".repeat(32),
        requiredNamespaces = mapOf(
            "eip155" to WalletConnectProposalNamespace(
                chains = listOf("eip155:1"),
                methods = listOf("personal_sign", "wallet_addEthereumChain"),
                events = listOf("chainChanged")
            )
        ),
        optionalNamespaces = mapOf(
            "eip155" to WalletConnectProposalNamespace(
                chains = listOf("eip155:1"),
                methods = listOf("eth_signTypedData_v4", "personal_sign"),
                events = listOf("accountsChanged", "chainChanged")
            )
        ),
        relayProtocol = "irn",
        expiryTimestamp = 0
    )
}
