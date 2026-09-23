package jp.co.soramitsu.account.impl.domain

import java.lang.reflect.Proxy
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.data.Keypair
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.common.utils.BitcoinKeyDerivation
import jp.co.soramitsu.common.utils.IrohaKeyDerivation
import jp.co.soramitsu.common.utils.SolanaKeyDerivation
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.coredb.dao.MetaAccountDao
import jp.co.soramitsu.coredb.model.ChainAccountLocal
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.MnemonicCreator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyNetworkAccountUpgradeTest {
    @Test
    fun `adds supported public accounts from authoritative root while retaining all old identities`() = runBlocking {
        val fixture = Fixture()
        val legacy = fixture.wallets.getValue(1)
        fixture.upgrade.upgrade()
        val upgraded = fixture.wallets.getValue(1)
        assertEquals(setOf("legacy-chain", BTC, SOL, TAIRA), upgraded.chainAccounts.keys)
        assertSame(legacy.substratePublicKey, upgraded.substratePublicKey)
        assertSame(legacy.ethereumPublicKey, upgraded.ethereumPublicKey)
        assertSame(legacy.tonPublicKey, upgraded.tonPublicKey)
        assertSame(legacy.chainAccounts["legacy-chain"], upgraded.chainAccounts["legacy-chain"])
        assertEquals(legacy.name, upgraded.name)
        assertEquals(legacy.isSelected, upgraded.isSelected)
        assertEquals(legacy.isBackedUp, upgraded.isBackedUp)
        assertArrayEquals(BitcoinKeyDerivation.deriveAccount(MNEMONIC).publicKey, upgraded.chainAccounts.getValue(BTC).publicKey)
        assertArrayEquals(SolanaKeyDerivation.deriveAccount(MNEMONIC).publicKey, upgraded.chainAccounts.getValue(SOL).publicKey)
        assertArrayEquals(IrohaKeyDerivation.deriveAccount(MNEMONIC).publicKey, upgraded.chainAccounts.getValue(TAIRA).publicKey)
        assertTrue(fixture.fallbackReads.isEmpty())
        assertEquals(3, fixture.inserts.size)
        fixture.upgrade.upgrade()
        assertEquals(3, fixture.inserts.size)
    }

    @Test
    fun `failed optional insertion retries only missing network without blocking other additions`() = runBlocking {
        val fixture = Fixture()
        fixture.failedChain = BTC
        fixture.upgrade.upgrade()
        assertEquals(setOf("legacy-chain", SOL, TAIRA), fixture.wallets.getValue(1).chainAccounts.keys)
        fixture.failedChain = null
        fixture.upgrade.upgrade()
        assertEquals(setOf("legacy-chain", BTC, SOL, TAIRA), fixture.wallets.getValue(1).chainAccounts.keys)
        assertEquals(3, fixture.inserts.size)
    }

    @Test
    fun `preserves independent existing network key under its alternate chain id`() = runBlocking {
        val fixture = Fixture()
        val independent = MetaAccount.ChainAccount(1, null, ByteArray(33) { 8 }, ByteArray(32) { 9 }, CryptoType.ECDSA, "Imported BTC")
        val original = fixture.wallets.getValue(1)
        fixture.wallets[1] = original.copy(chainAccounts = original.chainAccounts + (UniversalWalletRegistry.bitcoinMainnet.chainId to independent))
        fixture.upgrade.upgrade()
        assertSame(independent, fixture.wallets.getValue(1).chainAccounts[UniversalWalletRegistry.bitcoinMainnet.chainId])
        assertTrue(fixture.inserts.none { it.chainId == BTC })
        assertEquals(2, fixture.inserts.size)
    }

    @Test
    fun `seed only and watch only wallets remain untouched when no mnemonic exists`() = runBlocking {
        val fixture = Fixture()
        fixture.withEntropy = false
        val original = fixture.wallets.getValue(1)
        fixture.upgrade.upgrade()
        assertSame(original, fixture.wallets[1])
        assertTrue(fixture.inserts.isEmpty())
        assertEquals(listOf("getEthereumSecrets", "getTonSecrets"), fixture.fallbackReads)
    }

    @Test
    fun `unreadable wallet cannot block another valid legacy wallet`() = runBlocking {
        val fixture = Fixture()
        fixture.wallets[2] = fixture.wallets.getValue(1).copy(id = 2, chainAccounts = emptyMap())
        fixture.unreadableWallet = 1
        fixture.upgrade.upgrade()
        assertEquals(setOf("legacy-chain"), fixture.wallets.getValue(1).chainAccounts.keys)
        assertEquals(setOf(BTC, SOL, TAIRA), fixture.wallets.getValue(2).chainAccounts.keys)
    }

    @Test
    fun `catalog unavailable leaves old wallet usable and next run can retry`() = runBlocking {
        val fixture = Fixture()
        fixture.catalogUnavailable = true
        val original = fixture.wallets.getValue(1)
        fixture.upgrade.upgrade()
        assertSame(original, fixture.wallets[1])
        fixture.catalogUnavailable = false
        fixture.upgrade.upgrade()
        assertEquals(3, fixture.inserts.size)
    }

    @Test
    fun `wallet deleted after enumeration is skipped and cannot be recreated`() = runBlocking {
        val fixture = Fixture()
        fixture.deleteAfterEnumeration = true
        fixture.upgrade.upgrade()
        assertTrue(fixture.wallets.isEmpty())
        assertTrue(fixture.inserts.isEmpty())
    }

    @Test
    fun `cancellation is propagated without publishing new accounts`() {
        val fixture = Fixture()
        fixture.cancel = true
        assertThrows(CancellationException::class.java) { runBlocking { fixture.upgrade.upgrade() } }
        assertTrue(fixture.inserts.isEmpty())
    }

    private class Fixture {
        val wallets = linkedMapOf(1L to MetaAccount(
            id = 1, chainAccounts = mapOf("legacy-chain" to MetaAccount.ChainAccount(1, null, byteArrayOf(7), byteArrayOf(7), CryptoType.ED25519, "Custom")),
            favoriteChains = emptyMap(), substratePublicKey = ByteArray(32) { 1 }, substrateCryptoType = CryptoType.SR25519,
            substrateAccountId = ByteArray(32) { 1 }, ethereumAddress = ByteArray(20) { 2 }, ethereumPublicKey = ByteArray(33) { 2 },
            tonPublicKey = ByteArray(32) { 3 }, isSelected = true, isBackedUp = false, googleBackupAddress = null,
            name = "Legacy wallet", initialized = true
        ))
        val inserts = mutableListOf<ChainAccountLocal>()
        val fallbackReads = mutableListOf<String>()
        var failedChain: String? = null
        var unreadableWallet: Long? = null
        var withEntropy = true
        var catalogUnavailable = false
        var deleteAfterEnumeration = false
        var cancel = false
        val repository = proxy<AccountRepository> { method, args ->
            when (method) {
                "allMetaAccounts" -> wallets.values.toList().also { if (deleteAfterEnumeration) wallets.clear() }
                "getMetaAccount" -> wallets.getValue(args[0] as Long)
                "getSubstrateSecrets" -> {
                    if (cancel) throw CancellationException("synthetic cancellation")
                    if (args[0] == unreadableWallet) error("synthetic unavailable wallet")
                    if (withEntropy) SubstrateSecrets(
                        substrateKeyPair = Keypair(ByteArray(32) { 1 }, ByteArray(32) { 2 }),
                        entropy = MnemonicCreator.fromWords(MNEMONIC).entropy
                    ) else null
                }
                "getEthereumSecrets", "getTonSecrets" -> { fallbackReads += method; null }
                else -> error("Unexpected repository mutation: $method")
            }
        }
        val dao = proxy<MetaAccountDao> { method, args ->
            check(method == "insertDerivedChainAccountsIfAbsent")
            @Suppress("UNCHECKED_CAST")
            val additions = args[0] as List<ChainAccountLocal>
            for (addition in additions) {
                if (addition.chainId == failedChain) error("synthetic write failure")
                val current = wallets.getValue(addition.metaId)
                if (addition.chainId !in current.chainAccounts) {
                    inserts += addition
                    wallets[addition.metaId] = current.copy(chainAccounts = current.chainAccounts + (addition.chainId to MetaAccount.ChainAccount(
                        addition.metaId, null, addition.publicKey, addition.accountId, addition.cryptoType, addition.name
                    )))
                }
            }
            Unit
        }
        val upgrade = LegacyNetworkAccountUpgrade(repository, dao) {
            if (catalogUnavailable) error("synthetic catalog failure")
            setOf(BTC, SOL, TAIRA, UniversalWalletRegistry.nexus.id, UniversalWalletRegistry.bitcoinTestnet.id)
        }
    }

    companion object {
        private const val MNEMONIC = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        private val BTC = UniversalWalletRegistry.bitcoinMainnet.id
        private val SOL = UniversalWalletRegistry.solanaMainnet.id
        private val TAIRA = UniversalWalletRegistry.taira.id

        @Suppress("UNCHECKED_CAST")
        private inline fun <reified T> proxy(crossinline handler: (String, Array<out Any?>) -> Any?): T =
            Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
                handler(method.name, args ?: emptyArray())
            } as T
    }
}
