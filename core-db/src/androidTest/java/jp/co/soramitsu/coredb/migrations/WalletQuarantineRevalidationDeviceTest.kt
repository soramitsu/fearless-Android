package jp.co.soramitsu.coredb.migrations

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.common.data.secrets.EmergencyRecoveryFieldType
import jp.co.soramitsu.common.data.secrets.WalletEmergencyRecoveryReader
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecrets
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecretStore
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.storage.encrypt.TonConnectStorageKeys
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityRecovery
import jp.co.soramitsu.common.data.storage.encrypt.WalletRecoveryStateCounts
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretAccessGuard
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretArchive
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretQuarantine
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretScope
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.core.crypto.mapCryptoTypeToEncryption
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.coredb.APP_DATABASE_VERSION
import jp.co.soramitsu.coredb.AppDatabase
import jp.co.soramitsu.coredb.migrations.shipped.ShippedSecretWriters
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.scale.toHexString
import jp.co.soramitsu.testshared.HashMapEncryptedPreferences
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The state internal builds 230/231 (databases 77-80) left on a device, opened
 * at the current schema the way the startup gate does: the revalidation pass,
 * then the orphan inventory, then the app-wide recovery inventory. Real
 * SQLite and the shipped SR25519 library; every wallet key ends up usable and
 * nothing is left that blocks startup (addendum U0.1, U0.3; AND-S1, AND-S4).
 */
class WalletQuarantineRevalidationDeviceTest {

    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @After
    fun deleteDatabase() {
        context.deleteDatabase(DATABASE)
    }

    @Test
    fun internalBuildRecoveryStateIsReleasedOnStartWithoutLosingAnyKey() {
        // AND-V2 as 230/231 left it: the 3.7.4 raw-seed payload quarantined.
        val rawSeedWallet = ShippedSecretWriters.importFromSeed374(
            seed = RAW_SEED_HEX,
            derivationPath = "",
            selectedEncryptionType = CryptoType.SR25519,
            ethSeed = null
        )
        // AND-S2 as 230/231 left it: a 4.2.0 SR25519 soft-path key whose
        // random nonce no re-derivation reproduces.
        val softPathSeed = SOFT_SEED_HEX.removePrefix("0x").fromHex()
        val softPathKeypair = SubstrateKeypairFactory.generate(
            mapCryptoTypeToEncryption(CryptoType.SR25519),
            softPathSeed,
            jp.co.soramitsu.fearless_utils.encrypt.junction.SubstrateJunctionDecoder
                .decode(SOFT_PATH).junctions
        )
        val softPathSecret = SubstrateSecrets(
            substrateKeyPair = softPathKeypair,
            seed = softPathSeed,
            substrateDerivationPath = SOFT_PATH
        ).toHexString()
        // A 3.x chain account quarantined by the strict chain validator.
        val chainSeed = CHAIN_SEED_HEX.removePrefix("0x").fromHex()
        val chainKeypair = SubstrateKeypairFactory.generate(
            mapCryptoTypeToEncryption(CryptoType.ED25519),
            chainSeed,
            emptyList()
        )
        val chainAccountId = chainKeypair.publicKey.substrateAccountId()
        val chainKey = "$RAW_SEED_META_ID:${chainAccountId.toHexString()}:ACCESS_SECRETS"
        val chainSecret = ChainAccountSecrets(keyPair = chainKeypair, seed = chainSeed).toHexString()
        // AND-S1 as 230/231 left it: a wallet deleted on 4.x, its keys
        // quarantined with markers, plus its TON Connect session.
        val deletedKeys = listOf("$DELETED_META_ID:SUBSTRATE_SECRETS", "$DELETED_META_ID:TON_SECRETS")
        val deletedSession = TonConnectStorageKeys.scoped(
            metaId = DELETED_META_ID,
            url = "https://deleted.example/manifest.json",
            source = "WEB"
        )
        val preferences = HashMapEncryptedPreferences().apply {
            putEncryptedString(
                WalletSecretQuarantine.keyFor("$RAW_SEED_META_ID:ACCESS_SECRETS"),
                rawSeedWallet.accessSecretsHex
            )
            putEncryptedString(
                WalletSecretQuarantine.keyFor("$SOFT_PATH_META_ID:SUBSTRATE_SECRETS"),
                softPathSecret
            )
            putEncryptedString(WalletSecretQuarantine.keyFor(chainKey), chainSecret)
            (deletedKeys + deletedSession).forEach { activeKey ->
                putEncryptedString(WalletSecretQuarantine.keyFor(activeKey), "deleted:$activeKey")
                putEncryptedString(
                    WalletPublicIdentityRecovery.keyFor(DELETED_META_ID, activeKey),
                    WalletPublicIdentityRecovery.MARKER_VALUE
                )
            }
            // A 4.x leftover the internal build never saw.
            putEncryptedString("$OLDER_DELETED_META_ID:ETHEREUM_SECRETS", "older-deleted")
        }

        helper.createDatabase(DATABASE, APP_DATABASE_VERSION).use { database ->
            database.insertWallet(RAW_SEED_META_ID, rawSeedWallet.substratePublicKey)
            database.insertWallet(SOFT_PATH_META_ID, softPathKeypair.publicKey)
            database.insertChainAccount(
                metaId = RAW_SEED_META_ID,
                publicKey = chainKeypair.publicKey,
                accountId = chainAccountId,
                cryptoType = CryptoType.ED25519
            )

            repeat(2) {
                WalletQuarantineRevalidation(preferences).reconcile(database)
                WalletOrphanSecretInventory(preferences).reconcile(database)
                assertFalse(WalletSecretAccessGuard(preferences).hasAnyRecoveryState())
            }
        }

        assertNotNull(
            SubstrateSecretStore(preferences).get(
                metaId = RAW_SEED_META_ID,
                expectedPublicKey = rawSeedWallet.substratePublicKey,
                expectedCryptoType = CryptoType.SR25519,
                expectedAccountId = rawSeedWallet.substrateAccountId
            )
        )
        assertEquals(
            rawSeedWallet.accessSecretsHex,
            preferences.getDecryptedString(WalletSecretArchive.keyFor("$RAW_SEED_META_ID:ACCESS_SECRETS"))
        )
        assertEquals(
            softPathSecret,
            preferences.getDecryptedString("$SOFT_PATH_META_ID:SUBSTRATE_SECRETS")
        )
        val softPath = checkNotNull(
            SubstrateSecretStore(preferences).get(
                metaId = SOFT_PATH_META_ID,
                expectedPublicKey = softPathKeypair.publicKey,
                expectedCryptoType = CryptoType.SR25519,
                expectedAccountId = softPathKeypair.publicKey.substrateAccountId()
            )
        )
        assertArrayEquals(
            softPathKeypair.privateKey,
            softPath[SubstrateSecrets.SubstrateKeypair][
                jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema.PrivateKey
            ]
        )
        assertEquals(chainSecret, preferences.getDecryptedString(chainKey))
        (deletedKeys + deletedSession).forEach { activeKey ->
            assertEquals(
                "deleted:$activeKey",
                preferences.getDecryptedString(WalletSecretArchive.keyFor(activeKey))
            )
        }
        assertEquals(
            "older-deleted",
            preferences.getDecryptedString(
                WalletSecretArchive.keyFor("$OLDER_DELETED_META_ID:ETHEREUM_SECRETS")
            )
        )
        assertTrue(preferences.hasKey("$RAW_SEED_META_ID:SUBSTRATE_SECRETS"))
    }

    @Test
    fun markersInternalBuildsWroteAreReevaluatedAndScopedToTheirKey() {
        // A 4.2.0 SR25519 soft-path wallet that 230/231 flagged with both a
        // quarantine and a public-identity marker.
        val softPathSeed = SOFT_SEED_HEX.removePrefix("0x").fromHex()
        val softPathKeypair = SubstrateKeypairFactory.generate(
            mapCryptoTypeToEncryption(CryptoType.SR25519),
            softPathSeed,
            jp.co.soramitsu.fearless_utils.encrypt.junction.SubstrateJunctionDecoder
                .decode(SOFT_PATH).junctions
        )
        val softPathKey = "$SOFT_PATH_META_ID:SUBSTRATE_SECRETS"
        val softPathSecret = SubstrateSecrets(
            substrateKeyPair = softPathKeypair,
            seed = softPathSeed,
            substrateDerivationPath = SOFT_PATH
        ).toHexString()
        // A wallet whose recorded account id is not its public key's: the
        // marker still applies and must stay, for that key only.
        val malformedSeed = RAW_SEED_HEX.removePrefix("0x").fromHex()
        val malformedKeypair = SubstrateKeypairFactory.generate(
            mapCryptoTypeToEncryption(CryptoType.SR25519),
            malformedSeed,
            emptyList()
        )
        val malformedKey = "$RAW_SEED_META_ID:SUBSTRATE_SECRETS"
        val malformedSecret = SubstrateSecrets(
            substrateKeyPair = malformedKeypair,
            seed = malformedSeed
        ).toHexString()
        val preferences = HashMapEncryptedPreferences().apply {
            putEncryptedString(WalletSecretQuarantine.keyFor(softPathKey), softPathSecret)
            putEncryptedString(
                WalletPublicIdentityRecovery.keyFor(SOFT_PATH_META_ID, softPathKey),
                WalletPublicIdentityRecovery.MARKER_VALUE
            )
            putEncryptedString(malformedKey, malformedSecret)
            putEncryptedString(
                WalletPublicIdentityRecovery.keyFor(RAW_SEED_META_ID, malformedKey),
                WalletPublicIdentityRecovery.MARKER_VALUE
            )
        }

        helper.createDatabase(DATABASE, APP_DATABASE_VERSION).use { database ->
            database.insertWallet(SOFT_PATH_META_ID, softPathKeypair.publicKey)
            database.insertWallet(
                RAW_SEED_META_ID,
                malformedKeypair.publicKey,
                accountId = ByteArray(32) { 9 }
            )
            repeat(2) {
                WalletQuarantineRevalidation(preferences).reconcile(database)
            }
        }

        assertEquals(softPathSecret, preferences.getDecryptedString(softPathKey))
        assertFalse(preferences.hasKey(WalletSecretQuarantine.keyFor(softPathKey)))
        assertFalse(
            preferences.hasKey(WalletPublicIdentityRecovery.keyFor(SOFT_PATH_META_ID, softPathKey))
        )
        assertEquals(malformedSecret, preferences.getDecryptedString(malformedKey))
        assertTrue(
            preferences.hasKey(WalletPublicIdentityRecovery.keyFor(RAW_SEED_META_ID, malformedKey))
        )
        val guard = WalletSecretAccessGuard(preferences)
        assertFalse(guard.isRecoveryRequired(SOFT_PATH_META_ID, WalletSecretScope.Substrate))
        assertTrue(guard.isRecoveryRequired(RAW_SEED_META_ID, WalletSecretScope.Substrate))
        assertFalse(guard.isRecoveryRequired(RAW_SEED_META_ID, WalletSecretScope.Ethereum))
        assertEquals(WalletRecoveryStateCounts(1, 0), guard.recoveryStateCounts())

        // Emergency key access still shows the flagged wallet's seed exactly.
        val flagged = WalletEmergencyRecoveryReader(preferences).read()
            .single { it.storageKey == malformedKey }
        assertEquals(
            RAW_SEED_HEX,
            flagged.fields.single { it.type == EmergencyRecoveryFieldType.RAW_SEED }.value
        )
    }

    private fun SupportSQLiteDatabase.insertWallet(
        metaId: Long,
        publicKey: ByteArray,
        accountId: ByteArray = publicKey.substrateAccountId()
    ) {
        execSQL(
            """
            INSERT INTO meta_accounts(
                id, substratePublicKey, substrateCryptoType, substrateAccountId,
                name, isSelected, position, isBackedUp, initialized
            ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>(
                metaId,
                publicKey,
                CryptoType.SR25519.name,
                accountId,
                "Wallet $metaId",
                0,
                metaId.toInt(),
                1,
                1
            )
        )
    }

    private fun SupportSQLiteDatabase.insertChainAccount(
        metaId: Long,
        publicKey: ByteArray,
        accountId: ByteArray,
        cryptoType: CryptoType
    ) {
        execSQL(
            """
            INSERT INTO chains(
                id, name, icon, prefix, isEthereumBased, isTestNet, hasCrowdloans,
                supportStakingPool, isEthereumChain, isChainlinkProvider,
                supportNft, isUsesAppId, ecosystem
            ) VALUES(?, ?, ?, ?, 0, 0, 0, 0, 0, 0, 0, 0, ?)
            """.trimIndent(),
            arrayOf<Any>(CHAIN_ID, "Chain", "icon", 42, "Substrate")
        )
        execSQL(
            """
            INSERT INTO chain_accounts(
                metaId, chainId, publicKey, accountId, cryptoType, name, initialized
            ) VALUES(?, ?, ?, ?, ?, ?, 1)
            """.trimIndent(),
            arrayOf<Any>(metaId, CHAIN_ID, publicKey, accountId, cryptoType.name, "Chain account")
        )
    }

    private companion object {
        const val DATABASE = "wallet-quarantine-revalidation"
        const val CHAIN_ID = "revalidation-chain"
        const val RAW_SEED_META_ID = 3L
        const val SOFT_PATH_META_ID = 4L
        const val DELETED_META_ID = 2L
        const val OLDER_DELETED_META_ID = 1L
        const val SOFT_PATH = "//polkadot/0"
        const val RAW_SEED_HEX = "0x9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60"
        const val SOFT_SEED_HEX = "0x4ccd089b28ff96da9db6c346ec114e0f5b8a319f35aba624da8cf6ed4fb8a6fb"
        const val CHAIN_SEED_HEX = "0xc5aa8df43f9f837bedb7442f31dcb7b166d38535076f094b85ce3a2e0b4458f7"
    }
}
