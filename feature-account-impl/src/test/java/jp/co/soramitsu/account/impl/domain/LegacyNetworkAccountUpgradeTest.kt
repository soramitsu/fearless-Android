package jp.co.soramitsu.account.impl.domain

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import jp.co.soramitsu.account.api.domain.UniversalWalletFundsProbe
import jp.co.soramitsu.account.api.domain.UniversalWalletPasswordNotice
import jp.co.soramitsu.account.api.domain.UniversalWalletSlot
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.AddAccountPayload
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.impl.data.repository.SubstrateOrEvmAccountRepository
import jp.co.soramitsu.account.impl.data.repository.WalletSecretMutationCoordinator
import jp.co.soramitsu.common.data.Keypair
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.secrets.v3.TonSecrets
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.common.utils.BitcoinKeyDerivation
import jp.co.soramitsu.common.utils.DEFAULT_DERIVATION_PATH
import jp.co.soramitsu.common.utils.IrohaKeyDerivation
import jp.co.soramitsu.common.utils.SolanaKeyDerivation
import jp.co.soramitsu.common.utils.TonKeyDerivation
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.coredb.dao.MetaAccountDao
import jp.co.soramitsu.coredb.model.ChainAccountLocal
import jp.co.soramitsu.coredb.model.MetaAccountLocal
import jp.co.soramitsu.fearless_utils.encrypt.junction.BIP32JunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.MnemonicCreator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.lang.reflect.Proxy

class LegacyNetworkAccountUpgradeTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `wallet imported after startup receives native accounts without process restart or repeat writes`() = runTest {
        val fixture = Fixture()
        val firstWallet = fixture.wallets.getValue(1)
        fixture.wallets.clear()
        fixture.walletFlow.value = emptyList()
        val catalog = MutableStateFlow(setOf(BTC, BTC_TEST, SOL, TAIRA))
        val observer = fixture.upgrade.observeWallets(backgroundScope, catalog)
        runCurrent()
        assertTrue(fixture.inserts.isEmpty())

        fixture.wallets[1] = firstWallet
        fixture.walletFlow.value = listOf(firstWallet)
        runCurrent()
        assertEquals(setOf(BTC, BTC_TEST, SOL, TAIRA), fixture.inserts.map { it.chainId }.toSet())

        fixture.walletFlow.value = fixture.wallets.values.toList()
        runCurrent()
        assertEquals(4, fixture.inserts.size)

        fixture.wallets[2] = firstWallet.copy(id = 2)
        fixture.walletFlow.value = fixture.wallets.values.toList()
        runCurrent()
        assertEquals(8, fixture.inserts.size)
        observer.cancel()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `catalog recovery retries a wallet whose earlier native expansion was unavailable`() = runTest {
        val fixture = Fixture()
        fixture.catalogUnavailable = true
        val catalog = MutableStateFlow(emptySet<String>())
        val observer = fixture.upgrade.observeWallets(backgroundScope, catalog)
        runCurrent()
        assertTrue(fixture.inserts.isEmpty())

        fixture.catalogUnavailable = false
        catalog.value = setOf(BTC, SOL, TAIRA)
        runCurrent()
        assertEquals(4, fixture.inserts.size)
        observer.cancel()
    }

    @Test
    fun `adds supported public accounts from authoritative root while retaining all old identities`() = runBlocking {
        val fixture = Fixture()
        val legacy = fixture.wallets.getValue(1)
        fixture.upgrade.upgrade()
        val upgraded = fixture.wallets.getValue(1)
        assertEquals(setOf("legacy-chain", BTC, BTC_TEST, SOL, TAIRA), upgraded.chainAccounts.keys)
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
        // Only the Ethereum path is consulted for a password, since the Substrate path has none.
        assertEquals(listOf("getEthereumSecrets"), fixture.fallbackReads)
        assertEquals(4, fixture.inserts.size)
        fixture.upgrade.upgrade()
        assertEquals(4, fixture.inserts.size)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `initial enrollment new imports and catalog arrivals run without restart or metadata loops`() = runTest {
        val fixture = Fixture()
        val original = fixture.wallets.getValue(1)
        fixture.wallets.clear()
        fixture.catalogIds = emptySet()
        fixture.walletFlow.value = emptyList()
        val chains = MutableStateFlow(emptySet<String>())
        val observer = fixture.upgrade.observeWallets(backgroundScope, chains)
        runCurrent()
        assertEquals(1, fixture.enumerations)
        fixture.wallets[1] = original
        fixture.walletFlow.value = fixture.wallets.values.toList()
        runCurrent()
        assertTrue(fixture.inserts.isEmpty())
        fixture.catalogIds = setOf(BTC_TEST)
        chains.emit(fixture.catalogIds)
        runCurrent()
        assertEquals(listOf(BTC_TEST), fixture.inserts.map { it.chainId })
        fixture.wallets[2] = original.copy(id = 2, chainAccounts = emptyMap())
        fixture.walletFlow.value = fixture.wallets.values.toList()
        runCurrent()
        assertEquals(setOf(BTC_TEST), fixture.wallets.getValue(2).chainAccounts.keys)
        val runs = fixture.enumerations
        fixture.walletFlow.value = fixture.wallets.values.reversed()
        chains.emit(fixture.catalogIds)
        runCurrent()
        assertEquals(runs, fixture.enumerations)
        observer.cancel()
    }

    @Test
    fun `password import keeps funded or unreadable old accounts and protects confirmed empty ones`() = runBlocking {
        val fixture = Fixture()
        fixture.substratePath = "//polkadot///$PASSWORD"
        fixture.funds = mapOf(
            UniversalWalletSlot.BITCOIN_MAINNET to UniversalWalletFundsProbe.Funds.FUNDED,
            UniversalWalletSlot.BITCOIN_TESTNET to UniversalWalletFundsProbe.Funds.UNKNOWN,
            UniversalWalletSlot.SOLANA to UniversalWalletFundsProbe.Funds.EMPTY,
            UniversalWalletSlot.TAIRA to UniversalWalletFundsProbe.Funds.EMPTY
        )
        fixture.upgrade.upgrade()

        val accounts = fixture.wallets.getValue(1).chainAccounts
        assertArrayEquals(UniversalWalletSlot.BITCOIN_MAINNET.publicKey(MNEMONIC, ""), accounts.getValue(BTC).publicKey)
        assertArrayEquals(UniversalWalletSlot.BITCOIN_TESTNET.publicKey(MNEMONIC, ""), accounts.getValue(BTC_TEST).publicKey)
        assertArrayEquals(UniversalWalletSlot.SOLANA.publicKey(MNEMONIC, PASSWORD), accounts.getValue(SOL).publicKey)
        assertArrayEquals(UniversalWalletSlot.TAIRA.publicKey(MNEMONIC, PASSWORD), accounts.getValue(TAIRA).publicKey)
        // The probe saw the accounts the words alone open, never the password ones.
        for (slot in UniversalWalletSlot.chainRowSlots) {
            assertArrayEquals(slot.publicKey(MNEMONIC, ""), fixture.probed.getValue(slot))
        }
        // Each slot is decided once: later runs never replace an existing account.
        fixture.funds = UniversalWalletSlot.entries.associateWith { UniversalWalletFundsProbe.Funds.EMPTY }
        fixture.upgrade.upgrade()
        assertEquals(4, fixture.inserts.size)
        assertArrayEquals(UniversalWalletSlot.BITCOIN_MAINNET.publicKey(MNEMONIC, ""), fixture.wallets.getValue(1).chainAccounts.getValue(BTC).publicKey)
    }

    @Test
    fun `password from the shared vectors derives their addresses for confirmed empty slots`() = runBlocking {
        val fixture = Fixture()
        fixture.substratePath = "///$PASSWORD"
        fixture.funds = UniversalWalletSlot.entries.associateWith { UniversalWalletFundsProbe.Funds.EMPTY }
        fixture.upgrade.upgrade()

        val accounts = fixture.wallets.getValue(1).chainAccounts
        val expected = passphraseVector().getAsJsonObject("expected")
        assertEquals(
            expected.getAsJsonObject("bitcoin").getAsJsonObject("mainnet")["firstReceiveAddress"].asString,
            UniversalWalletSlot.BITCOIN_MAINNET.address(accounts.getValue(BTC).publicKey)
        )
        assertEquals(
            expected.getAsJsonObject("bitcoin").getAsJsonObject("testnet")["firstReceiveAddress"].asString,
            UniversalWalletSlot.BITCOIN_TESTNET.address(accounts.getValue(BTC_TEST).publicKey)
        )
        assertEquals(expected.getAsJsonObject("solana")["address"].asString, UniversalWalletSlot.SOLANA.address(accounts.getValue(SOL).publicKey))
        assertEquals(
            expected.getAsJsonObject("iroha").getAsJsonObject("taira")["i105"].asString,
            UniversalWalletSlot.TAIRA.address(accounts.getValue(TAIRA).publicKey)
        )
    }

    @Test
    fun `wallet created from fresh words takes the password without probing`() = runBlocking {
        val fixture = Fixture()
        fixture.substratePath = "//polkadot///$PASSWORD"
        fixture.probeFails = true
        val substratePublicKey = requireNotNull(fixture.wallets.getValue(1).substratePublicKey)
        fixture.freshRoots.begin(substratePublicKey)
        fixture.freshRoots.bind(substratePublicKey, 1)
        fixture.upgrade.upgrade()

        val accounts = fixture.wallets.getValue(1).chainAccounts
        for ((slot, chainId) in listOf(
            UniversalWalletSlot.BITCOIN_MAINNET to BTC, UniversalWalletSlot.BITCOIN_TESTNET to BTC_TEST,
            UniversalWalletSlot.SOLANA to SOL, UniversalWalletSlot.TAIRA to TAIRA
        )) {
            assertArrayEquals(slot.publicKey(MNEMONIC, PASSWORD), accounts.getValue(chainId).publicKey)
        }
        assertTrue(fixture.probed.isEmpty())
        // Every slot exists now, so a later import of the same words is probed again.
        assertFalse(fixture.freshRoots.isCreated(1, substratePublicKey))
    }

    @Test
    fun `words created fresh but deleted mid-enrolment are probed when imported again`() = runBlocking {
        val fixture = Fixture()
        fixture.substratePath = "//polkadot///$PASSWORD"
        fixture.funds = UniversalWalletSlot.entries.associateWith { UniversalWalletFundsProbe.Funds.EMPTY }
        val created = mock<WalletSecretMutationCoordinator>()
        whenever(created.create(any(), any(), any(), anyOrNull())).thenReturn(1L, 2L)
        val creation = SubstrateOrEvmAccountRepository(mock(), created, fixture.freshRoots)
        fun payload(fresh: Boolean) = AddAccountPayload.SubstrateOrEvm(
            "Wallet", MNEMONIC, CryptoType.ECDSA, "//polkadot///$PASSWORD", BIP32JunctionDecoder.DEFAULT_DERIVATION_PATH,
            googleBackupAddress = null, isBackedUp = true, freshlyGenerated = fresh
        )

        // Created from fresh words; enrolment stops after one network when the app is closed.
        assertEquals(1L, creation.create(payload(fresh = true)))
        val prototype = argumentCaptor<MetaAccountLocal>()
        verify(created).create(prototype.capture(), any(), any(), anyOrNull())
        val template = fixture.wallets.getValue(1).copy(substratePublicKey = prototype.firstValue.substratePublicKey, chainAccounts = emptyMap())
        fixture.wallets[1] = template
        fixture.catalogIds = setOf(BTC)
        fixture.upgrade.upgrade()
        assertTrue(fixture.probed.isEmpty())

        // Deleted, then the same words imported again: they may have been used anywhere since.
        fixture.wallets.remove(1)
        assertEquals(2L, creation.create(payload(fresh = false)))
        fixture.wallets[2] = template.copy(id = 2)
        fixture.catalogIds = setOf(BTC, BTC_TEST, SOL, TAIRA)
        fixture.upgrade.upgrade()

        assertEquals(UniversalWalletSlot.chainRowSlots.toSet(), fixture.probed.keys)
    }

    @Test
    fun `password import watches each old account it gave up and the notice re-checks it`() = runBlocking {
        val fixture = Fixture()
        fixture.substratePath = "//polkadot///$PASSWORD"
        fixture.funds = mapOf(
            UniversalWalletSlot.BITCOIN_MAINNET to UniversalWalletFundsProbe.Funds.FUNDED,
            UniversalWalletSlot.SOLANA to UniversalWalletFundsProbe.Funds.EMPTY,
            UniversalWalletSlot.TAIRA to UniversalWalletFundsProbe.Funds.EMPTY
        )
        val watchList = UniversalWalletWatchList(fixture.preferences)
        val refreshed = mutableListOf<Long>()
        val notice = mock<UniversalWalletPasswordNotice>()
        whenever(notice.refresh(any())).thenAnswer { refreshed += it.getArgument<Long>(0) }
        LegacyNetworkAccountUpgrade(
            fixture.repository, fixture.dao, fixture.coordinator, fixture.probe, fixture.freshRoots, watchList, notice
        ) { fixture.catalogIds }.upgrade()

        val watched = watchList.entries(1).associateBy { it.slot }
        assertEquals(setOf(UniversalWalletSlot.SOLANA, UniversalWalletSlot.TAIRA), watched.keys)
        for ((slot, chainId) in listOf(UniversalWalletSlot.SOLANA to SOL, UniversalWalletSlot.TAIRA to TAIRA)) {
            val entry = watched.getValue(slot)
            assertEquals(chainId, entry.chainId)
            assertArrayEquals(slot.publicKey(MNEMONIC, ""), entry.oldKey)
            assertArrayEquals(slot.publicKey(MNEMONIC, PASSWORD), entry.newKey)
            assertArrayEquals(entry.newKey, fixture.wallets.getValue(1).chainAccounts.getValue(chainId).publicKey)
        }
        assertEquals(listOf(1L), refreshed)
    }

    @Test
    fun `older password wallet keeps every address it showed and nothing is probed`() = runBlocking {
        val fixture = Fixture()
        fixture.substratePath = "//polkadot///$PASSWORD"
        fixture.probeFails = true
        val legacyTon = TonKeyDerivation.deriveAccount(MNEMONIC).publicKey
        // What 4.2.x and 4.3.0 stored for a password wallet: every slot without the password.
        val older = fixture.wallets.getValue(1).let { wallet ->
            wallet.copy(tonPublicKey = legacyTon, chainAccounts = wallet.chainAccounts + listOf(
                BTC to UniversalWalletSlot.BITCOIN_MAINNET, BTC_TEST to UniversalWalletSlot.BITCOIN_TESTNET,
                SOL to UniversalWalletSlot.SOLANA, TAIRA to UniversalWalletSlot.TAIRA
            ).associate { (chainId, slot) ->
                val key = slot.publicKey(MNEMONIC, "")
                chainId to MetaAccount.ChainAccount(1, null, key, key.clone(), CryptoType.ED25519, "Legacy wallet")
            })
        }
        fixture.wallets[1] = older
        fixture.upgrade.upgrade()

        assertSame(older, fixture.wallets.getValue(1))
        assertTrue(fixture.inserts.isEmpty())
        assertTrue(fixture.probed.isEmpty())
        verifyNoInteractions(fixture.coordinator)
    }

    @Test
    fun `missing TON root of a password wallet follows the probe of its old address`() = runBlocking {
        for ((funds, passphrase) in listOf(
            UniversalWalletFundsProbe.Funds.EMPTY to PASSWORD,
            UniversalWalletFundsProbe.Funds.FUNDED to "",
            UniversalWalletFundsProbe.Funds.UNKNOWN to ""
        )) {
            val fixture = Fixture()
            fixture.substratePath = "//polkadot///$PASSWORD"
            fixture.wallets[1] = fixture.wallets.getValue(1).copy(tonPublicKey = null)
            fixture.funds = UniversalWalletSlot.entries.associateWith { funds }
            whenever(fixture.coordinator.addTon(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any())).thenReturn(1L)
            fixture.upgrade.upgrade()

            val encoded = argumentCaptor<String>()
            val after = argumentCaptor<MetaAccountLocal>()
            verify(fixture.coordinator).addTon(org.mockito.kotlin.any(), after.capture(), encoded.capture())
            val expected = TonKeyDerivation.deriveAccount(MNEMONIC, passphrase)
            assertArrayEquals(expected.publicKey, after.firstValue.tonPublicKey)
            assertArrayEquals(expected.privateKey, TonSecrets.read(encoded.firstValue)[TonSecrets.PrivateKey])
            assertEquals(MNEMONIC, TonSecrets.read(encoded.firstValue)[TonSecrets.Seed].decodeToString())
            assertArrayEquals(TonKeyDerivation.deriveAccount(MNEMONIC).publicKey, fixture.probed.getValue(UniversalWalletSlot.TON))
        }
    }

    @Test
    fun `wallet without a password is never probed`() = runBlocking {
        val fixture = Fixture()
        fixture.probeFails = true
        fixture.wallets[1] = fixture.wallets.getValue(1).copy(tonPublicKey = null)
        fixture.upgrade.upgrade()
        assertEquals(4, fixture.inserts.size)
        assertTrue(fixture.probed.isEmpty())
        assertArrayEquals(SolanaKeyDerivation.deriveAccount(MNEMONIC).publicKey, fixture.wallets.getValue(1).chainAccounts.getValue(SOL).publicKey)
    }

    @Test
    fun `missing TON is derived from validated entropy and committed without changing existing roots`() = runBlocking {
        val fixture = Fixture()
        val original = fixture.wallets.getValue(1).copy(tonPublicKey = null)
        fixture.wallets[1] = original
        fixture.upgrade.upgrade()
        val before = argumentCaptor<MetaAccountLocal>()
        val after = argumentCaptor<MetaAccountLocal>()
        val encoded = argumentCaptor<String>()
        verify(fixture.coordinator).addTon(before.capture(), after.capture(), encoded.capture())
        assertArrayEquals(original.substratePublicKey, after.firstValue.substratePublicKey)
        assertArrayEquals(original.ethereumPublicKey, after.firstValue.ethereumPublicKey)
        assertEquals(before.firstValue.name, after.firstValue.name)
        assertEquals(before.firstValue.isBackedUp, after.firstValue.isBackedUp)
        val secrets = TonSecrets.read(encoded.firstValue)
        val expected = TonKeyDerivation.deriveAccount(MNEMONIC)
        assertArrayEquals(expected.publicKey, after.firstValue.tonPublicKey)
        assertArrayEquals(expected.privateKey, secrets[TonSecrets.PrivateKey])
        assertEquals(MNEMONIC, secrets[TonSecrets.Seed].decodeToString())
        assertArrayEquals(
            BitcoinKeyDerivation.deriveAccount(MNEMONIC, network = BitcoinKeyDerivation.Network.Testnet).publicKey,
            fixture.wallets.getValue(1).chainAccounts.getValue(BTC_TEST).publicKey
        )
    }

    @Test
    fun `native TON and dedicated TON accounts never receive a replacement root`() = runBlocking {
        val native = Fixture()
        native.upgrade.upgrade()
        verifyNoInteractions(native.coordinator)
        for (alias in listOf("ton-mainnet", "ton:mainnet", "TON:MAINNET", "-239", "-3")) {
            val fixture = Fixture()
            val wallet = fixture.wallets.getValue(1)
            fixture.wallets[1] = wallet.copy(tonPublicKey = null, chainAccounts = wallet.chainAccounts + (alias to wallet.chainAccounts.getValue("legacy-chain")))
            fixture.upgrade.upgrade()
            verifyNoInteractions(fixture.coordinator)
        }
        val seedOnly = Fixture()
        seedOnly.wallets[1] = seedOnly.wallets.getValue(1).copy(tonPublicKey = null)
        seedOnly.withEntropy = false
        seedOnly.upgrade.upgrade()
        verifyNoInteractions(seedOnly.coordinator)
    }

    @Test
    fun `failed optional insertion retries only missing network without blocking other additions`() = runBlocking {
        val fixture = Fixture()
        fixture.failedChain = BTC
        fixture.upgrade.upgrade()
        assertEquals(setOf("legacy-chain", BTC_TEST, SOL, TAIRA), fixture.wallets.getValue(1).chainAccounts.keys)
        fixture.failedChain = null
        fixture.upgrade.upgrade()
        assertEquals(setOf("legacy-chain", BTC, BTC_TEST, SOL, TAIRA), fixture.wallets.getValue(1).chainAccounts.keys)
        assertEquals(4, fixture.inserts.size)
    }

    @Test
    fun `preserves independent existing network key under its alternate chain id`() = runBlocking {
        for (network in listOf(UniversalWalletRegistry.bitcoinMainnet, UniversalWalletRegistry.bitcoinTestnet)) {
            val aliases = listOf(
                network.id, network.chainId, network.id.uppercase(), network.chainId.uppercase(),
                network.chainId.replace("bitcoin", "BitCoin")
            )
            for (alias in aliases) {
                val fixture = Fixture()
                val independent = MetaAccount.ChainAccount(
                    1, null, ByteArray(33) { 8 }, ByteArray(32) { 9 }, CryptoType.ECDSA, "Imported BTC"
                )
                val original = fixture.wallets.getValue(1)
                fixture.wallets[1] = original.copy(chainAccounts = original.chainAccounts + (alias to independent))
                fixture.upgrade.upgrade()
                assertSame(independent, fixture.wallets.getValue(1).chainAccounts[alias])
                assertTrue(fixture.inserts.none { it.chainId == network.id })
                assertEquals(3, fixture.inserts.size)
            }
        }
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
        assertEquals(setOf(BTC, BTC_TEST, SOL, TAIRA), fixture.wallets.getValue(2).chainAccounts.keys)
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
        assertEquals(4, fixture.inserts.size)
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
        val walletFlow = MutableStateFlow(wallets.values.toList())
        val fallbackReads = mutableListOf<String>()
        var failedChain: String? = null
        var unreadableWallet: Long? = null
        var withEntropy = true
        var catalogUnavailable = false
        var deleteAfterEnumeration = false
        var cancel = false
        var enumerations = 0
        var catalogIds = setOf(BTC, BTC_TEST, SOL, TAIRA, UniversalWalletRegistry.nexus.id)
        var substratePath: String? = null
        var funds: Map<UniversalWalletSlot, UniversalWalletFundsProbe.Funds> = emptyMap()
        var probeFails = false
        val probed = linkedMapOf<UniversalWalletSlot, ByteArray>()
        val preferenceValues = linkedMapOf<String, String>()
        val preferences = proxy<Preferences> { method, args ->
            when (method) {
                "getString" -> preferenceValues[args[0] as String] ?: args.getOrNull(1)
                "putString" -> {
                    val value = args[1] as String?
                    if (value == null) preferenceValues.remove(args[0] as String) else preferenceValues[args[0] as String] = value
                    Unit
                }
                else -> error("Unexpected preference call: $method")
            }
        }
        val freshRoots = UniversalWalletFreshRoots(preferences)
        val probe = UniversalWalletFundsProbe { slot, mnemonic, _, publicKey ->
            check(!probeFails) { "This wallet must not be probed" }
            check(mnemonic == MNEMONIC)
            probed[slot] = publicKey
            funds[slot] ?: UniversalWalletFundsProbe.Funds.UNKNOWN
        }
        val repository = proxy<AccountRepository> { method, args ->
            when (method) {
                "allMetaAccountsFlow" -> walletFlow
                "allMetaAccounts" -> wallets.values.toList().also {
                    enumerations++
                    if (deleteAfterEnumeration) wallets.clear()
                }
                "getMetaAccount" -> wallets.getValue(args[0] as Long)
                "getSubstrateSecrets" -> {
                    if (cancel) throw CancellationException("synthetic cancellation")
                    if (args[0] == unreadableWallet) error("synthetic unavailable wallet")
                    if (withEntropy) SubstrateSecrets(
                        substrateKeyPair = Keypair(ByteArray(32) { 1 }, ByteArray(32) { 2 }),
                        entropy = MnemonicCreator.fromWords(MNEMONIC).entropy,
                        substrateDerivationPath = substratePath
                    ) else null
                }
                "getEthereumSecrets", "getTonSecrets" -> { fallbackReads += method; null }
                else -> error("Unexpected repository mutation: $method")
            }
        }
        val dao = proxy<MetaAccountDao> { method, args ->
            if (method == "getMetaAccount") {
                val wallet = wallets.getValue(args[0] as Long)
                return@proxy MetaAccountLocal(
                    wallet.substratePublicKey, wallet.substrateCryptoType, wallet.substrateAccountId,
                    wallet.ethereumPublicKey, wallet.ethereumAddress, wallet.tonPublicKey,
                    wallet.name, wallet.isSelected, 0, wallet.isBackedUp, wallet.googleBackupAddress, wallet.initialized
                ).apply { id = wallet.id }
            }
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
        val coordinator = mock<WalletSecretMutationCoordinator>()
        val upgrade = LegacyNetworkAccountUpgrade(repository, dao, coordinator, probe, freshRoots) {
            if (catalogUnavailable) error("synthetic catalog failure")
            catalogIds
        }
    }

    companion object {
        private const val MNEMONIC = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        private const val PASSWORD = "TREZOR"
        private val BTC = UniversalWalletRegistry.bitcoinMainnet.id
        private val BTC_TEST = UniversalWalletRegistry.bitcoinTestnet.id
        private val SOL = UniversalWalletRegistry.solanaMainnet.id
        private val TAIRA = UniversalWalletRegistry.taira.id

        /** The shared import12-trezor vector: the same words and password as [MNEMONIC] and [PASSWORD]. */
        private fun passphraseVector(): JsonObject {
            val stream = requireNotNull(LegacyNetworkAccountUpgradeTest::class.java.classLoader)
                .getResourceAsStream("universal-wallet-v2-passphrase-vectors.json")
            val fixture = stream.use { JsonParser.parseReader(java.io.InputStreamReader(it, Charsets.UTF_8)).asJsonObject }
            return fixture.getAsJsonArray("vectors").map { it.asJsonObject }.single { it["id"].asString == "import12-trezor" }.also {
                check(it["mnemonic"].asString == MNEMONIC && it["passphrase"].asString == PASSWORD)
            }
        }

        @Suppress("UNCHECKED_CAST")
        private inline fun <reified T> proxy(crossinline handler: (String, Array<out Any?>) -> Any?): T =
            Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
                handler(method.name, args ?: emptyArray())
            } as T
    }
}
