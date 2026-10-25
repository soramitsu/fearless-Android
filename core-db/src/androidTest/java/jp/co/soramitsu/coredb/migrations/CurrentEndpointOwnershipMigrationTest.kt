package jp.co.soramitsu.coredb.migrations

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteException
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.common.data.secrets.v1.SecretStoreV1Impl
import jp.co.soramitsu.common.data.secrets.v2.SecretStoreV2
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecretStore
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecretStore
import jp.co.soramitsu.coredb.AppDatabase
import jp.co.soramitsu.coredb.APP_DATABASE_VERSION
import jp.co.soramitsu.testshared.HashMapEncryptedPreferences
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/** Owned disposable databases only; public synthetic values, no default app files or network. */
class CurrentEndpointOwnershipMigrationTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val names = mutableListOf<String>()
    @get:Rule val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java,
        emptyList(), FrameworkSQLiteOpenHelperFactory()
    )
    @After fun cleanup() { names.forEach(context::deleteDatabase) }

    @Test fun current77Through81KeepExactInputAcrossTwoProductionOpens() {
        for (version in 77..81) {
            val name = "current-owner-$version-${UUID.randomUUID()}".also(names::add)
            val before = helper.createDatabase(name, version).use { db ->
                db.execSQL("INSERT INTO chains(id,name,icon,prefix,isEthereumBased,isTestNet,hasCrowdloans," +
                    "supportStakingPool,isEthereumChain,isChainlinkProvider,supportNft,isUsesAppId,ecosystem) " +
                    "VALUES('parent','Public chain','icon',0,0,0,0,0,0,0,0,0,'Substrate')")
                db.execSQL("INSERT INTO meta_accounts(id,name,isSelected,position,isBackedUp,initialized) VALUES(9001,'Retained public wallet',1,0,0,1)")
                db.execSQL("INSERT INTO chain_nodes(rowid,chainId,url,name,isActive,isDefault) VALUES" +
                    "(-7,'parent','https://owner.invalid/%2f?token=PUBLIC_ONLY','  Owner 雪  ',1,0)," +
                    "(8,'parent','https://default.invalid','Catalog',0,1)")
                nodeRows(db, "chain_nodes", "rowid") to walletRows(db)
            }
            val preferences = HashMapEncryptedPreferences()
            repeat(2) {
                val room = open(name, preferences)
                try {
                    val db = room.openHelper.writableDatabase
                    assertEquals(APP_DATABASE_VERSION, db.version)
                    assertEquals(before.first, nodeRows(db, CURRENT, "source_rowid"))
                    assertEquals(before.second, walletRows(db))
                    assertEquals(listOf(listOf("81", version.toString())), db.query(
                        "SELECT source_edge_version,source_user_version FROM current_endpoint_archive_header_v1"
                    ).use(::values))
                    assertEquals(listOf(listOf("1", "1", "parent", "https://owner.invalid/%2f?token=PUBLIC_ONLY", "  Owner 雪  ", "1", "0", "-7")),
                        db.query("SELECT id,revision,chainId,url,name,attached,deleted,originalSnapshotRowId FROM custom_endpoint_records").use(::values))
                    assertEquals(listOf(listOf("https://owner.invalid/%2f?token=PUBLIC_ONLY")), db.query("SELECT url FROM chain_nodes WHERE isActive=1").use(::values))
                    db.query("PRAGMA foreign_key_list('custom_endpoint_records')").use { assertFalse(it.moveToFirst()) }
                    db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
                } finally { room.close() }
            }
        }
    }

    @Test fun earliestArchiveCannotReplaceTheLaterCurrentSnapshotOrEditedOwnership() = memory { db ->
        canonical(db)
        db.execSQL("INSERT INTO chain_nodes(rowid,chainId,url,name,isActive,isDefault) VALUES(-7,'parent','old','Old owner',1,0)")
        db.version = 31
        assertTrue(ModernEndpointArchive.capture(db, 39))
        val earliest = nodeRows(db, "endpoint_archive_v1", "source_rowid")
        val earliestMetadata = archiveMetadata(db, "endpoint_archive")
        db.execSQL("UPDATE chain_nodes SET url='edited',name='Edited 雪',isActive=0")
        db.execSQL("INSERT INTO chain_nodes(rowid,chainId,url,name,isActive,isDefault) VALUES(9,'parent','new','New owner',0,0)")
        val current = nodeRows(db, "chain_nodes", "rowid")
        migrate(db)
        assertEquals(earliest, nodeRows(db, "endpoint_archive_v1", "source_rowid"))
        assertEquals(earliestMetadata, archiveMetadata(db, "endpoint_archive"))
        assertEquals(current, nodeRows(db, CURRENT, "source_rowid"))
        assertEquals(listOf(listOf("edited", "Edited 雪"), listOf("new", "New owner")),
            db.query("SELECT url,name FROM custom_endpoint_records ORDER BY id").use(::values))
        assertEquals(31, db.version)
        assertEquals(listOf(listOf("81", "31")), db.query("SELECT source_edge_version,source_user_version FROM current_endpoint_archive_header_v1").use(::values))
    }

    @Test fun rawAnomaliesAndOrphansRemainExactWithoutUnsafeProjection() = memory { db ->
        db.execSQL("CREATE TABLE chains(id TEXT NOT NULL PRIMARY KEY)")
        db.execSQL("INSERT INTO chains VALUES('parent')")
        db.execSQL("CREATE TABLE chain_nodes(chainId,url,name,isActive,isDefault)")
        db.execSQL("INSERT INTO chain_nodes(rowid,chainId,url,name,isActive,isDefault) VALUES" +
            "(-9223372036854775808,NULL,NULL,NULL,NULL,NULL)," +
            "(-7,42,42,42,42,42),(0,1.25,1.25,1.25,1.25,1.25)," +
            "(7,'parent','malformed',CAST(X'4EFF00' AS TEXT),0,0)," +
            "(9223372036854775807,X'00FF',X'00FF',X'00FF',X'00FF',X'00FF')," +
            "(19,'parent','duplicate','first',0,0),(20,'parent','duplicate','second',1,0)," +
            "(21,'missing','orphan','Orphan 雪',1,0),(22,'parent','invalid-flag','Unknown',0,2)," +
            "(23,'parent','healthy','Healthy',0,0),(24,'parent','catalog','Default',1,1)")
        val source = nodeRows(db, "chain_nodes", "rowid")
        migrate(db)
        assertEquals(source, nodeRows(db, CURRENT, "source_rowid"))
        assertEquals(listOf(listOf("healthy"), listOf("malformed"), listOf("orphan")),
            db.query("SELECT url FROM custom_endpoint_records ORDER BY url").use(::values))
        assertEquals(listOf(listOf("0")), db.query("SELECT attached FROM custom_endpoint_records WHERE url='orphan'").use(::values))
        assertEquals("4EFF00", text(db,"SELECT hex(CAST(name AS BLOB)) FROM custom_endpoint_records WHERE url='malformed'"))
        assertEquals(0L, scalar(db,"SELECT attached FROM custom_endpoint_records WHERE url='malformed'"))
        assertEquals(listOf(listOf("catalog"), listOf("healthy")),
            db.query("SELECT url FROM chain_nodes ORDER BY url").use(::values))
        assertEquals(listOf(listOf("catalog")), db.query("SELECT url FROM chain_nodes WHERE isActive=1").use(::values))
        db.execSQL("PRAGMA foreign_keys=ON")
        db.execSQL("DELETE FROM chains")
        assertEquals(0L, scalar(db,"SELECT COUNT(*) FROM chain_nodes"))
        assertEquals(source, nodeRows(db,CURRENT,"source_rowid"))
        assertEquals(3L, scalar(db,"SELECT COUNT(*) FROM custom_endpoint_records"))
    }

    @Test fun combinedTextBeyondProjectionBudgetRemainsExactOwnedPending() = memory { db ->
        canonical(db)
        val textLimit = jp.co.soramitsu.coredb.model.chain.EndpointProjectionLimits.MAX_TEXT_BYTES
        // Every field fits the archive cell budget, but their sum cannot enter a SELECT * cursor.
        val large = "x".repeat(800_000)
        val atBoundaryName = "b".repeat((textLimit - "parent".length - "boundary".length).toInt())
        db.execSQL("INSERT INTO chain_nodes VALUES('parent',?, ?,1,0)", arrayOf(large,large))
        db.execSQL("INSERT INTO chain_nodes VALUES('parent','boundary',?,0,0)", arrayOf(atBoundaryName))
        migrate(db)
        assertEquals(2L,scalar(db,"SELECT COUNT(*) FROM current_endpoint_archive_v1"))
        assertEquals(1L,db.query("SELECT COUNT(*) FROM custom_endpoint_records WHERE attached=0 AND deleted=0 AND CAST(url AS BLOB)=? AND CAST(name AS BLOB)=?", arrayOf(large.toByteArray(),large.toByteArray())).use { check(it.moveToFirst());it.getLong(0) })
        assertEquals(1L,scalar(db,"SELECT COUNT(*) FROM chain_nodes"))
        // Exercise full native cursor materialization at the exact accepted aggregate boundary.
        db.query("SELECT * FROM chain_nodes").use { cursor ->
            assertTrue(cursor.moveToFirst());assertEquals("boundary",cursor.getString(1))
            assertEquals(atBoundaryName,cursor.getString(2));assertFalse(cursor.moveToNext())
        }
        assertEquals(1L,scalar(db,"SELECT attached FROM custom_endpoint_records WHERE url='boundary'"))
    }

    @Test fun actualRestoreFailureRollsBackEveryNewObjectAndAllowsRetry() = memory { db ->
        canonical(db)
        db.execSQL("INSERT INTO chain_nodes(rowid,chainId,url,name,isActive,isDefault) VALUES(-7,'parent','public','Owner',1,0)")
        val source = nodeRows(db,"chain_nodes","rowid"); val before = schema(db)
        var injected = false
        val wrapper = object : SupportSQLiteDatabase by db {
            override fun execSQL(sql: String) {
                if (sql.startsWith("INSERT INTO chain_nodes (")) {
                    check(CurrentEndpointArchive.requireSafe(db))
                    assertEquals(1L, scalar(db,"SELECT COUNT(*) FROM custom_endpoint_records"))
                    db.execSQL("CREATE TRIGGER injected_current_restore BEFORE INSERT ON chain_nodes BEGIN SELECT RAISE(ABORT,'CURRENT_RESTORE_FAILURE'); END")
                    injected = true
                }
                db.execSQL(sql)
            }
        }
        val error = assertThrows(SQLiteException::class.java) { migrate(wrapper) }
        assertTrue(injected); assertTrue(error.message.orEmpty().contains("CURRENT_RESTORE_FAILURE"))
        assertEquals(before,schema(db)); assertEquals(source,nodeRows(db,"chain_nodes","rowid"))
        assertFalse(db.inTransaction()); assertFalse(CurrentEndpointArchive.requireSafe(db))
        assertEquals(0L,scalar(db,"SELECT COUNT(*) FROM sqlite_temp_master"))
        migrate(db)
        assertEquals(source,nodeRows(db,CURRENT,"source_rowid"))
        assertEquals(1L,scalar(db,"SELECT COUNT(*) FROM custom_endpoint_records"))
    }

    @Test fun existingOwnershipOrCurrentSnapshotObjectsRejectWithoutOverwrite() {
        for (target in listOf("custom_endpoint_records","current_endpoint_archive_v1","current_endpoint_archive_header_v1","current_endpoint_archive_schema_v1","current_endpoint_adoption_keys_v1")) {
            for (temporary in listOf(false,true)) memory { db ->
                canonical(db)
                db.execSQL("INSERT INTO chain_nodes VALUES('parent','public','Owner',0,0)")
                db.execSQL("CREATE ${if(temporary) "TEMP " else ""}TABLE $target(value)")
                db.execSQL("INSERT INTO $target VALUES(X'00FF')")
                val before=schema(db);val source=nodeRows(db,"chain_nodes","rowid")
                assertThrows(IllegalStateException::class.java) { migrate(db) }
                assertEquals(before,schema(db));assertEquals(source,nodeRows(db,"chain_nodes","rowid"))
                assertEquals("00FF",text(db,"SELECT hex(value) FROM $target"))
            }
        }
    }

    @Test fun completeEarlierCurrentSnapshotStillCannotShortCircuitAdoption() = memory { db ->
        canonical(db);db.execSQL("INSERT INTO chain_nodes VALUES('parent','public','Old',0,0)")
        assertTrue(CurrentEndpointArchive.capture(db,81))
        db.execSQL("UPDATE chain_nodes SET name='New'")
        val source=nodeRows(db,"chain_nodes","rowid");val snapshot=nodeRows(db,CURRENT,"source_rowid")
        val before=schema(db)
        assertThrows(IllegalStateException::class.java) { migrate(db) }
        assertEquals(before,schema(db));assertEquals(source,nodeRows(db,"chain_nodes","rowid"))
        assertEquals(snapshot,nodeRows(db,CURRENT,"source_rowid"))
    }

    @Test fun absentSourceCreatesEmptyOperationalAndOwnershipTablesOnly() = memory { db ->
        db.execSQL("CREATE TABLE chains(id TEXT NOT NULL PRIMARY KEY)")
        db.execSQL("INSERT INTO chains VALUES('parent')")
        db.execSQL("CREATE TABLE unrelated(value)");db.execSQL("INSERT INTO unrelated VALUES(X'00FF')")
        migrate(db)
        assertFalse(CurrentEndpointArchive.requireSafe(db))
        assertEquals(0L,scalar(db,"SELECT COUNT(*) FROM chain_nodes"))
        assertEquals(0L,scalar(db,"SELECT COUNT(*) FROM custom_endpoint_records"))
        assertEquals("00FF",text(db,"SELECT hex(value) FROM unrelated"))
    }

    @Test fun oversizedCurrentPayloadRejectsBeforeAnyMutation() = memory { db ->
        canonical(db)
        db.execSQL("INSERT INTO chain_nodes VALUES('parent','public',CAST(zeroblob(1048577) AS TEXT),0,0)")
        val before=schema(db)
        val bytes=scalar(db,"SELECT length(CAST(name AS BLOB)) FROM chain_nodes")
        assertThrows(MigrationPayloadLimitException::class.java) { migrate(db) }
        assertEquals(before,schema(db));assertEquals(bytes,scalar(db,"SELECT length(CAST(name AS BLOB)) FROM chain_nodes"))
        assertFalse(CurrentEndpointArchive.requireSafe(db))
    }

    @Test fun successfulFutureSuffixPreviewRestoresEverySqlRowAndSchema() {
        val name="current-preview-success-${UUID.randomUUID()}".also(names::add)
        helper.createDatabase(name,77).use { db ->
            val before=sqlImage(db)
            db.beginTransaction()
            try {
                CurrentEndpointUpgradePreflight.requireSafe(db,77)
                assertTrue(db.inTransaction())
                assertEquals(before,sqlImage(db))
                db.execSQL("CREATE TABLE outer_commit_probe(value)")
                db.execSQL("INSERT INTO outer_commit_probe VALUES(X'00FF')")
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
            assertEquals("00FF",text(db,"SELECT hex(value) FROM outer_commit_probe"))
            assertFalse(CurrentEndpointArchive.requireSafe(db))
            assertEquals(77,db.version)
        }
    }

    @Test fun failedFutureOwnershipCopyRestoresOuterSqlTransactionWithoutSwallowingFailure() {
        val name="current-preview-failure-${UUID.randomUUID()}".also(names::add)
        helper.createDatabase(name,77).use { db ->
            val before=sqlImage(db)
            val expected=IllegalStateException("injected future ownership schema failure")
            val wrapper=object:SupportSQLiteDatabase by db {
                override fun execSQL(sql:String) {
                    if(sql.startsWith("CREATE TABLE `custom_endpoint_records`")) {
                        assertTrue(CurrentEndpointArchive.requireSafe(db))
                        throw expected
                    }
                    db.execSQL(sql)
                }
            }
            db.beginTransaction()
            try {
                assertSame(expected,assertThrows(IllegalStateException::class.java) {
                    CurrentEndpointUpgradePreflight.requireSafe(wrapper,77)
                })
                assertTrue(db.inTransaction());assertEquals(before,sqlImage(db))
                db.execSQL("CREATE TABLE outer_commit_probe(value)")
                db.execSQL("INSERT INTO outer_commit_probe VALUES(X'00FF')")
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
            assertEquals("00FF",text(db,"SELECT hex(value) FROM outer_commit_probe"))
            assertFalse(CurrentEndpointArchive.requireSafe(db))
            assertEquals(77,db.version)
        }
    }

    @Test fun upgradeEntrySnapshotKeepsLaterRawEditsBeforeRepairWithoutActivatingThem() {
        val name="upgrade-entry-repair-${UUID.randomUUID()}".also(names::add)
        helper.createDatabase(name,76).use { db ->
            db.execSQL("PRAGMA foreign_keys=OFF")
            db.execSQL("DROP TABLE chain_nodes")
            db.execSQL("CREATE TABLE chain_nodes(chainId,url,name,isActive,isDefault)")
            db.execSQL("INSERT INTO chain_nodes(rowid,chainId,url,name,isActive,isDefault) VALUES(-7,'missing','old','Old',0,0)")
            assertTrue(ModernEndpointArchive.capture(db,76))
            val earliest=nodeRows(db,"endpoint_archive_v1","source_rowid")
            val earliestMetadata=archiveMetadata(db,"endpoint_archive")
            db.execSQL("UPDATE chain_nodes SET url='edited',name=CAST(X'80FE00' AS TEXT),isActive=1")
            db.execSQL("INSERT INTO chain_nodes(rowid,chainId,url,name,isActive,isDefault) VALUES(9,'missing','new','Later',0,0)")
            val input=nodeRows(db,"chain_nodes","rowid")
            db.beginTransaction()
            try {
                WalletIntegritySqlPreparation.prepare(db)
                assertTrue(UpgradeEntryEndpointArchive.requireSafeForSource(db))
                assertEquals(input,nodeRows(db,"upgrade_entry_endpoint_archive_v1","source_rowid"))
                assertEquals(0L,scalar(db,"SELECT COUNT(*) FROM chain_nodes"))
                CurrentEndpointUpgradePreflight.runSqlSuffix(db,77)
                assertEquals(input,nodeRows(db,"upgrade_entry_endpoint_archive_v1","source_rowid"))
                assertEquals(earliest,nodeRows(db,"endpoint_archive_v1","source_rowid"))
                assertEquals(earliestMetadata,archiveMetadata(db,"endpoint_archive"))
                assertEquals(0L,scalar(db,"SELECT COUNT(*) FROM custom_endpoint_records"))
                assertEquals(0L,scalar(db,"SELECT COUNT(*) FROM chain_nodes"))
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
        }
    }

    @Test fun upgradeEntryNamespaceCannotBeReusedByFirstEdgeOrModernSource() = memory { db ->
        canonical(db);db.version=76
        db.execSQL("INSERT INTO chain_nodes VALUES('parent','public','First',0,0)")
        UpgradeEntryEndpointArchive.beforeDestructiveEdge(db,76)
        db.execSQL("UPDATE chain_nodes SET name='Later'")
        val before=sqlImage(db)
        assertThrows(IllegalStateException::class.java) { UpgradeEntryEndpointArchive.beforeDestructiveEdge(db,76) }
        assertEquals(before,sqlImage(db))
        db.version=77
        val modern=sqlImage(db)
        assertThrows(IllegalStateException::class.java) { migrate(db) }
        assertEquals(modern,sqlImage(db))
    }

    @Test fun laterEdgesRetainEntryAndRequireExactOriginalSourceAssociation() = memory { db ->
        canonical(db);db.version=40
        db.execSQL("INSERT INTO chain_nodes VALUES('parent','public','Original',0,0)")
        UpgradeEntryEndpointArchive.beforeDestructiveEdge(db,41)
        val entry=nodeRows(db,"upgrade_entry_endpoint_archive_v1","source_rowid")
        db.execSQL("UPDATE chain_nodes SET name='Later'")
        UpgradeEntryEndpointArchive.beforeDestructiveEdge(db,45)
        assertEquals(entry,nodeRows(db,"upgrade_entry_endpoint_archive_v1","source_rowid"))
        db.execSQL("UPDATE upgrade_entry_endpoint_archive_header_v1 SET source_user_version=41")
        val before=sqlImage(db)
        assertThrows(IllegalStateException::class.java) { UpgradeEntryEndpointArchive.beforeDestructiveEdge(db,53) }
        assertEquals(before,sqlImage(db))
    }

    private fun sqlImage(db:SupportSQLiteDatabase):Map<String,List<List<String?>>> {
        val tables=db.query("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name").use { cursor ->
            buildList { while(cursor.moveToNext()) add(cursor.getString(0)) }
        }
        return buildMap {
            put("schema",schema(db))
            for(table in tables) {
                check(table.all { it.isLetterOrDigit() || it=='_' })
                val columns=db.query("PRAGMA table_info(`$table`)").use { cursor ->
                    buildList { while(cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
                }
                val fields=columns.joinToString { column ->
                    val quoted="`${column.replace("`","``")}`"
                    "typeof($quoted),CASE WHEN typeof($quoted) IN ('text','blob') THEN hex(CAST($quoted AS BLOB)) ELSE $quoted END"
                }
                put(table,db.query("SELECT rowid,$fields FROM `$table` ORDER BY rowid").use(::values))
            }
        }
    }

    private fun open(name:String,prefs:HashMapEncryptedPreferences)=AppDatabase.create(context,name,
        SecretStoreV1Impl(prefs),SecretStoreV2(prefs),prefs,SubstrateSecretStore(prefs),EthereumSecretStore(prefs))
    private fun migrate(db:SupportSQLiteDatabase) {
        db.beginTransaction()
        try { CurrentEndpointOwnershipMigration.migrate(db);db.setTransactionSuccessful() } finally { db.endTransaction() }
    }
    private fun memory(block:(SupportSQLiteDatabase)->Unit) {
        val opener=FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null).callback(object:SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db:SupportSQLiteDatabase)=Unit
                override fun onUpgrade(db:SupportSQLiteDatabase,oldVersion:Int,newVersion:Int)=error("Unexpected upgrade")
            }).build())
        try { block(opener.writableDatabase) } finally { opener.close() }
    }
    private fun canonical(db:SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE chains(id TEXT NOT NULL PRIMARY KEY)");db.execSQL("INSERT INTO chains VALUES('parent')")
        db.execSQL("CREATE TABLE chain_nodes(chainId TEXT NOT NULL,url TEXT NOT NULL,name TEXT NOT NULL,isActive INTEGER NOT NULL,isDefault INTEGER NOT NULL,PRIMARY KEY(chainId,url),FOREIGN KEY(chainId) REFERENCES chains(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX index_chain_nodes_chainId ON chain_nodes(chainId)")
    }
    private fun nodeRows(db:SupportSQLiteDatabase,table:String,id:String):List<List<String?>> {
        check(table in listOf("chain_nodes","endpoint_archive_v1","upgrade_entry_endpoint_archive_v1",CURRENT)&&id in listOf("rowid","source_rowid"))
        return db.query("SELECT $id,"+FIELDS.joinToString { "typeof($it),CASE WHEN typeof($it) IN ('text','blob') THEN hex(CAST($it AS BLOB)) ELSE $it END" }+" FROM main.$table ORDER BY $id").use(::values)
    }
    private fun walletRows(db:SupportSQLiteDatabase)=db.query("SELECT * FROM meta_accounts ORDER BY id").use(::values)
    private fun archiveMetadata(db:SupportSQLiteDatabase,prefix:String):List<List<String?>> {
        check(prefix=="endpoint_archive")
        return db.query("SELECT * FROM endpoint_archive_header_v1").use(::values)+db.query("SELECT * FROM endpoint_archive_schema_v1 ORDER BY source_schema_rowid").use(::values)
    }
    private fun schema(db:SupportSQLiteDatabase)=db.query("SELECT 'main',type,name,tbl_name,sql FROM sqlite_master UNION ALL SELECT 'temp',type,name,tbl_name,sql FROM sqlite_temp_master ORDER BY 1,2,3").use(::values)
    private fun values(cursor:Cursor):List<List<String?>> = buildList {
        while(cursor.moveToNext()) add((0 until cursor.columnCount).map { index->when(cursor.getType(index)) {
            Cursor.FIELD_TYPE_NULL->null
            Cursor.FIELD_TYPE_INTEGER->cursor.getLong(index).toString()
            Cursor.FIELD_TYPE_FLOAT->cursor.getDouble(index).toRawBits().toString()
            Cursor.FIELD_TYPE_BLOB->cursor.getBlob(index).joinToString("") { "%02x".format(it.toInt() and 255) }
            else->cursor.getString(index)
        }})
    }
    private fun scalar(db:SupportSQLiteDatabase,sql:String):Long=db.query(sql).use { assertTrue(it.moveToFirst());it.getLong(0) }
    private fun text(db:SupportSQLiteDatabase,sql:String):String=db.query(sql).use { assertTrue(it.moveToFirst());it.getString(0) }
    private companion object {
        const val CURRENT="current_endpoint_archive_v1"
        val FIELDS=listOf("chainId","url","name","isActive","isDefault")
    }
}
