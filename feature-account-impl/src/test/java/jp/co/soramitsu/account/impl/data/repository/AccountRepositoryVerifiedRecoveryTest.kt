package jp.co.soramitsu.account.impl.data.repository

import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.impl.data.repository.datasource.AccountDataSource
import jp.co.soramitsu.backup.domain.models.BackupAccountType
import jp.co.soramitsu.common.data.secrets.v1.Keypair
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v2.SecretStoreV2
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecretStore
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecrets
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecretStore
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.secrets.v3.TonSecretStore
import jp.co.soramitsu.common.data.secrets.v3.TonSecrets
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretAccessGuard
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretQuarantine
import jp.co.soramitsu.common.resources.LanguagesHolder
import jp.co.soramitsu.common.utils.DEFAULT_DERIVATION_PATH
import jp.co.soramitsu.common.utils.deriveSeed32
import jp.co.soramitsu.common.utils.ethereumAddressFromPublicKey
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.coredb.dao.MetaAccountDao
import jp.co.soramitsu.coredb.dao.NomisScoresDao
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.json.JsonSeedDecoder
import jp.co.soramitsu.fearless_utils.encrypt.json.JsonSeedEncoder
import jp.co.soramitsu.fearless_utils.encrypt.junction.BIP32JunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair as FearlessKeypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.ethereum.EthereumKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.MnemonicCreator
import jp.co.soramitsu.fearless_utils.encrypt.seed.ethereum.EthereumSeedFactory
import jp.co.soramitsu.fearless_utils.encrypt.seed.substrate.SubstrateSeedFactory
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.testshared.HashMapEncryptedPreferences
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.BeforeClass
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.mnemonic.Mnemonic

/**
 * Everything this repository hands out feeds phrase, seed and JSON export,
 * backup eligibility, and Bitcoin/Solana/Iroha derivation. Recovery material
 * that no longer re-derives the signing key must never reach them (it would
 * restore another account), while the key itself keeps signing.
 */
class AccountRepositoryVerifiedRecoveryTest {

    private val preferences = HashMapEncryptedPreferences()
    private val accountDataSource = mock<AccountDataSource>()

    @Test
    fun driftedRecoveryMaterialIsWithheldWhileTheKeypairsRemain() = runTest {
        val substrate = SubstrateKeypairFactory.generate(
            encryptionType = EncryptionType.ECDSA,
            seed = ByteArray(32) { 11 },
            junctions = emptyList()
        )
        val ethereum = EthereumKeypairFactory.createWithPrivateKey(ByteArray(32) { 12 })
        val ton = tonKeypair(TYPED_TON_PHRASE)
        val wallet = wallet(substrate.publicKey, ethereum.publicKey, ton.publicKey)
        whenever(accountDataSource.getMetaAccount(META_ID)).thenReturn(wallet)

        SubstrateSecretStore(preferences).put(
            META_ID,
            SubstrateSecrets(
                substrateKeyPair = substrate,
                entropy = ByteArray(16) { 13 },
                seed = ByteArray(32) { 14 },
                substrateDerivationPath = "//polkadot"
            )
        )
        EthereumSecretStore(preferences).put(
            META_ID,
            EthereumSecrets(seed = ByteArray(32) { 15 }, ethereumKeypair = ethereum)
        )
        // 4.2.0 derived this TON key from the phrase with its trailing space,
        // so that exact text is the only phrase that restores the account.
        TonSecretStore(preferences).put(
            META_ID,
            TonSecrets(seed = TYPED_TON_PHRASE.encodeToByteArray(), tonKeypair = ton)
        )
        val exact = SECRET_KEYS.associateWith(preferences::getDecryptedString)
        val repository = repository()

        val substrateSecrets = checkNotNull(repository.getSubstrateSecrets(META_ID))
        assertNull(substrateSecrets[SubstrateSecrets.Entropy])
        assertNull(substrateSecrets[SubstrateSecrets.Seed])
        assertNull(substrateSecrets[SubstrateSecrets.SubstrateDerivationPath])
        assertArrayEquals(
            substrate.privateKey,
            substrateSecrets[SubstrateSecrets.SubstrateKeypair][KeyPairSchema.PrivateKey]
        )
        val ethereumSecrets = checkNotNull(repository.getEthereumSecrets(META_ID))
        assertNull(ethereumSecrets[EthereumSecrets.Seed])
        assertArrayEquals(
            ethereum.privateKey,
            ethereumSecrets[EthereumSecrets.EthereumKeypair][KeyPairSchema.PrivateKey]
        )
        assertArrayEquals(
            TYPED_TON_PHRASE.encodeToByteArray(),
            repository.getTonSecrets(META_ID)?.get(TonSecrets.Seed)
        )

        // Backup falls back to the keypair-only JSON format.
        assertEquals(setOf(BackupAccountType.JSON), repository.getSupportedBackupTypes(META_ID))
        assertFalse(repository.isWalletRecoveryRequired(META_ID))

        SECRET_KEYS.forEach { key ->
            assertEquals(key, exact[key], preferences.getDecryptedString(key))
            assertFalse(key, preferences.hasKey(WalletSecretQuarantine.keyFor(key)))
        }
    }

    @Test
    fun verifiedRecoveryMaterialIsHandedOutUnchanged() = runTest {
        val mnemonic = MnemonicCreator.fromEntropy(ByteArray(16) { 21 })
        val substrateSeed = SubstrateSeedFactory.deriveSeed32(mnemonic.words, null).seed
        val substrate = SubstrateKeypairFactory.generate(
            encryptionType = EncryptionType.ECDSA,
            seed = substrateSeed,
            junctions = emptyList()
        )
        val ethereumPath = BIP32JunctionDecoder.decode(BIP32JunctionDecoder.DEFAULT_DERIVATION_PATH)
        val ethereum = EthereumKeypairFactory.generate(
            seed = EthereumSeedFactory.deriveSeed32(mnemonic.words, ethereumPath.password).seed,
            junctions = ethereumPath.junctions
        )
        val tonPhrase = mnemonic.words
        val ton = tonKeypair(tonPhrase)
        whenever(accountDataSource.getMetaAccount(META_ID))
            .thenReturn(wallet(substrate.publicKey, ethereum.publicKey, ton.publicKey))

        val substrateSecrets = SubstrateSecrets(
            substrateKeyPair = substrate,
            entropy = mnemonic.entropy,
            seed = substrateSeed
        )
        val ethereumSecrets = EthereumSecrets(
            entropy = mnemonic.entropy,
            seed = ethereum.privateKey,
            ethereumKeypair = ethereum,
            ethereumDerivationPath = BIP32JunctionDecoder.DEFAULT_DERIVATION_PATH
        )
        SubstrateSecretStore(preferences).put(META_ID, substrateSecrets)
        EthereumSecretStore(preferences).put(META_ID, ethereumSecrets)
        TonSecretStore(preferences).put(
            META_ID,
            TonSecrets(seed = tonPhrase.encodeToByteArray(), tonKeypair = ton)
        )
        val repository = repository()

        assertArrayEquals(
            mnemonic.entropy,
            repository.getSubstrateSecrets(META_ID)?.get(SubstrateSecrets.Entropy)
        )
        assertArrayEquals(
            mnemonic.entropy,
            repository.getEthereumSecrets(META_ID)?.get(EthereumSecrets.Entropy)
        )
        assertArrayEquals(
            tonPhrase.encodeToByteArray(),
            repository.getTonSecrets(META_ID)?.get(TonSecrets.Seed)
        )
        assertEquals(
            setOf(BackupAccountType.PASSPHRASE, BackupAccountType.SEED, BackupAccountType.JSON),
            repository.getSupportedBackupTypes(META_ID)
        )
    }

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
        walletSecretMutationCoordinator = mock<WalletSecretMutationCoordinator>(),
        walletSecretAccessGuard = WalletSecretAccessGuard(preferences),
        dispatcher = UnconfinedTestDispatcher()
    )

    private fun wallet(
        substratePublicKey: ByteArray,
        ethereumPublicKey: ByteArray,
        tonPublicKey: ByteArray
    ) = MetaAccount(
        id = META_ID,
        chainAccounts = emptyMap(),
        favoriteChains = emptyMap(),
        substratePublicKey = substratePublicKey,
        substrateCryptoType = CryptoType.ECDSA,
        substrateAccountId = substratePublicKey.substrateAccountId(),
        ethereumAddress = ethereumPublicKey.ethereumAddressFromPublicKey(),
        ethereumPublicKey = ethereumPublicKey,
        tonPublicKey = tonPublicKey,
        isSelected = true,
        isBackedUp = true,
        googleBackupAddress = null,
        name = "Recovery export wallet",
        initialized = true
    )

    private fun tonKeypair(typedText: String): FearlessKeypair {
        // Exactly how 4.2.0 TonAccountRepository.create derived the key.
        val privateKey = PrivateKeyEd25519(Mnemonic.toSeed(typedText.split(" ")))
        return Keypair(
            publicKey = privateKey.publicKey().key.toByteArray(),
            privateKey = privateKey.key.toByteArray()
        )
    }

    private companion object {

        @JvmStatic
        @BeforeClass
        fun enableByteBuddyForJava21TestRuntime() {
            System.setProperty("net.bytebuddy.experimental", "true")
        }

        const val META_ID = 7L
        const val TYPED_TON_PHRASE =
            "abandon abandon abandon abandon abandon abandon abandon abandon " +
                "abandon abandon abandon about "
        val SECRET_KEYS = listOf(
            "$META_ID:SUBSTRATE_SECRETS",
            "$META_ID:ETHEREUM_SECRETS",
            "$META_ID:TON_SECRETS"
        )
    }
}
