package jp.co.soramitsu.coredb.migrations

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.*
import org.junit.Test

/** SQL-only migration contracts over the frozen shipped3.7.4 database and public wallet42. */
abstract class TonUpgradeSqlPreparationContract {
    @Test fun healthyShippedSchemaRetainsWalletAndUsesVersion71ForeignKeys() = fixture("healthy") { db ->
        val report = TonUpgradeSqlPreparation.prepare(db)
        assertFalse(report.changedSchema)
        assertEquals(emptyMap<String, Int>(), report.danglingCacheRowsDeleted)
        TonUpgradeSqlPreflight.requireSafeToMutate(db)
        assertEquals("CASCADE", text(db, "SELECT on_delete FROM pragma_foreign_key_list('assets')"))
        assertPublicWallet(db)
    }

    @Test fun missingNodeIndexRepairsAndCompletesSqlSuffix() = fixture("index", {
        it.execSQL("DROP INDEX index_chain_nodes_chainId")
    }) { db ->
        assertEquals(listOf("chain_nodes"), TonUpgradeSqlPreparation.prepare(db).reindexed)
        TonUpgradeSqlPreflight.requireSafeToMutate(db)
        CurrentEndpointUpgradePreflight.runSqlSuffix(db, 71)
        assertPublicWallet(db)
        assertEquals(71, db.version) // Room owns the eventual version assignment.
        assertEquals(1, number(db, "SELECT count(*) FROM sqlite_master WHERE type='table' AND name='endpoint_recovery_decisions'"))
    }

    @Test fun extraStorageColumnPreservesKnownCacheValues() = fixture("extra-column", {
        it.execSQL("INSERT INTO storage VALUES ('public-key','public cached value 雪','network')")
        it.execSQL("ALTER TABLE storage ADD COLUMN public_cache_metadata TEXT")
    }) { db ->
        assertEquals(listOf("storage"), TonUpgradeSqlPreparation.prepare(db).rebuilt)
        TonUpgradeSqlPreflight.requireSafeToMutate(db)
        assertEquals("public cached value 雪", text(db, "SELECT content FROM storage WHERE storageKey='public-key'"))
        assertEquals(3, number(db, "SELECT count(*) FROM pragma_table_info('storage')"))
        assertPublicWallet(db)
    }

    @Test fun missingCacheTableIsRecreated() = fixture("missing-table", {
        it.execSQL("DROP TABLE storage")
    }) { db ->
        assertEquals(listOf("storage"), TonUpgradeSqlPreparation.prepare(db).created)
        TonUpgradeSqlPreflight.requireSafeToMutate(db)
        assertPublicWallet(db)
    }

    @Test fun orphanCustomEndpointIsArchivedBeforeCacheDeletion() = fixture("orphan-endpoint", {
        it.execSQL("INSERT INTO chain_nodes VALUES ('retired-network','https://public.invalid/rpc','My node 雪',1,0)")
    }) { db ->
        val report = TonUpgradeSqlPreparation.prepare(db)
        assertEquals(1, report.danglingCacheRowsDeleted["chain_nodes"])
        for (prefix in listOf("endpoint", "upgrade_entry_endpoint")) {
            assertEquals("My node 雪", text(db, "SELECT name FROM ${prefix}_archive_v1"))
            assertEquals(71, number(db, "SELECT source_edge_version FROM ${prefix}_archive_header_v1"))
            assertEquals(71, number(db, "SELECT source_user_version FROM ${prefix}_archive_header_v1"))
        }
        TonUpgradeSqlPreflight.requireSafeToMutate(db)
        CurrentEndpointUpgradePreflight.runSqlSuffix(db, 71)
        assertEquals("My node 雪", text(db, "SELECT name FROM upgrade_entry_endpoint_archive_v1"))
        assertPublicWallet(db)
    }

    @Test fun userFavoritesSurviveMissingRegistryParentAndCacheRepair() = fixture("favorite", {
        it.execSQL("INSERT INTO favorite_chains VALUES (42,'retired-network',1)")
        it.execSQL("ALTER TABLE favorite_chains ADD COLUMN old_hint TEXT")
    }) { db ->
        assertEquals(listOf("favorite_chains"), TonUpgradeSqlPreparation.prepare(db).rebuilt)
        TonUpgradeSqlPreflight.requireSafeToMutate(db)
        CurrentEndpointUpgradePreflight.runSqlSuffix(db, 71)
        assertEquals(1, number(db, "SELECT isFavorite FROM favorite_chains WHERE metaId=42 AND chainId='retired-network'"))
        assertPublicWallet(db)
    }

    @Test fun lateProtectedSchemaRejectionRollsBackCacheRepairAndArchives() = fixture("protected-rollback", {
        it.execSQL("ALTER TABLE storage ADD COLUMN cache_extra TEXT")
        it.execSQL("DROP INDEX index_meta_accounts_substrateAccountId")
    }, rollback = true) { db ->
        assertEquals(listOf("storage"), TonUpgradeSqlPreparation.prepare(db).rebuilt)
        assertThrows(TonUpgradeSqlIntegrityException::class.java) { TonUpgradeSqlPreflight.requireSafeToMutate(db) }
        assertPublicWallet(db)
    }

    @Test fun unsupportedNodeTriggerRejectsBeforeRepairAndRollsBack() = fixture("trigger-rollback", {
        it.execSQL("ALTER TABLE storage ADD COLUMN cache_extra TEXT")
        it.execSQL("CREATE TRIGGER unexpected_node_hook AFTER INSERT ON chain_nodes BEGIN UPDATE meta_accounts SET name='unexpected'; END")
    }, rollback = true) { db ->
        assertThrows(IllegalStateException::class.java) { TonUpgradeSqlPreparation.prepare(db) }
        assertEquals(4, number(db, "SELECT count(*) FROM pragma_table_info('storage')"))
        assertPublicWallet(db)
    }

    @Test fun preparationRequiresTransactionBeforeAnyWrite() = fixture("transaction", transaction = false) { db ->
        assertThrows(IllegalStateException::class.java) { TonUpgradeSqlPreparation.prepare(db) }
        assertEquals(0, number(db, "SELECT count(*) FROM sqlite_master WHERE name LIKE '%endpoint_archive%v1'"))
        assertPublicWallet(db)
    }

    @Test fun successfulSqlPreviewRollsBackEveryDatabaseByte() = fixture("suffix-rollback", {
        it.execSQL("ALTER TABLE storage ADD COLUMN cache_extra TEXT")
        it.execSQL("INSERT INTO chain_nodes VALUES ('retired','https://public.invalid','Public custom',1,0)")
    }, rollback = true) { db ->
        TonUpgradeSqlPreparation.prepare(db)
        TonUpgradeSqlPreflight.requireSafeToMutate(db)
        CurrentEndpointUpgradePreflight.runSqlSuffix(db, 71)
        assertPublicWallet(db)
        assertEquals("Public custom", text(db, "SELECT name FROM upgrade_entry_endpoint_archive_v1"))
    }

    @Test fun oldCompletedArchiveRemainsAdmittedByInventoryReader() = fixture("old-archive") { db ->
        EndpointArchiveFormat.UPGRADE_ENTRY.capture(db, 76)
        assertThrows(IllegalStateException::class.java) { UpgradeEntryEndpointArchive.requireSafeForSource(db) }
        db.version = 83
        val session = jp.co.soramitsu.coredb.recovery.admitEndpointSource(
            db, android.os.CancellationSignal(), jp.co.soramitsu.coredb.recovery.EndpointInventorySource.UPGRADE_ENTRY
        )
        assertNotNull(session)
        assertPublicWallet(db)
    }

    @Test fun everyRebuildableTableRecreatesTheShippedContract() {
        val expected = setOf("address_book", "assets", "token_price", "phishing", "storage",
            "account_staking_accesses", "total_reward", "operations", "chains", "chain_nodes",
            "chain_assets", "favorite_chains", "chain_runtimes", "chain_explorers", "chain_types",
            "nomis_wallet_score", "allpools", "userpools")
        assertEquals(expected, RELEASED_V71_REBUILDABLE_TABLES.map { it.contract.name }.toSet())
        for (name in expected) fixture("recreate-$name", { it.execSQL("DROP TABLE `$name`") }) { db ->
            assertEquals(listOf(name), TonUpgradeSqlPreparation.prepare(db).created)
            TonUpgradeSqlPreflight.requireSafeToMutate(db)
            assertPublicWallet(db)
        }
    }

    @Test fun missingRequiredContactValueRetainsUserRowByColumnName() = fixture("contact", {
        it.execSQL("DROP TABLE address_book")
        it.execSQL("CREATE TABLE address_book (address TEXT, name TEXT, chainId TEXT, id INTEGER PRIMARY KEY AUTOINCREMENT)")
        it.execSQL("INSERT INTO address_book VALUES ('public-contact','My contact 雪','network',7)")
    }) { db ->
        assertEquals(listOf("address_book"), TonUpgradeSqlPreparation.prepare(db).rebuilt)
        TonUpgradeSqlPreflight.requireSafeToMutate(db)
        assertEquals("My contact 雪", text(db, "SELECT name FROM address_book WHERE id=7"))
        assertEquals("public-contact", text(db, "SELECT address FROM address_book WHERE id=7"))
        assertEquals(0, number(db, "SELECT created FROM address_book WHERE id=7"))
        assertPublicWallet(db)
    }

    @Test fun storedOldCaptureRemainsReadableWithoutWeakeningNewFirstEdge() {
        for (version in 54..71) {
            assertEquals(71, UpgradeEntryEndpointArchive.firstEdge(version))
            assertTrue(UpgradeEntryEndpointArchive.admitsStoredCapture(version, 71))
            assertTrue(UpgradeEntryEndpointArchive.admitsStoredCapture(version, 76))
            assertFalse(UpgradeEntryEndpointArchive.admitsStoredCapture(version, 53))
        }
        assertFalse(UpgradeEntryEndpointArchive.admitsStoredCapture(53, 76))
        assertFalse(UpgradeEntryEndpointArchive.admitsStoredCapture(72, 71))
        assertTrue(UpgradeEntryEndpointArchive.admitsStoredCapture(72, 76))
        assertFalse(UpgradeEntryEndpointArchive.admitsStoredCapture(77, 76))
    }

    @Test fun duplicateLegacyContactIdsRejectVersion71RepairWithoutDiscardingEitherContact() =
        fixture("duplicate-contact-v71", { duplicateContactIds(it) }, rollback = true) { db ->
            val failure = runCatching { TonUpgradeSqlPreparation.prepare(db) }.exceptionOrNull()
            assertContactCollision(failure, db)
            assertPublicWallet(db)
        }

    @Test fun duplicateLegacyContactIdsRejectDefaultVersion76RepairWithoutDiscardingEitherContact() =
        fixture("duplicate-contact-v76", { db ->
            // Build the actual SQL-only71-to76 cohort; Room keeps original user_version until commit.
            db.beginTransaction()
            try {
                TonSchemaMigration.migrate(db)
                Migration_72_73.migrate(db)
                Migration_73_74.migrate(db)
                Migration_74_75.migrate(db)
                Migration_75_76.migrate(db)
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
            duplicateContactIds(db)
        }, rollback = true) { db ->
            val failure = runCatching { RebuildableTableRepair.repair(db) }.exceptionOrNull()
            assertContactCollision(failure, db)
            assertPublicWallet(db)
        }

    @Test fun duplicateCacheKeysKeepTheExistingIgnorePolicy() = fixture("duplicate-cache", { db ->
        db.execSQL("DROP TABLE storage")
        db.execSQL("CREATE TABLE storage(storageKey TEXT,content TEXT,chainId TEXT)")
        db.execSQL("INSERT INTO storage VALUES('public-key','cached-first','public-chain'),('public-key','cached-second','public-chain')")
    }) { db ->
        assertEquals(listOf("storage"), TonUpgradeSqlPreparation.prepare(db).rebuilt)
        assertEquals(1, number(db, "SELECT count(*) FROM storage"))
        TonUpgradeSqlPreflight.requireSafeToMutate(db)
        assertPublicWallet(db)
    }

    @Test fun copiedAssetRegistryCacheAboveOldCellLimitPreservesTheSqlSuffix() = assertCacheAtAndAboveCellLimit("chain_assets")
    @Test fun copiedPriceCacheAboveOldCellLimitPreservesTheSqlSuffix() = assertCacheAtAndAboveCellLimit("token_price")
    @Test fun copiedWalletScoreCacheAboveOldCellLimitPreservesTheSqlSuffix() = assertCacheAtAndAboveCellLimit("nomis_wallet_score")
    @Test fun copiedChainCacheAboveOldCellLimitPreservesTheSqlSuffix() = assertCacheAtAndAboveCellLimit("chains")

    private fun assertCacheAtAndAboveCellLimit(table: String) {
        val column = copiedCacheColumns.getValue(table)
        listOf(1_048_576, 1_048_578).forEach { size ->
            val payload = cachePayload(table, size)
            fixture("cache-byte-$table-$size", {
                insertCacheAndUserControls(it)
                it.execSQL("UPDATE `$table` SET `$column`=?", arrayOf(payload))
            }, rollback = true) { db ->
                assertCacheSqlSuffix(db, mapOf(table to payload))
            }
        }
    }

    @Test fun copiedCachesDoNotConsumeInjectedCellRowOrCumulativeBudgets() {
        copiedCacheColumns.forEach { (table, column) ->
            val payload = cachePayload(table, 8192)
            fixture("cache-injected-budget-$table", {
                insertCacheAndUserControls(it)
                it.execSQL("UPDATE `$table` SET `$column`=?", arrayOf(payload))
            }, rollback = true) { db ->
                assertCacheSqlSuffix(db, mapOf(table to payload), TonUpgradePayloadLimits(256, 512, 4096))
            }
        }
    }

    @Test fun copiedAssetCacheAboveOldRowBudgetRetainsEveryByte() {
        val payload = cachePayload("chain_assets", 5_242_880)
        fixture("cache-row-five-mib", {
            insertCacheAndUserControls(it)
            it.execSQL("UPDATE chain_assets SET purchaseProviders=?", arrayOf(payload))
        }, rollback = true) { db -> assertCacheSqlSuffix(db, mapOf("chain_assets" to payload)) }
    }

    @Test fun copiedCacheRowCountStillUsesItsActuallyTraversedBound() = fixture("cache-row-bound", {
        insertCacheAndUserControls(it)
        it.execSQL("INSERT INTO chain_assets SELECT '1',name,symbol,chainId,icon,priceId,staking,precision,purchaseProviders,isUtility,type,currencyId,existentialDeposit,color,isNative,ethereumType,priceProvider FROM chain_assets")
    }, rollback = true) { db ->
        TonUpgradeSqlPreparation.prepare(db)
        val failure = assertThrows(MigrationPayloadLimitException::class.java) {
            TonUpgradeSqlPreflight.requireSafeToMutate(db, foreignKeyCheckLimits = TON_UPGRADE_FOREIGN_KEY_CHECK_LIMITS.copy(
                maximumRowsByTable = TON_UPGRADE_FOREIGN_KEY_CHECK_LIMITS.maximumRowsByTable + ("chain_assets" to 1),
                uncappedCacheTables = emptySet()
            ))
        }
        assertEquals("chain_assets rows exceed the safe migration limit", failure.message)
    }

    @Test fun copiedCacheExemptionDoesNotRelaxProtectedIdentityCellBudget() = fixture("cache-protected-cell", {
        insertCacheAndUserControls(it)
        it.execSQL("UPDATE meta_accounts SET name=?", arrayOf("x".repeat(1_048_577)))
    }, rollback = true) { db ->
        TonUpgradeSqlPreparation.prepare(db)
        val failure = assertThrows(MigrationPayloadLimitException::class.java) { TonUpgradeSqlPreflight.requireSafeToMutate(db) }
        assertEquals("meta_accounts.name exceeds the safe migration payload limit", failure.message)
    }

    @Test fun copiedCacheExemptionDoesNotRelaxMixedUserAssetCellBudget() = fixture("cache-mixed-cell", {
        insertCacheAndUserControls(it)
        it.execSQL("UPDATE assets SET freeInPlanks=?", arrayOf("1".repeat(1_048_577)))
    }, rollback = true) { db ->
        TonUpgradeSqlPreparation.prepare(db)
        val failure = assertThrows(MigrationPayloadLimitException::class.java) { TonUpgradeSqlPreflight.requireSafeToMutate(db) }
        assertEquals("assets.freeInPlanks exceeds the safe migration payload limit", failure.message)
    }

    @Test fun copiedCacheExemptionDoesNotRelaxProtectedRowBudget() = fixture("cache-protected-row", {
        insertCacheAndUserControls(it)
        it.execSQL("UPDATE meta_accounts SET name=?", arrayOf("x".repeat(180)))
    }, rollback = true) { db ->
        TonUpgradeSqlPreparation.prepare(db)
        val failure = assertThrows(MigrationPayloadLimitException::class.java) {
            TonUpgradeSqlPreflight.requireSafeToMutate(db, payloadLimits = TonUpgradePayloadLimits(256, 128, 4096))
        }
        assertEquals("meta_accounts row exceeds the safe migration payload limit", failure.message)
    }

    @Test fun copiedCacheExemptionDoesNotRelaxProtectedCumulativeBudget() = fixture("cache-protected-total", {
        insertCacheAndUserControls(it)
    }, rollback = true) { db ->
        TonUpgradeSqlPreparation.prepare(db)
        val failure = assertThrows(MigrationPayloadLimitException::class.java) {
            TonUpgradeSqlPreflight.requireSafeToMutate(db, payloadLimits = TonUpgradePayloadLimits.PRODUCTION.copy(maximumTotalBytes = 1))
        }
        assertTrue(failure.message.orEmpty().contains("safe cumulative migration payload limit"))
    }

    @Test fun lateSqlFailureAfterAllOversizedCacheCopiesRollsBackEveryOriginalByte() {
        val payloads = copiedCacheColumns.mapValues { (table, _) -> cachePayload(table, 1_048_578) }
        fixture("cache-late-sql-rollback", { db ->
            insertCacheAndUserControls(db)
            payloads.forEach { (table, payload) ->
                db.execSQL("UPDATE `$table` SET `${copiedCacheColumns.getValue(table)}`=?", arrayOf(payload))
            }
        }, rollback = true) { db ->
            assertCacheSqlSuffix(db, payloads)
            val failure = runCatching {
                db.execSQL("INSERT INTO address_book(address,name,chainId,created,id) VALUES('late-public','Late collision','public-chain',0,7)")
            }.exceptionOrNull()
            assertNotNull(failure)
            val messages = generateSequence(failure) { it.cause }.mapNotNull { it.message }.toList()
            assertTrue(messages.toString(), messages.any { it.contains("UNIQUE constraint failed: address_book.id") })
            assertTrue(db.inTransaction())
            assertEquals(71, db.version)
        }
    }

    private val copiedCacheColumns = linkedMapOf(
        "chain_assets" to "purchaseProviders",
        "token_price" to "fiatRate",
        "nomis_wallet_score" to "nativeBalanceUsd",
        "chains" to "icon"
    )

    private fun cachePayload(table: String, size: Int): String {
        val (prefix, suffix) = when (table) {
            "chain_assets" -> "{\"public\":\"" to "\"}"
            "chains" -> "data:image/svg+xml,<svg xmlns='http://www.w3.org/2000/svg'><!--" to "--></svg>"
            else -> "0." to "1"
        }
        return prefix + "0".repeat(size - prefix.length - suffix.length) + suffix
    }

    private fun insertCacheAndUserControls(db: SupportSQLiteDatabase) {
        val chain = "a".repeat(64)
        val key = "d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a"
        db.execSQL("INSERT INTO chains(id,name,icon,prefix,isEthereumBased,isTestNet,hasCrowdloans,supportStakingPool,isEthereumChain,isChainlinkProvider,supportNft,isUsesAppId) VALUES(?, 'Public network', 'https://public.invalid/icon',42,0,0,0,0,0,0,0,0)", arrayOf(chain))
        db.execSQL("INSERT INTO chain_assets(id,name,symbol,chainId,icon,staking,precision,purchaseProviders) VALUES('0','Public token','PUB',?,'https://public.invalid/token','unsupported',12,'{}')", arrayOf(chain))
        db.execSQL("INSERT INTO token_price VALUES('public-price','1','USD','0')")
        db.execSQL("INSERT INTO nomis_wallet_score VALUES(42,1,123,'1','0',1,1,0,1.0,1.0,1.0,'public-date')")
        db.execSQL("INSERT INTO chain_nodes VALUES(?, 'wss://public.invalid/default','Default public endpoint',0,1)", arrayOf(chain))
        db.execSQL("INSERT INTO chain_nodes VALUES(?, 'wss://public.invalid/custom','User endpoint 雪',1,0)", arrayOf(chain))
        db.execSQL("INSERT INTO chain_accounts VALUES(42,?,X'$key',X'$key','ED25519','Public chain account',1)", arrayOf(chain))
        db.execSQL("INSERT INTO favorite_chains VALUES(42,?,1)", arrayOf(chain))
        db.execSQL("INSERT INTO assets(id,chainId,accountId,metaId,sortIndex,enabled,markedNotNeed,freeInPlanks) VALUES('0',?,X'$key',42,19,0,0,'1')", arrayOf(chain))
        db.execSQL("INSERT INTO address_book(address,name,chainId,created,id) VALUES('public-address','Contact 雪',?,123,7)", arrayOf(chain))
    }

    private fun cacheProtectedRows(db: SupportSQLiteDatabase): List<List<List<String?>>> = listOf(
        "SELECT id,hex(substratePublicKey),substrateCryptoType,hex(substrateAccountId),name,isSelected,position,isBackedUp,initialized FROM meta_accounts",
        "SELECT metaId,chainId,hex(publicKey),hex(accountId),cryptoType,name,initialized FROM chain_accounts",
        "SELECT metaId,chainId,isFavorite FROM favorite_chains",
        "SELECT id,chainId,hex(accountId),metaId,sortIndex,enabled,markedNotNeed,freeInPlanks FROM assets",
        "SELECT address,name,chainId,created,id FROM address_book"
    ).map { sql -> db.query(sql).use { cursor ->
        buildList { while (cursor.moveToNext()) add((0 until cursor.columnCount).map { if (cursor.isNull(it)) null else cursor.getString(it) }) }
    } }

    private fun assertCacheSqlSuffix(
        db: SupportSQLiteDatabase,
        payloads: Map<String, String>,
        limits: TonUpgradePayloadLimits = TonUpgradePayloadLimits.PRODUCTION
    ) {
        val protected = cacheProtectedRows(db)
        TonUpgradeSqlPreparation.prepare(db)
        payloads.forEach { (table, payload) -> assertCachePayloadBytes(db, table, payload) }
        TonUpgradeSqlPreflight.requireSafeToMutate(db, payloadLimits = limits)
        CurrentEndpointUpgradePreflight.runSqlSuffix(db, 71)
        assertEquals(71, db.version)
        assertEquals(protected, cacheProtectedRows(db))
        assertEquals(0, db.query("PRAGMA foreign_key_check").use { it.count })
        assertEquals(1, number(db, "SELECT count(*) FROM upgrade_entry_endpoint_archive_v1 WHERE name='User endpoint 雪' AND url='wss://public.invalid/custom'"))
        assertEquals(1, number(db, "SELECT count(*) FROM sqlite_master WHERE name='endpoint_recovery_decisions'"))
        // The released TON SQL edge intentionally rebuilds pricing from an empty cache.
        assertEquals(0, number(db, "SELECT count(*) FROM token_price"))
        payloads.filterKeys { it != "token_price" }.forEach { (table, payload) ->
            assertCachePayloadBytes(db, table, payload)
        }
    }

    private fun assertCachePayloadBytes(db: SupportSQLiteDatabase, table: String, payload: String) {
        val column = copiedCacheColumns.getValue(table)
        assertEquals(payload.toByteArray().size, number(db, "SELECT length(CAST(`$column` AS BLOB)) FROM `$table`"))
        val expected = java.security.MessageDigest.getInstance("SHA-256").digest(payload.toByteArray())
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        for (offset in 1..payload.length step 32768) {
            db.query("SELECT substr(CAST(`$column` AS BLOB),$offset,32768) FROM `$table`").use {
                assertTrue(it.moveToFirst()); digest.update(it.getBlob(0)); assertFalse(it.moveToNext())
            }
        }
        assertArrayEquals(expected, digest.digest())
    }

    private fun duplicateContactIds(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE address_book")
        db.execSQL("CREATE TABLE address_book(address TEXT,name TEXT,chainId TEXT,created INTEGER,id INTEGER)")
        db.execSQL("INSERT INTO address_book VALUES('public-contact-A','Contact A','public-chain',1,7),('public-contact-B','Contact B','public-chain',2,7)")
    }

    private fun assertContactCollision(failure: Throwable?, db: SupportSQLiteDatabase) {
        assertNotNull("A user primary-key collision must reject repair", failure)
        // Host JDBC and Android wrap SQLite errors differently; require the exact failing constraint.
        val messages = generateSequence(failure) { it.cause }.mapNotNull { it.message }.toList()
        assertTrue(messages.toString(), messages.any { it.contains("UNIQUE constraint failed: _repair_address_book.id") })
        assertEquals(2, number(db, "SELECT count(*) FROM address_book"))
        assertEquals("Contact A", text(db, "SELECT name FROM address_book WHERE address='public-contact-A'"))
        assertEquals("Contact B", text(db, "SELECT name FROM address_book WHERE address='public-contact-B'"))
    }

    protected abstract fun fixture(
        name: String,
        mutate: (SupportSQLiteDatabase) -> Unit = {},
        rollback: Boolean = false,
        transaction: Boolean = true,
        check: (SupportSQLiteDatabase) -> Unit
    )

    private fun assertPublicWallet(db: SupportSQLiteDatabase) {
        assertEquals("Public historical fixture 雪", text(db, "SELECT name FROM meta_accounts WHERE id=42"))
        assertEquals("D75A980182B10AB7D54BFED3C964073A0EE172F3DAA62325AF021A68F707511A", text(db, "SELECT hex(substratePublicKey) FROM meta_accounts WHERE id=42"))
        assertEquals("ED25519", text(db, "SELECT substrateCryptoType FROM meta_accounts WHERE id=42"))
        assertEquals(1, number(db, "SELECT count(*) FROM meta_accounts"))
    }
    private fun text(db: SupportSQLiteDatabase, sql: String): String = db.query(sql).use {
        assertTrue(it.moveToFirst()); val value = it.getString(0); assertFalse(it.moveToNext()); value
    }
    private fun number(db: SupportSQLiteDatabase, sql: String): Int = db.query(sql).use {
        assertTrue(it.moveToFirst()); val value = it.getInt(0); assertFalse(it.moveToNext()); value
    }
}
