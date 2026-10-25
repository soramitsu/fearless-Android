package jp.co.soramitsu.coredb.migrations

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import jp.co.soramitsu.coredb.model.chain.CustomEndpointLocal
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*

class CurrentEndpointOwnershipMigrationTest {
    @Test fun `strict UTF8 validates exact text without replacing invalid bytes`() {
        for (value in listOf("", "ASCII", "雪 e\u0301 \uD83D\uDE00", "a".repeat(8192) + "雪", "x".repeat(1_048_576))) {
            assertTrue(CurrentEndpointTextAdmission.isExactUtf8(value.toByteArray(Charsets.UTF_8)))
        }
        for (bytes in listOf(byteArrayOf(0xff.toByte()), byteArrayOf(0xc0.toByte(),0x80.toByte()),
            byteArrayOf(0xe9.toByte(),0x9b.toByte()), byteArrayOf(0xed.toByte(),0xa0.toByte(),0x80.toByte()),
            "a".repeat(4095).toByteArray()+byteArrayOf(0xf4.toByte(),0x90.toByte(),0x80.toByte(),0x80.toByte()))) {
            assertFalse(CurrentEndpointTextAdmission.isExactUtf8(bytes))
        }
        assertThrows(IllegalArgumentException::class.java) { CurrentEndpointTextAdmission.isExactUtf8(ByteArray(1_048_577)) }
    }

    @Test fun `empty text admission reads only bounded scalar identifiers and performs no updates`() {
        val empty=mock<Cursor> { on { moveToNext() } doReturn false }
        val db=mock<SupportSQLiteDatabase> { on { query(any<String>(),any<Array<Any?>>()) } doReturn empty }
        CurrentEndpointTextAdmission.markOrdinaryUtf8(db)
        verify(db).query(argThat<String> { contains("SELECT s.source_rowid") && contains("LIMIT 32") },any<Array<Any?>>())
        verify(db,never()).execSQL(any<String>(),any<Array<Any?>>())
        verify(db,never()).execSQL(any<String>())
    }

    @Test fun `migration refuses to run outside caller transaction before reading source`() {
        val db=mock<SupportSQLiteDatabase>()
        assertThrows(IllegalStateException::class.java) { CurrentEndpointOwnershipMigration.migrate(db) }
        verify(db).inTransaction();verifyNoMoreInteractions(db)
    }

    @Test fun `reserved target fails before snapshots and mutations`() {
        val row=mock<Cursor> { on { moveToFirst() } doReturn true }
        val db=mock<SupportSQLiteDatabase> {
            on { inTransaction() } doReturn true
            on { query(any<String>()) } doReturn row
        }
        assertThrows(IllegalStateException::class.java) { CurrentEndpointOwnershipMigration.migrate(db) }
        verify(db,never()).execSQL(any<String>())
        verify(db,never()).execSQL(any<String>(),any<Array<Any?>>())
        verify(db,times(1)).query(any<String>())
    }

    @Test fun `missing cache creates only fresh empty ownership and projection`() {
        val db=mock<SupportSQLiteDatabase> {
            on { inTransaction() } doReturn true
            on { query(any<String>()) } doAnswer { mock<Cursor> { on { moveToFirst() } doReturn false; on { moveToNext() } doReturn false } }
        }
        CurrentEndpointOwnershipMigration.migrate(db)
        val sql=argumentCaptor<String>();verify(db,times(4)).execSQL(sql.capture())
        assertTrue(sql.allValues.all { it.startsWith("CREATE ") })
        assertTrue(sql.allValues.any { it.contains("custom_endpoint_records") })
        assertTrue(sql.allValues.any { it.contains("chain_nodes") })
        verify(db,never()).execSQL(any<String>(),any<Array<Any?>>())
    }

    @Test fun `current facade admits same raw grammar but only the current edge`() {
        val source=listOf(ModernEndpointSchemaRow(1,"table","chain_nodes","chain_nodes","CREATE TABLE chain_nodes(chainId,url,name,isActive,isDefault)"))
        assertFalse(CurrentEndpointArchive.requireSupportedSourceSchema(source))
        assertEquals(setOf("current_endpoint_archive_v1","current_endpoint_archive_header_v1","current_endpoint_archive_schema_v1"),
            CurrentEndpointArchive.schemaObjects.map { it.name }.toSet())
        val db=mock<SupportSQLiteDatabase>()
        assertThrows(IllegalArgumentException::class.java) { CurrentEndpointArchive.capture(db,76) }
        verifyNoInteractions(db)
    }

    @Test fun `current capture absent source balances transaction and performs no writes`() {
        val db=mock<SupportSQLiteDatabase> {
            on { query(any<String>()) } doAnswer { mock<Cursor> { on { moveToFirst() } doReturn false; on { moveToNext() } doReturn false } }
        }
        assertFalse(CurrentEndpointArchive.requireSafe(db))
        assertFalse(CurrentEndpointArchive.capture(db,81))
        verify(db).beginTransaction();verify(db).setTransactionSuccessful();verify(db).endTransaction()
        verify(db,never()).execSQL(any<String>())
    }

    @Test fun `invalid preview entry never touches the connection`() {
        val db=mock<SupportSQLiteDatabase>()
        assertThrows(IllegalArgumentException::class.java) { CurrentEndpointUpgradePreflight.requireSafe(db,76) }
        assertThrows(IllegalArgumentException::class.java) { CurrentEndpointUpgradePreflight.runSqlSuffix(db,76) }
        verifyNoInteractions(db)
    }

    @Test fun `valid preview requires a transaction before any schema operation`() {
        val db=mock<SupportSQLiteDatabase>()
        assertThrows(IllegalStateException::class.java) { CurrentEndpointUpgradePreflight.requireSafe(db,72) }
        verify(db).inTransaction();verifyNoMoreInteractions(db)
    }

    @Test fun `TON shared SQL phase keeps public schema operation order`() {
        val db=mock<SupportSQLiteDatabase>()
        TonSchemaMigration.migrate(db)
        val statements=argumentCaptor<String>();verify(db,atLeastOnce()).execSQL(statements.capture())
        val sql=statements.allValues
        assertTrue(sql.first().contains("_ton_migration_chain_accounts"))
        assertTrue(sql.last().contains("CREATE TABLE IF NOT EXISTS `token_price`"))
        assertEquals(1,sql.count { it.contains("ALTER TABLE chains ADD COLUMN `ecosystem`") })
        assertEquals(1,sql.count { it.contains("ADD COLUMN `androidMinAppVersion`") })
        assertTrue(sql.any { it.contains("`tonPublicKey` BLOB") })
        verify(db,never()).query(any<String>())
    }

    @Test fun `wallet SQL preparation propagates capture failure before repair`() {
        val failure=IllegalStateException("capture failed")
        val db=mock<SupportSQLiteDatabase> {
            on { inTransaction() } doReturn true
            on { query(any<String>()) } doAnswer { throw failure }
        }
        assertSame(failure,assertThrows(IllegalStateException::class.java) { WalletIntegritySqlPreparation.prepare(db) })
        verify(db,times(1)).query(any<String>())
        verify(db,never()).execSQL(any<String>())
        verify(db,never()).execSQL(any<String>(),any<Array<Any?>>())
    }

    @Test fun `owned metadata string representation excludes credential-bearing values`() {
        val record=CustomEndpointLocal(id=7,revision=3,chainId="private-chain",url="https://private.invalid/?token=SECRET",name="Private label",attached=false)
        assertEquals("CustomEndpointLocal(id=7, revision=3, attached=false, deleted=false)",record.toString())
        assertFalse(record.toString().contains(record.url))
    }
}
