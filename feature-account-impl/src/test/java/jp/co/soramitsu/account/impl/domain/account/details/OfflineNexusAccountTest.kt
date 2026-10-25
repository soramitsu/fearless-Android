package jp.co.soramitsu.account.impl.domain.account.details

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.interfaces.AssetNotNeedAccountUseCase
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.common.model.WalletEcosystem
import jp.co.soramitsu.common.utils.IrohaKeyDerivation
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class OfflineNexusAccountTest {
    @Test
    fun `new wallet exposes a production address with no runtime registry or network`() = runBlocking {
        val wallet = wallet(mapOf(UniversalWalletRegistry.taira.id to account()))
        val repository = mock(AccountRepository::class.java)
        val registry = mock(ChainRegistry::class.java)
        `when`(repository.getMetaAccount(wallet.id)).thenReturn(wallet)
        `when`(registry.getChains()).thenReturn(emptyList())
        val interactor = AccountDetailsInteractor(repository, registry, mock(AssetNotNeedAccountUseCase::class.java))

        val row = interactor.getChainProjectionsFlow(wallet.id, WalletEcosystem.Iroha).first().values.flatten().single()
        val expected = IrohaKeyDerivation.deriveAddress(MNEMONIC, chainDiscriminant = UniversalWalletRegistry.nexus.chainDiscriminant).i105
        assertEquals(expected, row.projection!!.address)
        assertNotEquals(IrohaKeyDerivation.deriveAddress(MNEMONIC).i105, row.projection.address)
        assertTrue(row.isOffline)
        assertTrue(row.chain.nodes.isEmpty())
        assertTrue(row.chain.assets.isEmpty())
        assertTrue(row.chain.explorers.isEmpty())
        assertNull(row.chain.externalApi)
        assertEquals(listOf(WalletEcosystem.Iroha to 1), interactor.getChainAccountsSummaryFlow(wallet.id).first())
        assertTrue(UniversalWalletRegistry.taira.enabledByDefault)
        assertFalse(UniversalWalletRegistry.nexus.enabledByDefault)
    }

    @Test
    fun `registry nexus aliases cannot duplicate or activate the offline account row`() = runBlocking {
        val wallet = wallet(mapOf(UniversalWalletRegistry.taira.id to account()))
        val nexusChain = offlineNexusAccount(wallet)!!.chain
        val tairaChain = nexusChain.copy(id = UniversalWalletRegistry.taira.id, name = "SORA Taira", isTestNet = true)
        val repository = mock(AccountRepository::class.java)
        val registry = mock(ChainRegistry::class.java)
        `when`(repository.getMetaAccount(wallet.id)).thenReturn(wallet)
        `when`(registry.getChains()).thenReturn(listOf(tairaChain, nexusChain, nexusChain.copy(id = UniversalWalletRegistry.nexus.chainId.uppercase())))
        val interactor = AccountDetailsInteractor(repository, registry, mock(AssetNotNeedAccountUseCase::class.java))

        val rows = interactor.getChainProjectionsFlow(wallet.id, WalletEcosystem.Iroha).first().values.flatten()

        assertEquals(2, rows.size)
        assertEquals(1, rows.count { it.isOffline })
        assertFalse(rows.single { it.chain.id == UniversalWalletRegistry.taira.id }.isOffline)
        assertEquals(listOf(WalletEcosystem.Iroha to 2), interactor.getChainAccountsSummaryFlow(wallet.id).first())
    }

    @Test
    fun `dedicated nexus key takes priority over the taira key`() {
        val dedicated = account(ByteArray(32) { (it + 1).toByte() })
        val wallet = wallet(
            mapOf(
                UniversalWalletRegistry.taira.id to account(),
                UniversalWalletRegistry.nexus.chainId to dedicated
            )
        )
        val row = offlineNexusAccount(wallet)!!
        assertTrue(row.projection!!.accountId.contentEquals(dedicated.publicKey))
        assertEquals(AccountInChain.From.CHAIN_ACCOUNT, row.from)
    }

    @Test
    fun `case variant dedicated nexus keys take priority and malformed variants block taira fallback`() {
        val dedicated = account(ByteArray(32) { (it + 1).toByte() })
        val tairaAlias = UniversalWalletRegistry.taira.chainId.uppercase()
        for (nexusAlias in listOf(UniversalWalletRegistry.nexus.id.uppercase(), UniversalWalletRegistry.nexus.chainId.uppercase())) {
            val accounts = mapOf(tairaAlias to account(), nexusAlias to dedicated)
            val row = offlineNexusAccount(wallet(accounts))!!
            assertTrue(row.projection!!.accountId.contentEquals(dedicated.publicKey))
            assertEquals(AccountInChain.From.CHAIN_ACCOUNT, row.from)
            assertNull(offlineNexusAccount(wallet(accounts + (nexusAlias to account(ByteArray(31))))))
            assertNull(offlineNexusAccount(wallet(accounts + (UniversalWalletRegistry.nexus.id to account()))))
        }
        assertEquals(AccountInChain.From.META_ACCOUNT, offlineNexusAccount(wallet(mapOf(tairaAlias to account())))!!.from)
    }

    @Test
    fun `missing malformed and conflicting iroha keys never fall back to generic wallet keys`() {
        assertNull(offlineNexusAccount(wallet(emptyMap())))
        assertNull(offlineNexusAccount(wallet(mapOf(UniversalWalletRegistry.taira.id to account(ByteArray(31))))))
        val malformedDedicated = wallet(
            mapOf(
                UniversalWalletRegistry.taira.id to account(),
                UniversalWalletRegistry.nexus.id to account(ByteArray(31))
            )
        )
        assertNull(offlineNexusAccount(malformedDedicated))
        val conflicting = wallet(
            mapOf(
                UniversalWalletRegistry.nexus.id to account(),
                UniversalWalletRegistry.nexus.chainId to account(ByteArray(32) { 2 })
            )
        )
        assertNull(offlineNexusAccount(conflicting))
    }

    private fun account(publicKey: ByteArray = IrohaKeyDerivation.deriveAccount(MNEMONIC).publicKey) =
        MetaAccount.ChainAccount(
            metaId = 1, chain = null, publicKey = publicKey, accountId = publicKey,
            cryptoType = CryptoType.ED25519, accountName = "Iroha"
        )

    private fun wallet(accounts: Map<String, MetaAccount.ChainAccount>) = MetaAccount(
        id = 1, chainAccounts = accounts, favoriteChains = emptyMap(),
        substratePublicKey = null, substrateCryptoType = null, substrateAccountId = null,
        ethereumAddress = null, ethereumPublicKey = null, tonPublicKey = null,
        isSelected = true, isBackedUp = true, googleBackupAddress = null, name = "Wallet", initialized = true
    )

    private companion object {
        const val MNEMONIC = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
    }
}
