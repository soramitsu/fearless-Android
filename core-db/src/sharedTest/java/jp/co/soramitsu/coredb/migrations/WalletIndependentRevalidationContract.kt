package jp.co.soramitsu.coredb.migrations

import androidx.sqlite.db.SupportSQLiteDatabase
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecrets
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferenceSnapshot
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.TonConnectStorageKeys
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityRecovery
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretArchive
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretQuarantine
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.core.crypto.mapCryptoTypeToEncryption
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.coredb.migrations.shipped.ShippedSecretWriters
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.scale.toHexString
import jp.co.soramitsu.testshared.HashMapEncryptedPreferences
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Same public released-writer recovery cases on actual JDBC and Android SQLite. */
abstract class WalletIndependentRevalidationContract {
    protected interface FixtureDatabase : AutoCloseable {
        val db: SupportSQLiteDatabase
        fun rawFileDigest(): String
    }

    private val shipped = ShippedSecretWriters.importFromSeed374(
        seed = "0x9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60",
        derivationPath = "", selectedEncryptionType = CryptoType.ED25519, ethSeed = null
    )
    private val identity = WalletPublicIdentity(
        5L, shipped.substratePublicKey, shipped.substrateCryptoType, true,
        shipped.substrateAccountId, shipped.ethereumPublicKey, shipped.ethereumAddress, null
    )
    private val active = "5:ACCESS_SECRETS"
    private val quarantine = WalletSecretQuarantine.keyFor(active)

    protected abstract fun newDatabase(): FixtureDatabase

    @Test fun unrelatedOversizedPublicIdentityDoesNotSuppressHealthyKey() = fixture { holder, prefs ->
        insert(holder.db, 9L)
        holder.db.execSQL("UPDATE meta_accounts SET substratePublicKey = zeroblob(2097152) WHERE id = 9")
        releases(holder, prefs)
    }

    @Test fun healthyIdentityIsFoundBeyondTheOldWalletCountLimit() = fixture { holder, prefs ->
        holder.db.beginTransaction()
        try {
            for (id in 10L..8210L) insert(holder.db, id)
            holder.db.setTransactionSuccessful()
        } finally {
            holder.db.endTransaction()
        }
        releases(holder, prefs)
    }

    @Test fun largeRecoveryInventoryStillVisitsHealthyKey() = fixture { holder, prefs ->
        val unrelated = (1..9000).map { WalletSecretQuarantine.KEY_PREFIX + "unknown_" + it + "x".repeat(150) }
        unrelated.forEach { prefs.putEncryptedString(it, "unchanged") }
        releases(holder, prefs)
        unrelated.forEach { assertEquals("unchanged", prefs.getDecryptedString(it)) }
    }

    @Test fun oneOversizedRecoveryNameDoesNotBlockValidNames() = fixture { holder, prefs ->
        val oversized = WalletSecretQuarantine.KEY_PREFIX + "x".repeat(3000)
        prefs.putEncryptedString(oversized, "untouched")
        releases(holder, prefs)
        assertEquals("untouched", prefs.getDecryptedString(oversized))
    }

    @Test fun malformedSameWalletTonConnectionDoesNotBlockRootKey() = fixture { holder, prefs ->
        holder.db.execSQL("INSERT INTO ton_connection VALUES(5,'client','name','icon','', 'BROKEN')")
        releases(holder, prefs)
    }

    @Test fun rootRecoveryDoesNotDependOnUnrelatedChainTableReadability() = fixture { holder, prefs ->
        // Test-only malformed store: no public rows or production destructive repair.
        holder.db.execSQL("ALTER TABLE chain_accounts RENAME TO unavailable_chain_accounts")
        releases(holder, prefs)
    }

    @Test fun invalidOwnPublicKeyIsPreservedWhileAnotherKeyRestores() = fixture { holder, prefs ->
        insert(holder.db, 9L)
        holder.db.execSQL("UPDATE meta_accounts SET substratePublicKey = X'ff' WHERE id = 9")
        val invalid = WalletSecretQuarantine.keyFor("9:ACCESS_SECRETS")
        prefs.putEncryptedString(invalid, shipped.accessSecretsHex)
        releases(holder, prefs)
        assertEquals(shipped.accessSecretsHex, prefs.getDecryptedString(invalid))
        assertFalse(prefs.hasKey("9:SUBSTRATE_SECRETS"))
    }

    @Test fun healthyArchiveRestoresDespiteAnUnrelatedMalformedWallet() = fixture { holder, prefs ->
        insert(holder.db, 0L)
        prefs.removeKey(quarantine)
        val archive = WalletSecretArchive.keyFor(active)
        prefs.putEncryptedString(archive, shipped.accessSecretsHex)
        val before = holder.rawFileDigest()
        val report = WalletQuarantineRevalidation(prefs).reconcile(holder.db)
        assertEquals(1, report.restored)
        assertNotNull(prefs.getDecryptedString("5:SUBSTRATE_SECRETS"))
        assertEquals(shipped.accessSecretsHex, prefs.getDecryptedString(archive))
        assertEquals(before, holder.rawFileDigest())
    }

    @Test fun healthyMarkerClearsDespiteAnUnrelatedMalformedWallet() = fixture { holder, prefs ->
        insert(holder.db, -1L)
        val marker = WalletPublicIdentityRecovery.keyFor(5L, active)
        prefs.putEncryptedString(marker, WalletPublicIdentityRecovery.MARKER_VALUE)
        val report = releases(holder, prefs)
        assertEquals(1, report.markersCleared)
        assertFalse(prefs.hasKey(marker))
    }

    @Test fun absentOwnerArchivesIndependentlyOfInvalidWallet() = fixture { holder, prefs ->
        insert(holder.db, 0L)
        val missing = "99:SUBSTRATE_SECRETS"
        val key = WalletSecretQuarantine.keyFor(missing)
        val marker = WalletPublicIdentityRecovery.keyFor(99L, missing)
        prefs.putEncryptedString(key, "exact orphan bytes")
        prefs.putEncryptedString(marker, WalletPublicIdentityRecovery.MARKER_VALUE)
        val report = releases(holder, prefs)
        assertEquals(1, report.archived)
        assertEquals("exact orphan bytes", prefs.getDecryptedString(WalletSecretArchive.keyFor(missing)))
        assertFalse(prefs.hasKey(key))
        assertFalse(prefs.hasKey(marker))
    }

    @Test fun repeatStartDoesNotRewriteTheReleasedKeyOrDatabase() = fixture { holder, prefs ->
        insert(holder.db, 0L)
        releases(holder, prefs)
        val key = prefs.getDecryptedString("5:SUBSTRATE_SECRETS")
        val bytes = holder.rawFileDigest()
        val report = WalletQuarantineRevalidation(prefs).reconcile(holder.db)
        assertEquals(WalletQuarantineRevalidationReport(), report)
        assertEquals(key, prefs.getDecryptedString("5:SUBSTRATE_SECRETS"))
        assertEquals(bytes, holder.rawFileDigest())
    }

    @Test fun pointLookupPreservesBoundedInvalidPresentIdentity() = fixture { holder, _ ->
        holder.db.execSQL("UPDATE meta_accounts SET substratePublicKey = zeroblob(2097152) WHERE id = 5")
        val actual = readBoundedWalletPublicIdentity(holder.db, true, 5L)
        assertNotNull(actual)
        assertTrue(checkNotNull(actual).substratePublicKey!!.size < 1024)
        assertEquals(0, actual.substratePublicKey!!.size)
        assertEquals(null, readBoundedWalletPublicIdentity(holder.db, true, 99L))
    }

    @Test fun knownTonSessionRestoresDespiteAnotherMalformedConnection() = fixture { holder, prefs ->
        val key = TonConnectStorageKeys.scoped(5L, "https://public-fixture.example", "WEB")
        holder.db.execSQL("INSERT INTO ton_connection VALUES(5,'client','name','icon','https://public-fixture.example','WEB')")
        holder.db.execSQL("INSERT INTO ton_connection VALUES(5,'bad','name','icon','','BROKEN')")
        prefs.putEncryptedString(WalletSecretQuarantine.keyFor(key), "public-session-fixture")
        val before = holder.rawFileDigest()
        val report = WalletQuarantineRevalidation(prefs).reconcile(holder.db)
        assertEquals(2, report.released)
        assertEquals("public-session-fixture", prefs.getDecryptedString(key))
        assertEquals(before, holder.rawFileDigest())
    }

    @Test fun missingTonSessionIsNotArchivedWhenOwnerIdentityIsUnreadable() = fixture { holder, prefs ->
        insert(holder.db, 0L)
        holder.db.execSQL("INSERT INTO ton_connection VALUES(0,'client','name','icon','https://public-fixture.example','WEB')")
        val key = TonConnectStorageKeys.scoped(5L, "https://public-fixture.example", "WEB")
        prefs.putEncryptedString(WalletSecretQuarantine.keyFor(key), "public-session-fixture")
        releases(holder, prefs)
        assertEquals("public-session-fixture", prefs.getDecryptedString(WalletSecretQuarantine.keyFor(key)))
        assertFalse(prefs.hasKey(WalletSecretArchive.keyFor(key)))
    }

    @Test fun sameOwnerUnreadableChainAccountDoesNotJustifyOrphaning() = fixture { holder, prefs ->
        // A missing cache parent is irrelevant to judging this protected row.
        holder.db.setForeignKeyConstraintsEnabled(false)
        holder.db.execSQL("INSERT INTO chain_accounts VALUES(5,'fixture-chain',X'ff',X'','ED25519','test',1)")
        val key = "5:" + "ab".repeat(32) + ":ACCESS_SECRETS"
        prefs.putEncryptedString(WalletSecretQuarantine.keyFor(key), "preserve-exact")
        releases(holder, prefs)
        assertEquals("preserve-exact", prefs.getDecryptedString(WalletSecretQuarantine.keyFor(key)))
        assertFalse(prefs.hasKey(WalletSecretArchive.keyFor(key)))
    }

    @Test fun missingChainKeyIsNotArchivedWhenOwnerIdentityIsUnreadable() = fixture { holder, prefs ->
        holder.db.setForeignKeyConstraintsEnabled(false)
        val account = "ab".repeat(32)
        holder.db.execSQL(
            "INSERT INTO chain_accounts VALUES(0,'fixture-chain',X'ff',?,'ED25519','test',1)",
            arrayOf(account.fromHex())
        )
        val key = "5:$account:ACCESS_SECRETS"
        prefs.putEncryptedString(WalletSecretQuarantine.keyFor(key), "preserve-exact")
        releases(holder, prefs)
        assertEquals("preserve-exact", prefs.getDecryptedString(WalletSecretQuarantine.keyFor(key)))
        assertFalse(prefs.hasKey(WalletSecretArchive.keyFor(key)))
    }

    @Test fun tonTargetAfterMultiplePagesIsStillRestored() = fixture { holder, prefs ->
        for (i in 0..130) holder.db.execSQL(
            "INSERT INTO ton_connection VALUES(5,?,'name','icon',?,'WEB')",
            arrayOf("client$i", "https://public-fixture.example/$i")
        )
        val key = TonConnectStorageKeys.scoped(5L, "https://public-fixture.example/130", "WEB")
        prefs.putEncryptedString(WalletSecretQuarantine.keyFor(key), "public-session-fixture")
        val before = holder.rawFileDigest()
        val report = WalletQuarantineRevalidation(prefs).reconcile(holder.db)
        assertEquals(2, report.released)
        assertEquals("public-session-fixture", prefs.getDecryptedString(key))
        assertEquals(before, holder.rawFileDigest())
    }

    @Test fun minimumSqlRowIdIsVisitedBeforeTheFirstPageBoundary() = fixture { holder, prefs ->
        holder.db.execSQL(
            "INSERT INTO ton_connection(rowid,metaId,clientId,name,icon,url,source) VALUES(?,5,'client','name','icon','https://public-fixture.example','WEB')",
            arrayOf(Long.MIN_VALUE)
        )
        val key = TonConnectStorageKeys.scoped(5L, "https://public-fixture.example", "WEB")
        prefs.putEncryptedString(WalletSecretQuarantine.keyFor(key), "public-session-fixture")
        val before = holder.rawFileDigest()
        val report = WalletQuarantineRevalidation(prefs).reconcile(holder.db)
        assertEquals(2, report.released)
        assertEquals("public-session-fixture", prefs.getDecryptedString(key))
        assertEquals(before, holder.rawFileDigest())
    }

    @Test fun provingChainRowRestoresDespiteSameOwnerOversizedAccount() = assertChainRecovery(false, false)

    @Test fun provingChainRowAfterTwoPagesIsStillRestored() = assertChainRecovery(true, false)

    @Test fun conflictingRowsForOneChainSecretRemainUntouched() = assertChainRecovery(false, true)

    @Test fun provingChainRowRestoresDespiteAnotherUnreadableOwner() = assertChainRecovery(false, false, true)

    private fun assertChainRecovery(
        multiplePages: Boolean,
        conflict: Boolean,
        invalidOwner: Boolean = false
    ) = fixture { holder, prefs ->
        val seed = "0x4ccd089b28ff96da9db6c346ec114e0f5b8a319f35aba624da8cf6ed4fb8a6fb".removePrefix("0x").fromHex()
        val pair = SubstrateKeypairFactory.generate(mapCryptoTypeToEncryption(CryptoType.ED25519), seed, emptyList())
        val accountId = pair.publicKey.substrateAccountId()
        val key = "5:${accountId.toHexString()}:ACCESS_SECRETS"
        val secret = ChainAccountSecrets(keyPair = pair, seed = seed).toHexString()
        holder.db.setForeignKeyConstraintsEnabled(false)
        if (invalidOwner) {
            holder.db.execSQL(
                "INSERT INTO chain_accounts VALUES(0,'unknown-owner',?,?,'ED25519','test',1)",
            arrayOf(pair.publicKey, accountId)
            )
        }
        if (multiplePages) {
            for (i in 0..130) {
            holder.db.execSQL("INSERT INTO chain_accounts VALUES(5,?,X'ff',X'01','ED25519','test',1)", arrayOf("other$i"))
        }
        }
        holder.db.execSQL("INSERT INTO chain_accounts VALUES(5,'bad',X'ff',zeroblob(2097152),'ED25519','test',1)")
        holder.db.execSQL("INSERT INTO chain_accounts VALUES(5,'target',?,?,'ED25519','test',1)", arrayOf(pair.publicKey, accountId))
        if (conflict) holder.db.execSQL("INSERT INTO chain_accounts VALUES(5,'conflict',X'ff',?,'ED25519','test',1)", arrayOf(accountId))
        prefs.putEncryptedString(WalletSecretQuarantine.keyFor(key), secret)
        val before = holder.rawFileDigest()
        val report = WalletQuarantineRevalidation(prefs).reconcile(holder.db)
        assertEquals(if (conflict) 1 else 2, report.released)
        if (conflict) {
            assertEquals(1, report.skipped)
            assertFalse(prefs.hasKey(key))
            assertEquals(secret, prefs.getDecryptedString(WalletSecretQuarantine.keyFor(key)))
        } else {
            assertEquals(secret, prefs.getDecryptedString(key))
            assertFalse(prefs.hasKey(WalletSecretQuarantine.keyFor(key)))
        }
        assertEquals(before, holder.rawFileDigest())
    }

    @Test fun cancellationFromOneItemPropagatesWithoutMovingIt() = fixture { holder, prefs ->
        val canceled = CancellationException("public test cancellation")
        val guarded = object : EncryptedPreferences by prefs {
            override fun getDecryptedStringSnapshot(field: String): EncryptedPreferenceSnapshot? {
                if (field == quarantine) throw canceled
                return prefs.getDecryptedStringSnapshot(field)
            }
        }
        val before = holder.rawFileDigest()
        val actual = assertThrows(CancellationException::class.java) {
            WalletQuarantineRevalidation(guarded).reconcile(holder.db)
        }
        assertSame(canceled, actual)
        assertEquals(shipped.accessSecretsHex, prefs.getDecryptedString(quarantine))
        assertFalse(prefs.hasKey("5:SUBSTRATE_SECRETS"))
        assertEquals(before, holder.rawFileDigest())
    }

    @Test fun ambiguousTargetIdentityCannotBeTreatedAsMissing() = fixture { holder, _ ->
        holder.db.setForeignKeyConstraintsEnabled(false)
        holder.db.execSQL("ALTER TABLE meta_accounts RENAME TO original_meta_accounts")
        holder.db.execSQL("CREATE TABLE meta_accounts AS SELECT * FROM original_meta_accounts")
        holder.db.execSQL("INSERT INTO meta_accounts SELECT * FROM original_meta_accounts")
        assertThrows(jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityIntegrityException::class.java) {
            readBoundedWalletPublicIdentity(holder.db, true, 5L)
        }
    }

    private fun releases(
        holder: FixtureDatabase,
        prefs: HashMapEncryptedPreferences
    ): WalletQuarantineRevalidationReport {
        val before = holder.rawFileDigest()
        val report = WalletQuarantineRevalidation(prefs).reconcile(holder.db)
        assertEquals(1, report.released)
        assertEquals(shipped.accessSecretsHex, prefs.getDecryptedString(WalletSecretArchive.keyFor(active)))
        assertFalse(prefs.hasKey(quarantine))
        assertEquals("preserve-me", prefs.getDecryptedString("unrelated-public-test-entry"))
        assertEquals("Every durable SQL byte must stay exact", before, holder.rawFileDigest())
        return report
    }

    private fun insert(db: SupportSQLiteDatabase, id: Long) {
        db.execSQL(
            "INSERT INTO meta_accounts(id,substratePublicKey,substrateCryptoType,substrateAccountId,name,isSelected,position,isBackedUp,initialized) VALUES(?,?,?,?,?,0,0,0,1)",
            arrayOf<Any?>(id, identity.substratePublicKey, identity.substrateCryptoType?.name, identity.substrateAccountId, "Public shipped-writer identity")
        )
    }

    private fun fixture(action: (FixtureDatabase, HashMapEncryptedPreferences) -> Unit) {
        newDatabase().use { holder ->
            holder.db.version = 83
            holder.db.setForeignKeyConstraintsEnabled(true)
            insert(holder.db, 5L)
            val prefs = HashMapEncryptedPreferences().apply {
                putEncryptedString(quarantine, shipped.accessSecretsHex)
                putEncryptedString("unrelated-public-test-entry", "preserve-me")
            }
            action(holder, prefs)
        }
    }
}
