package jp.co.soramitsu.coredb.migrations

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.*
import org.junit.Test

/** Shipped3.7.4 database and public wallet42, with exact rollback and retained user choices. */
abstract class CacheRowAvailabilityContract {
    @Test fun version71LargeCacheReachesCurrentSqlSchema() = fixture(
        "v71-large-cache", { seed(it, 262_145) }
    ) { db ->
        val protected = protectedRows(db)
        TonUpgradeSqlPreparation.prepare(db)
        TonUpgradeSqlPreflight.requireSafeToMutate(db)
        CurrentEndpointUpgradePreflight.runSqlSuffix(db, 71)
        assertMigrated(db, protected, 262_145)
    }

    @Test fun version76LargeCachePassesSecretQueryPreflight() = fixture(
        "v76-large-cache", { seed(it, 262_145) }
    ) { db ->
        val protected = protectedRows(db)
        TonUpgradeSqlPreparation.prepare(db)
        TonUpgradeSqlPreflight.requireSafeToMutate(db)
        TonSchemaMigration.migrate(db)
        Migration_72_73.migrate(db)
        Migration_73_74.migrate(db)
        Migration_74_75.migrate(db)
        Migration_75_76.migrate(db)
        WalletIntegritySqlPreparation.prepare(db)
        WalletSecretIntegrityQueryPreflight.requireReadable(db)
        CurrentEndpointUpgradePreflight.runSqlSuffix(db, 77)
        assertMigrated(db, protected, 262_145)
    }

    @Test fun uncappedCacheStillRejectsInvalidForeignKey() = policy("fk")
    @Test fun protectedWalletRowsRemainCapped() = policy("wallet")
    @Test fun mixedUserAssetRowsRemainCapped() = policy("asset")
    @Test fun explicitlyBoundedCachePolicyStillRejectsExcessRows() = policy("bounded")

    private fun policy(mode: String) = fixture("policy-$mode", { seed(it, 2) }) { db ->
        TonUpgradeSqlPreparation.prepare(db)
        var limits = TON_UPGRADE_FOREIGN_KEY_CHECK_LIMITS.copy(
            maximumRowsByTable = TON_UPGRADE_FOREIGN_KEY_CHECK_LIMITS.maximumRowsByTable + ("chain_assets" to 1)
        )
        when (mode) {
            "fk" -> db.execSQL("UPDATE chain_assets SET chainId='missing-parent' WHERE id='1'")
            "bounded" -> limits = limits.copy(uncappedCacheTables = emptySet())
            "wallet" -> {
                limits = limits.copy(maximumRowsByTable = limits.maximumRowsByTable + ("meta_accounts" to 1))
                db.execSQL("INSERT INTO meta_accounts(id,substratePublicKey,substrateCryptoType,substrateAccountId,name,isSelected,position,isBackedUp,initialized) SELECT 43,substratePublicKey,substrateCryptoType,substrateAccountId,'Other public wallet',0,1,1,1 FROM meta_accounts WHERE id=42")
            }
            "asset" -> {
                limits = limits.copy(maximumRowsByTable = limits.maximumRowsByTable + ("assets" to 1))
                db.execSQL("INSERT INTO assets(id,chainId,accountId,metaId,sortIndex,enabled,markedNotNeed,freeInPlanks) SELECT '1',chainId,accountId,metaId,20,0,0,'1' FROM assets WHERE id='0'")
            }
        }
        val failure = assertThrows(TonUpgradeSqlIntegrityException::class.java) {
            requireBoundedForeignKeyCheck(db, limits) { TonUpgradeSqlIntegrityException(it) }
        }
        val expected = when (mode) {
            "bounded" -> "chain_assets rows exceed the safe migration limit"
            "wallet" -> "meta_accounts rows exceed the safe migration limit"
            "asset" -> "assets rows exceed the safe migration limit"
            else -> "The database contains a foreign-key violation"
        }
        assertEquals(expected, failure.message)
        assertEquals(71, db.version)
    }

    private fun seed(db: SupportSQLiteDatabase, count: Int) {
        val chain = "a".repeat(64)
        val cache = "{\"public\":\"" + "a".repeat(19) + "\"}"
        val storage = "0x" + "ab".repeat(15)
            db.execSQL("INSERT INTO chains(id,name,icon,prefix,isEthereumBased,isTestNet,hasCrowdloans,supportStakingPool,isEthereumChain,isChainlinkProvider,supportNft,isUsesAppId) VALUES(?, 'Public network', 'https://public.invalid/icon',42,0,0,0,0,0,0,0,0)", arrayOf(chain))
            db.execSQL("INSERT INTO chain_assets(id,name,symbol,chainId,icon,staking,precision,purchaseProviders) VALUES('0','Public token','PUB',?,'https://public.invalid/token','unsupported',12,?)", arrayOf(chain, cache))
            db.execSQL("INSERT INTO chain_nodes VALUES(?, 'wss://public.invalid/default','Default public endpoint',0,1)", arrayOf(chain))
            db.execSQL("INSERT INTO chain_nodes VALUES(?, 'wss://public.invalid/custom','User endpoint 雪',1,0)", arrayOf(chain))
            db.execSQL("INSERT INTO favorite_chains VALUES(42,?,1)", arrayOf(chain))
            db.execSQL("INSERT INTO assets(id,chainId,accountId,metaId,sortIndex,enabled,markedNotNeed,freeInPlanks) VALUES('0',?,X'd75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a',42,19,0,0,'1')", arrayOf(chain))
            db.execSQL("INSERT INTO address_book(address,name,chainId,created,id) VALUES('public-address','Contact 雪',?,123,7)", arrayOf(chain))
            db.execSQL("INSERT INTO storage VALUES('public-storage',?,?)", arrayOf(storage, chain))
            db.execSQL("WITH RECURSIVE n(i) AS (VALUES(1) UNION ALL SELECT i+1 FROM n WHERE i < ?) INSERT INTO chain_assets(id,name,symbol,chainId,icon,staking,precision,purchaseProviders) SELECT CAST(i AS TEXT),'Public token','PUB',?,'https://public.invalid/token','unsupported',12,? FROM n", arrayOf<Any>(count - 1, chain, cache))
            assertEquals(count, number(db, "SELECT count(*) FROM chain_assets"))
    }

    private fun assertMigrated(db: SupportSQLiteDatabase, protected: List<List<List<String?>>>, count: Int) {
        assertEquals(protected, protectedRows(db))
        assertEquals(71, db.version)
        assertEquals(count, number(db, "SELECT count(*) FROM chain_assets"))
        val cache = "{\"public\":\"" + "a".repeat(19) + "\"}"
        assertEquals(0, number(db, "SELECT count(*) FROM chain_assets WHERE purchaseProviders IS NULL OR purchaseProviders != ?", arrayOf(cache)))
        assertEquals(0, db.query("PRAGMA foreign_key_check").use { it.count })
        assertEquals(1, number(db, "SELECT count(*) FROM sqlite_master WHERE name='endpoint_recovery_decisions'"))
        assertEquals(1, number(db, "SELECT count(*) FROM upgrade_entry_endpoint_archive_v1 WHERE name='User endpoint 雪' AND url='wss://public.invalid/custom'"))
    }

    private fun protectedRows(db: SupportSQLiteDatabase) = listOf(
        rows(db, "SELECT id,hex(substratePublicKey),substrateCryptoType,hex(substrateAccountId),name,isSelected,position,isBackedUp,initialized FROM meta_accounts ORDER BY id"),
        rows(db, "SELECT metaId,chainId,isFavorite FROM favorite_chains ORDER BY metaId,chainId"),
        rows(db, "SELECT id,chainId,hex(accountId),metaId,sortIndex,enabled,markedNotNeed,freeInPlanks FROM assets ORDER BY id,chainId,metaId"),
        rows(db, "SELECT address,name,chainId,created,id FROM address_book ORDER BY id")
    )
    private fun rows(db: SupportSQLiteDatabase, sql: String): List<List<String?>> = db.query(sql).use { cursor ->
        buildList { while (cursor.moveToNext()) add((0 until cursor.columnCount).map { if (cursor.isNull(it)) null else cursor.getString(it) }) }
    }
    private fun number(db: SupportSQLiteDatabase, sql: String, args: Array<out Any?> = emptyArray()): Int = db.query(sql, args).use {
        assertTrue(it.moveToFirst())
        val result = it.getInt(0)
        assertFalse(it.moveToNext())
        result
    }

    protected abstract fun fixture(
        name: String,
        mutate: (SupportSQLiteDatabase) -> Unit,
        rollback: Boolean = true,
        transaction: Boolean = true,
        check: (SupportSQLiteDatabase) -> Unit
    )
}
