package jp.co.soramitsu.account.impl.data.repository

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import jp.co.soramitsu.account.api.domain.UniversalWalletFundsProbe
import jp.co.soramitsu.account.api.domain.UniversalWalletFundsProbe.Funds
import jp.co.soramitsu.account.api.domain.UniversalWalletPasswordNotice.Arrival
import jp.co.soramitsu.account.api.domain.UniversalWalletPasswordNotice.Outcome
import jp.co.soramitsu.account.api.domain.UniversalWalletSlot
import jp.co.soramitsu.account.api.domain.model.AddAccountPayload
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.universalWalletRoot
import jp.co.soramitsu.account.impl.data.repository.datasource.AccountDataSource
import jp.co.soramitsu.account.impl.domain.AccountInteractorImpl
import jp.co.soramitsu.account.impl.domain.LegacyNetworkAccountUpgrade
import jp.co.soramitsu.account.impl.domain.UniversalWalletPasswordNoticeService
import jp.co.soramitsu.account.impl.domain.UniversalWalletWatchList
import jp.co.soramitsu.account.impl.presentation.exporting.mnemonic.ExportMnemonicFragment
import jp.co.soramitsu.account.impl.presentation.exporting.mnemonic.ExportMnemonicPayload
import jp.co.soramitsu.account.impl.presentation.exporting.mnemonic.ExportMnemonicViewModel
import jp.co.soramitsu.common.data.Keypair
import jp.co.soramitsu.common.data.secrets.v2.SecretStoreV2
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecretStore
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecrets
import jp.co.soramitsu.common.data.secrets.v3.RecoveryAssessment
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecretStore
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.secrets.v3.TonSecretStore
import jp.co.soramitsu.common.data.secrets.v3.TonSecrets
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretAccessGuard
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretQuarantine
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.common.model.WalletEcosystem
import jp.co.soramitsu.common.resources.LanguagesHolder
import jp.co.soramitsu.common.utils.DEFAULT_DERIVATION_PATH
import jp.co.soramitsu.common.utils.TonKeyDerivation
import jp.co.soramitsu.common.utils.deriveSeed32
import jp.co.soramitsu.common.utils.ethereumAddressFromPublicKey
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.coredb.dao.MetaAccountDao
import jp.co.soramitsu.coredb.dao.NomisScoresDao
import jp.co.soramitsu.coredb.model.MetaAccountLocal
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.json.JsonSeedDecoder
import jp.co.soramitsu.fearless_utils.encrypt.json.JsonSeedEncoder
import jp.co.soramitsu.fearless_utils.encrypt.junction.BIP32JunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.keypair.ethereum.EthereumKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.MnemonicCreator
import jp.co.soramitsu.fearless_utils.encrypt.seed.ethereum.EthereumSeedFactory
import jp.co.soramitsu.fearless_utils.encrypt.seed.substrate.SubstrateSeedFactory
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.tonChainId
import jp.co.soramitsu.testshared.HashMapEncryptedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.io.InputStreamReader
import java.lang.reflect.Proxy

/**
 * A password wallet as 4.2.x and 4.3.0 stored it (Substrate path with `///password`, every
 * universal account derived without it) through the real account repository and secret stores:
 * no address it ever showed disappears, each still signs, and Switch replaces only accounts a
 * probe confirms empty, with the addresses of the shared passphrase vectors.
 */
class UniversalWalletPasswordRestoreTest {

    @get:Rule
    val instantTaskRule = InstantTaskExecutorRule()

    private val preferences = HashMapEncryptedPreferences()
    private val accountDataSource = mock<AccountDataSource>()
    private val dao = mock<MetaAccountDao>()
    private val coordinator = mock<WalletSecretMutationCoordinator>()

    @Test
    fun olderPasswordWalletKeepsEveryAddressItShowedThroughTheUpgrade() = runTest {
        val wallet = storeOlderPasswordWallet()
        var probed = false
        LegacyNetworkAccountUpgrade(repository(), dao, coordinator, UniversalWalletFundsProbe { _, _, _, _ ->
            probed = true
            Funds.EMPTY
        }) { CATALOG }.upgrade()

        verify(dao, never()).insertDerivedChainAccountsIfAbsent(any())
        verifyNoInteractions(coordinator)
        assertTrue(!probed)

        // Every stored account is still the no-password one, and its signer finds that passphrase.
        val root = requireNotNull(repository().universalWalletRoot(META_ID))
        assertEquals(PASSWORD, root.passphrase)
        for (slot in UniversalWalletSlot.chainRowSlots) {
            val stored = wallet.chainAccounts.entries.single { slot.matches(it.key) }.value.publicKey
            assertEquals("", root.passphraseCandidates.single { slot.publicKey(root.mnemonic, it).contentEquals(stored) })
        }
        // The TON root still reads, verifies and signs exactly as before.
        assertNotNull(repository().getTonSecrets(META_ID))
        assertEquals(RecoveryAssessment.Verified, repository().recoveryAssessment(META_ID, WalletEcosystem.Ton))
        assertArrayEquals(legacyTon.publicKey, TonSecretStore(preferences).get(META_ID, legacyTon.publicKey)?.get(TonSecrets.PublicKey))
    }

    @Test
    fun noticeListsEachOldAddressBesideTheSharedVectorsPasswordAddress() = runTest {
        storeOlderPasswordWallet()
        val rows = notice(UniversalWalletFundsProbe { _, _, _, _ -> Funds.UNKNOWN }).rows(META_ID)

        val legacy = vector("universal-wallet-v2-vectors.json", "import12").getAsJsonObject("expected")
        val protected = vector("universal-wallet-v2-passphrase-vectors.json", "import12-trezor").getAsJsonObject("expected")
        assertEquals(UniversalWalletSlot.entries.toSet(), rows.map { it.slot }.toSet())
        for (row in rows) {
            assertEquals(row.slot.name, expectedAddress(legacy, row.slot), row.oldAddress)
            assertEquals(row.slot.name, expectedAddress(protected, row.slot), row.newAddress)
            assertNull(row.outcome)
        }
    }

    @Test
    fun switchReplacesOnlyAccountsConfirmedEmptyAndKeepsTheRest() = runTest {
        storeOlderPasswordWallet()
        whenever(dao.replaceDerivedChainAccount(any(), any(), any(), any(), any())).thenReturn(1)
        whenever(coordinator.replaceUniversalTon(any(), any(), any())).thenReturn(META_ID)
        val funds = mapOf(
            UniversalWalletSlot.BITCOIN_MAINNET to Funds.FUNDED,
            UniversalWalletSlot.BITCOIN_TESTNET to Funds.UNKNOWN,
            UniversalWalletSlot.SOLANA to Funds.EMPTY,
            UniversalWalletSlot.TAIRA to Funds.EMPTY,
            UniversalWalletSlot.TON to Funds.EMPTY
        )
        val probed = linkedMapOf<UniversalWalletSlot, ByteArray>()
        val rows = notice(UniversalWalletFundsProbe { slot, _, _, publicKey ->
            probed[slot] = publicKey
            funds.getValue(slot)
        }).switch(META_ID).associateBy { it.slot }

        assertEquals(Outcome.STILL_FUNDED, rows.getValue(UniversalWalletSlot.BITCOIN_MAINNET).outcome)
        assertEquals(Outcome.UNKNOWN, rows.getValue(UniversalWalletSlot.BITCOIN_TESTNET).outcome)
        assertEquals(Outcome.SWITCHED, rows.getValue(UniversalWalletSlot.SOLANA).outcome)
        assertEquals(Outcome.SWITCHED, rows.getValue(UniversalWalletSlot.TAIRA).outcome)
        assertEquals(Outcome.SWITCHED, rows.getValue(UniversalWalletSlot.TON).outcome)
        // Only the no-password accounts were probed.
        for ((slot, key) in probed) assertArrayEquals(slot.name, slot.publicKey(MNEMONIC, ""), key)

        for (slot in listOf(UniversalWalletSlot.SOLANA, UniversalWalletSlot.TAIRA)) {
            verify(dao).replaceDerivedChainAccount(
                eq(META_ID), eq(chainIdOf(slot)), eq(slot.publicKey(MNEMONIC, "")), eq(slot.publicKey(MNEMONIC, PASSWORD)), any()
            )
        }
        for (slot in listOf(UniversalWalletSlot.BITCOIN_MAINNET, UniversalWalletSlot.BITCOIN_TESTNET)) {
            verify(dao, never()).replaceDerivedChainAccount(eq(META_ID), eq(chainIdOf(slot)), any(), any(), any())
        }
        val after = argumentCaptor<MetaAccountLocal>()
        val encoded = argumentCaptor<String>()
        verify(coordinator).replaceUniversalTon(any(), after.capture(), encoded.capture())
        assertArrayEquals(protectedTon.publicKey, after.firstValue.tonPublicKey)
        assertArrayEquals(protectedTon.privateKey, TonSecrets.read(encoded.firstValue)[TonSecrets.PrivateKey])
        assertEquals(MNEMONIC, TonSecrets.read(encoded.firstValue)[TonSecrets.Seed].decodeToString())
    }

    @Test
    fun switchedTonRootReadsAndVerifiesWithTheWalletPassword() = runTest {
        val wallet = storeOlderPasswordWallet()
        // What the coordinator commits on Switch.
        TonSecretStore(preferences).put(
            META_ID, TonSecrets(MNEMONIC.encodeToByteArray(), Keypair(protectedTon.publicKey, protectedTon.privateKey))
        )
        whenever(accountDataSource.getMetaAccount(META_ID)).thenReturn(wallet.copy(tonPublicKey = protectedTon.publicKey))

        val secrets = repository().getTonSecrets(META_ID)
        assertNotNull(secrets)
        assertEquals(MNEMONIC, secrets!![TonSecrets.Seed].decodeToString())
        assertEquals(RecoveryAssessment.Verified, repository().recoveryAssessment(META_ID, WalletEcosystem.Ton))
        assertTrue(notice(UniversalWalletFundsProbe { _, _, _, _ -> Funds.EMPTY }).rows(META_ID).none { it.slot == UniversalWalletSlot.TON })
    }

    @Test
    fun connectedTonAppsKeepTheOldTonAddress() = runTest {
        storeOlderPasswordWallet()
        whenever(dao.replaceDerivedChainAccount(any(), any(), any(), any(), any())).thenReturn(1)
        whenever(coordinator.replaceUniversalTon(any(), any(), any())).thenThrow(
            WalletSecretMutationCoordinatorException(WalletMutationFailureReason.CONNECTED_APPS, "connected")
        )
        val rows = notice(UniversalWalletFundsProbe { _, _, _, _ -> Funds.EMPTY }).switch(META_ID).associateBy { it.slot }
        assertEquals(Outcome.IN_USE, rows.getValue(UniversalWalletSlot.TON).outcome)
        assertEquals(Outcome.SWITCHED, rows.getValue(UniversalWalletSlot.SOLANA).outcome)
    }

    @Test
    fun walletWithoutPasswordHasNoNotice() = runTest {
        storeOlderPasswordWallet(substratePath = null)
        assertTrue(notice(UniversalWalletFundsProbe { _, _, _, _ -> Funds.EMPTY }).rows(META_ID).isEmpty())
        assertTrue(notice(UniversalWalletFundsProbe { _, _, _, _ -> Funds.EMPTY }).switch(META_ID).isEmpty())
        verifyNoInteractions(coordinator)
    }

    @Test
    fun tonExportOfAWalletWhosePasswordIsInItsEthereumPathRestoresTheSameTonAddress() = runTest {
        // An empty Substrate path; the password is in the Ethereum path over the same words.
        val wallet = storeWallet(substratePath = null, ethereumPath = "$DEFAULT_ETHEREUM_PATH///$PASSWORD", tonPassphrase = PASSWORD)
        val exported = exportTon()
        assertEquals("$DEFAULT_ETHEREUM_PATH///$PASSWORD", exported.ethereumPath)
        assertArrayEquals(wallet.tonPublicKey, restore(exported))
    }

    @Test
    fun tonExportOfAWalletWhosePasswordIsInItsSubstratePathRestoresTheSameTonAddress() = runTest {
        val wallet = storeWallet(substratePath = "///$PASSWORD", tonPassphrase = PASSWORD)
        val exported = exportTon()
        assertEquals("///$PASSWORD", exported.substratePath)
        assertArrayEquals(wallet.tonPublicKey, restore(exported))
    }

    @Test
    fun staleTonReaderAfterTheSwapCommittedReadsTheNewKeyWithoutQuarantine() = runTest {
        val wallet = storeOlderPasswordWallet()
        var reads = 0
        whenever(accountDataSource.getMetaAccount(META_ID)).thenAnswer {
            if (reads++ == 0) {
                // Switch stages the new key, Room names it and the journal clears right after this read.
                TonSecretStore(preferences).put(
                    META_ID, TonSecrets(MNEMONIC.encodeToByteArray(), Keypair(protectedTon.publicKey, protectedTon.privateKey))
                )
                wallet
            } else {
                wallet.copy(tonPublicKey = protectedTon.publicKey)
            }
        }

        val secrets = repository().getTonSecrets(META_ID)

        assertArrayEquals(protectedTon.publicKey, secrets?.get(TonSecrets.PublicKey))
        assertTrue(!preferences.hasKey(WalletSecretQuarantine.keyFor("$META_ID:TON_SECRETS")))
        assertEquals(RecoveryAssessment.Verified, repository().recoveryAssessment(META_ID, WalletEcosystem.Ton))
    }

    @Test
    fun depositReachingAnAlreadyReadOldAddressBeforeTheSwitchCommitsMovesTheSlotBack() = runTest {
        storeOlderPasswordWallet()
        val ledger = Ledger()
        val notice = trackedNotice(ledger)
        // Each old account reads empty; a deposit lands after that read, before the swap commits.
        onReplace = { slot ->
            when (slot) {
                UniversalWalletSlot.SOLANA -> ledger.fund(slot, legacy = true, balance = "2 SOL")
                UniversalWalletSlot.TON -> ledger.fund(slot, legacy = true, balance = "1 TON")
                else -> Unit
            }
        }
        val switched = notice.switch(META_ID).associateBy { it.slot }
        assertEquals(Outcome.SWITCHED, switched.getValue(UniversalWalletSlot.SOLANA).outcome)
        assertEquals(Outcome.SWITCHED, switched.getValue(UniversalWalletSlot.TON).outcome)
        onReplace = {}
        notice.dismiss(META_ID)

        notice.refresh(META_ID)

        // The new addresses are empty, so the funded old ones are back where the wallet shows them.
        assertArrayEquals(UniversalWalletSlot.SOLANA.publicKey(MNEMONIC, ""), current.chainAccounts.getValue(chainIdOf(UniversalWalletSlot.SOLANA)).publicKey)
        assertArrayEquals(legacyTon.publicKey, current.tonPublicKey)
        val after = argumentCaptor<MetaAccountLocal>()
        val encoded = argumentCaptor<String>()
        verify(coordinator, times(2)).replaceUniversalTon(any(), after.capture(), encoded.capture())
        assertArrayEquals(legacyTon.privateKey, TonSecrets.read(encoded.secondValue)[TonSecrets.PrivateKey])
        // The notice lists them again, says why, and shows once more.
        val rows = notice.rows(META_ID).associateBy { it.slot }
        for ((slot, balance) in listOf(UniversalWalletSlot.SOLANA to "2 SOL", UniversalWalletSlot.TON to "1 TON")) {
            val arrival = requireNotNull(rows.getValue(slot).arrival)
            assertEquals(Arrival.Kind.OLD_ADDRESS_USED_AGAIN, arrival.kind)
            assertEquals(balance, arrival.balance)
            assertTrue(rows.getValue(slot).switchable)
        }
        assertTrue(!notice.isDismissed(META_ID))
    }

    @Test
    fun depositReachingAnOldAddressAfterTheSwitchNeverHidesTheNewAddressFunds() = runTest {
        storeOlderPasswordWallet()
        val ledger = Ledger()
        val notice = trackedNotice(ledger)
        notice.switch(META_ID)
        val switchedSolana = current.chainAccounts.getValue(chainIdOf(UniversalWalletSlot.SOLANA)).publicKey
        // Later: the old Solana and Taira addresses receive funds; the new Solana one holds funds,
        // the new Taira one cannot be read.
        ledger.fund(UniversalWalletSlot.SOLANA, legacy = true, balance = "0.5 SOL")
        ledger.fund(UniversalWalletSlot.SOLANA, legacy = false, balance = "3 SOL")
        ledger.fund(UniversalWalletSlot.TAIRA, legacy = true, balance = null)
        ledger.unknown(UniversalWalletSlot.TAIRA, legacy = false)

        notice.refresh(META_ID)

        assertArrayEquals(switchedSolana, current.chainAccounts.getValue(chainIdOf(UniversalWalletSlot.SOLANA)).publicKey)
        assertArrayEquals(
            UniversalWalletSlot.TAIRA.publicKey(MNEMONIC, PASSWORD),
            current.chainAccounts.getValue(chainIdOf(UniversalWalletSlot.TAIRA)).publicKey
        )
        val rows = notice.rows(META_ID).filter { it.arrival != null }.associateBy { it.slot }
        assertEquals(setOf(UniversalWalletSlot.SOLANA, UniversalWalletSlot.TAIRA), rows.keys)
        for (row in rows.values) {
            assertEquals(Arrival.Kind.OLD_ADDRESS, row.arrival?.kind)
            assertEquals(row.slot.address(row.slot.publicKey(MNEMONIC, "")), row.oldAddress)
            assertTrue(!row.switchable)
        }
        assertEquals("0.5 SOL", rows.getValue(UniversalWalletSlot.SOLANA).arrival?.balance)

        // Once the old addresses are emptied the rows go.
        ledger.empty(UniversalWalletSlot.SOLANA, legacy = true)
        ledger.empty(UniversalWalletSlot.TAIRA, legacy = true)
        notice.refresh(META_ID)
        assertTrue(notice.rows(META_ID).none { it.arrival != null })
    }

    @Test
    fun deletingTheWalletForgetsItsWatchedOldAddresses() = runTest {
        storeOlderPasswordWallet()
        val notice = trackedNotice(Ledger())
        notice.switch(META_ID)
        assertTrue(watchList.entries(META_ID).isNotEmpty())

        repository().deleteAccount(META_ID)

        verify(coordinator).delete(META_ID)
        assertTrue(watchList.entries(META_ID).isEmpty())
    }

    private val legacyTon = TonKeyDerivation.deriveAccount(MNEMONIC)
    private val protectedTon = TonKeyDerivation.deriveAccount(MNEMONIC, PASSWORD)
    private val noticePreferences = memoryPreferences()
    private val watchList = UniversalWalletWatchList(noticePreferences)
    private lateinit var current: MetaAccount
    private var onReplace: (UniversalWalletSlot) -> Unit = {}

    private suspend fun storeOlderPasswordWallet(substratePath: String? = "///$PASSWORD"): MetaAccount =
        storeWallet(substratePath = substratePath)

    /** A wallet of [MNEMONIC]: legacy chain rows, and a TON root derived with [tonPassphrase]. */
    private suspend fun storeWallet(
        metaId: Long = META_ID,
        substratePath: String?,
        ethereumPath: String = DEFAULT_ETHEREUM_PATH,
        tonPassphrase: String = ""
    ): MetaAccount {
        val mnemonic = MnemonicCreator.fromWords(MNEMONIC)
        val password = substratePath?.let { jp.co.soramitsu.fearless_utils.encrypt.junction.SubstrateJunctionDecoder.decode(it).password }
        val substrateSeed = SubstrateSeedFactory.deriveSeed32(mnemonic.words, password).seed
        val substrate = SubstrateKeypairFactory.generate(EncryptionType.ECDSA, substrateSeed, emptyList())
        val decodedEthereumPath = BIP32JunctionDecoder.decode(ethereumPath)
        val ethereum = EthereumKeypairFactory.generate(
            seed = EthereumSeedFactory.deriveSeed32(mnemonic.words, decodedEthereumPath.password).seed,
            junctions = decodedEthereumPath.junctions
        )
        val ton = TonKeyDerivation.deriveAccount(MNEMONIC, tonPassphrase)
        val chainAccounts = UniversalWalletSlot.chainRowSlots.associate { slot ->
            val key = slot.publicKey(MNEMONIC, "")
            val bitcoin = slot == UniversalWalletSlot.BITCOIN_MAINNET || slot == UniversalWalletSlot.BITCOIN_TESTNET
            chainIdOf(slot) to MetaAccount.ChainAccount(
                metaId, null, key, key.clone(), if (bitcoin) CryptoType.ECDSA else CryptoType.ED25519, "Password wallet"
            )
        }
        val wallet = MetaAccount(
            id = metaId,
            chainAccounts = chainAccounts,
            favoriteChains = emptyMap(),
            substratePublicKey = substrate.publicKey,
            substrateCryptoType = CryptoType.ECDSA,
            substrateAccountId = substrate.publicKey.substrateAccountId(),
            ethereumAddress = ethereum.publicKey.ethereumAddressFromPublicKey(),
            ethereumPublicKey = ethereum.publicKey,
            tonPublicKey = ton.publicKey,
            isSelected = true,
            isBackedUp = true,
            googleBackupAddress = null,
            name = "Password wallet",
            initialized = true
        )
        current = wallet
        whenever(accountDataSource.getMetaAccount(metaId)).thenReturn(wallet)
        whenever(accountDataSource.allMetaAccounts()).thenReturn(listOf(wallet))
        whenever(accountDataSource.lightMetaAccountsFlow()).thenReturn(flowOf(listOf(wallet)))
        whenever(dao.getMetaAccount(metaId)).thenReturn(wallet.toLocal())
        SubstrateSecretStore(preferences).put(
            metaId,
            SubstrateSecrets(
                substrateKeyPair = substrate,
                entropy = mnemonic.entropy,
                seed = substrateSeed,
                substrateDerivationPath = substratePath
            )
        )
        EthereumSecretStore(preferences).put(
            metaId,
            EthereumSecrets(
                entropy = mnemonic.entropy,
                seed = ethereum.privateKey,
                ethereumKeypair = ethereum,
                ethereumDerivationPath = ethereumPath
            )
        )
        // 4.2.x LegacyNetworkAccountUpgrade.addMissingTonRoot stored the words without a passphrase.
        TonSecretStore(preferences).put(
            metaId, TonSecrets(MNEMONIC.encodeToByteArray(), Keypair(ton.publicKey, ton.privateKey))
        )
        return wallet
    }

    private fun MetaAccount.toLocal() = MetaAccountLocal(
        substratePublicKey, substrateCryptoType, substrateAccountId,
        ethereumPublicKey, ethereumAddress, tonPublicKey,
        name, isSelected, 0, isBackedUp, googleBackupAddress, initialized
    ).also { it.id = id }

    private class Exported(val words: String, val substratePath: String?, val ethereumPath: String?)

    /** What the TON account's mnemonic export shows: the words and, under Advanced, the paths. */
    private fun exportTon(): Exported {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val chain = mock<Chain>()
            whenever(chain.ecosystem).thenReturn(Ecosystem.Ton)
            whenever(chain.isEthereumBased).thenReturn(false)
            val chainRegistry = mock<ChainRegistry>()
            runBlocking { whenever(chainRegistry.getChain(tonChainId)).thenReturn(chain) }
            val interactor = AccountInteractorImpl(repository(), mock(), mock(), mock(), mock(), Dispatchers.Unconfined)
            val viewModel = ExportMnemonicViewModel(
                mock(), mock(), interactor, chainRegistry,
                SavedStateHandle(mapOf(ExportMnemonicFragment.PAYLOAD_KEY to ExportMnemonicPayload(META_ID, tonChainId, false)))
            )
            val paths = viewModel.derivationPathLiveData.observed()
            return Exported(viewModel.mnemonicLiveData.observed().words, paths.component1(), paths.component2())
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun <T> LiveData<T>.observed(): T {
        observeForever {}
        return checkNotNull(value)
    }

    /**
     * Types [exported] into a new import (an absent Ethereum path is the default one) and enrols
     * it as the app does an import whose old TON address is empty; returns its TON public key.
     */
    private suspend fun restore(exported: Exported): ByteArray {
        val creator = mock<WalletSecretMutationCoordinator>()
        whenever(creator.create(any(), any(), any(), anyOrNull())).thenReturn(RESTORED_ID)
        SubstrateOrEvmAccountRepository(mock(), creator).create(
            AddAccountPayload.SubstrateOrEvm(
                accountName = "Restored",
                mnemonic = exported.words,
                encryptionType = CryptoType.ECDSA,
                substrateDerivationPath = exported.substratePath.orEmpty(),
                ethereumDerivationPath = exported.ethereumPath ?: DEFAULT_ETHEREUM_PATH,
                googleBackupAddress = null,
                isBackedUp = true
            )
        )
        val prototype = argumentCaptor<MetaAccountLocal>()
        val substrate = argumentCaptor<String>()
        val ethereum = argumentCaptor<String>()
        verify(creator).create(prototype.capture(), substrate.capture(), ethereum.capture(), anyOrNull())
        SubstrateSecretStore(preferences).put(RESTORED_ID, SubstrateSecrets.read(substrate.firstValue))
        EthereumSecretStore(preferences).put(RESTORED_ID, EthereumSecrets.read(ethereum.firstValue))
        val local = prototype.firstValue
        val restored = MetaAccount(
            id = RESTORED_ID, chainAccounts = emptyMap(), favoriteChains = emptyMap(),
            substratePublicKey = local.substratePublicKey, substrateCryptoType = local.substrateCryptoType,
            substrateAccountId = local.substrateAccountId, ethereumAddress = local.ethereumAddress,
            ethereumPublicKey = local.ethereumPublicKey, tonPublicKey = null, isSelected = false, isBackedUp = true,
            googleBackupAddress = null, name = "Restored", initialized = false
        )
        whenever(accountDataSource.getMetaAccount(RESTORED_ID)).thenReturn(restored)
        whenever(accountDataSource.allMetaAccounts()).thenReturn(listOf(restored))
        whenever(dao.getMetaAccount(RESTORED_ID)).thenReturn(restored.toLocal())
        whenever(coordinator.addTon(any(), any(), any())).thenReturn(RESTORED_ID)
        LegacyNetworkAccountUpgrade(repository(), dao, coordinator, UniversalWalletFundsProbe { _, _, _, _ -> Funds.EMPTY }) {
            emptySet()
        }.upgrade()
        val after = argumentCaptor<MetaAccountLocal>()
        verify(coordinator).addTon(any(), after.capture(), any())
        return checkNotNull(after.firstValue.tonPublicKey)
    }

    /** Funds per slot account; a read must name the passphrase that derives the account. */
    private class Ledger : UniversalWalletFundsProbe {
        private val readings = linkedMapOf<String, UniversalWalletFundsProbe.Reading>()

        fun fund(slot: UniversalWalletSlot, legacy: Boolean, balance: String?) = set(slot, legacy, Funds.FUNDED, balance)
        fun unknown(slot: UniversalWalletSlot, legacy: Boolean) = set(slot, legacy, Funds.UNKNOWN, null)
        fun empty(slot: UniversalWalletSlot, legacy: Boolean) = set(slot, legacy, Funds.EMPTY, null)

        private fun set(slot: UniversalWalletSlot, legacy: Boolean, funds: Funds, balance: String?) {
            readings[key(slot, slot.publicKey(MNEMONIC, if (legacy) "" else PASSWORD))] = UniversalWalletFundsProbe.Reading(funds, balance)
        }

        override suspend fun probe(slot: UniversalWalletSlot, mnemonic: String, passphrase: String, publicKey: ByteArray): Funds =
            read(slot, mnemonic, passphrase, publicKey).funds

        override suspend fun read(
            slot: UniversalWalletSlot,
            mnemonic: String,
            passphrase: String,
            publicKey: ByteArray
        ): UniversalWalletFundsProbe.Reading {
            check(slot.publicKey(mnemonic, passphrase).contentEquals(publicKey)) { "${slot.name} read with another passphrase" }
            return readings[key(slot, publicKey)] ?: UniversalWalletFundsProbe.Reading(Funds.EMPTY)
        }

        private fun key(slot: UniversalWalletSlot, publicKey: ByteArray) = slot.name + publicKey.joinToString("") { "%02x".format(it) }
    }

    /** A notice over [current]: chain rows and the TON root change exactly as Room would change them. */
    private suspend fun trackedNotice(ledger: Ledger): UniversalWalletPasswordNoticeService {
        whenever(accountDataSource.getMetaAccount(META_ID)).thenAnswer { current }
        whenever(dao.getMetaAccount(META_ID)).thenAnswer { current.toLocal() }
        whenever(dao.replaceDerivedChainAccount(any(), any(), any(), any(), any())).thenAnswer { invocation ->
            val chainId = invocation.getArgument<String>(1)
            val expected = invocation.getArgument<ByteArray>(2)
            val publicKey = invocation.getArgument<ByteArray>(3)
            onReplace(UniversalWalletSlot.chainRowSlots.single { it.matches(chainId) })
            val row = current.chainAccounts.getValue(chainId)
            if (!row.publicKey.contentEquals(expected)) return@thenAnswer 0
            current = current.copy(chainAccounts = current.chainAccounts + (chainId to MetaAccount.ChainAccount(
                META_ID, null, publicKey, publicKey.clone(), row.cryptoType, row.accountName
            )))
            1
        }
        whenever(coordinator.replaceUniversalTon(any(), any(), any())).thenAnswer { invocation ->
            val before = invocation.getArgument<MetaAccountLocal>(0)
            check(before.tonPublicKey.contentEquals(current.tonPublicKey)) { "A stale TON before-image" }
            onReplace(UniversalWalletSlot.TON)
            current = current.copy(tonPublicKey = invocation.getArgument<MetaAccountLocal>(1).tonPublicKey)
            META_ID
        }
        return UniversalWalletPasswordNoticeService(repository(), dao, coordinator, ledger, noticePreferences, watchList)
    }

    private fun notice(probe: UniversalWalletFundsProbe) =
        UniversalWalletPasswordNoticeService(repository(), dao, coordinator, probe, memoryPreferences())

    private fun repository() = AccountRepositoryImpl(
        accountDataSource = accountDataSource,
        metaAccountDao = mock<MetaAccountDao>(),
        legacyStoreV2 = SecretStoreV2(preferences),
        jsonSeedDecoder = mock<JsonSeedDecoder>(),
        jsonSeedEncoder = mock<JsonSeedEncoder>(),
        languagesHolder = mock<LanguagesHolder>(),
        chainsRepository = mock<ChainsRepository>(),
        nomisScoresDao = mock<NomisScoresDao>(),
        substrateSecretStore = SubstrateSecretStore(preferences),
        ethereumSecretStore = EthereumSecretStore(preferences),
        tonSecretStore = TonSecretStore(preferences),
        accountRepositoryDelegate = mock<AccountRepositoryDelegate>(),
        walletSecretMutationCoordinator = coordinator,
        walletSecretAccessGuard = WalletSecretAccessGuard(preferences),
        dispatcher = UnconfinedTestDispatcher(),
        universalWalletWatchList = watchList
    )

    private fun memoryPreferences(): Preferences {
        val values = linkedMapOf<String, String>()
        return Proxy.newProxyInstance(Preferences::class.java.classLoader, arrayOf(Preferences::class.java)) { _, method, args ->
            when (method.name) {
                "getString" -> values[args[0] as String] ?: args.getOrNull(1)
                "putString" -> {
                    (args[1] as String?)?.let { values[args[0] as String] = it } ?: values.remove(args[0] as String)
                    Unit
                }
                else -> error("Unexpected preference call: ${method.name}")
            }
        } as Preferences
    }

    private fun expectedAddress(expected: JsonObject, slot: UniversalWalletSlot): String = when (slot) {
        UniversalWalletSlot.BITCOIN_MAINNET -> expected.getAsJsonObject("bitcoin").getAsJsonObject("mainnet")["firstReceiveAddress"].asString
        UniversalWalletSlot.BITCOIN_TESTNET -> expected.getAsJsonObject("bitcoin").getAsJsonObject("testnet")["firstReceiveAddress"].asString
        UniversalWalletSlot.SOLANA -> expected.getAsJsonObject("solana")["address"].asString
        UniversalWalletSlot.TAIRA -> expected.getAsJsonObject("iroha").getAsJsonObject("taira")["i105"].asString
        UniversalWalletSlot.TON -> expected.getAsJsonObject("ton")["addressNonBounceable"].asString
    }

    private fun vector(resource: String, id: String): JsonObject {
        val stream = requireNotNull(javaClass.classLoader?.getResourceAsStream(resource)) { "$resource is missing" }
        val fixture = stream.use { JsonParser.parseReader(InputStreamReader(it, Charsets.UTF_8)).asJsonObject }
        return fixture.getAsJsonArray("vectors").map { it.asJsonObject }.single { it["id"].asString == id }
    }

    private companion object {

        @JvmStatic
        @BeforeClass
        fun enableByteBuddyForJava21TestRuntime() {
            System.setProperty("net.bytebuddy.experimental", "true")
        }

        const val META_ID = 9L
        const val RESTORED_ID = 10L
        val DEFAULT_ETHEREUM_PATH: String = BIP32JunctionDecoder.DEFAULT_DERIVATION_PATH
        const val MNEMONIC = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        const val PASSWORD = "TREZOR"
        val CATALOG = setOf(
            UniversalWalletRegistry.bitcoinMainnet.id,
            UniversalWalletRegistry.bitcoinTestnet.id,
            UniversalWalletRegistry.solanaMainnet.id,
            UniversalWalletRegistry.taira.id
        )

        fun chainIdOf(slot: UniversalWalletSlot): String = when (slot) {
            UniversalWalletSlot.BITCOIN_MAINNET -> UniversalWalletRegistry.bitcoinMainnet.id
            UniversalWalletSlot.BITCOIN_TESTNET -> UniversalWalletRegistry.bitcoinTestnet.id
            UniversalWalletSlot.SOLANA -> UniversalWalletRegistry.solanaMainnet.id
            UniversalWalletSlot.TAIRA -> UniversalWalletRegistry.taira.id
            UniversalWalletSlot.TON -> error("TON is a root, not a chain row")
        }
    }
}
