package jp.co.soramitsu.coredb.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.co.soramitsu.coredb.AppDatabase
import jp.co.soramitsu.coredb.model.ConnectionSource
import jp.co.soramitsu.coredb.model.MetaAccountLocal
import jp.co.soramitsu.coredb.model.TonConnectionReadProjection
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TonConnectDaoTest : DaoTest<TonConnectDao>(
    AppDatabase::tonConnectDao
) {

    @Before
    fun insertWallet() {
        runBlocking {
            db.metaAccountDao().insertMetaAccount(
                MetaAccountLocal(
                    substratePublicKey = null,
                    substrateCryptoType = null,
                    substrateAccountId = null,
                    ethereumPublicKey = null,
                    ethereumAddress = null,
                    tonPublicKey = null,
                    name = "Wallet",
                    isSelected = true,
                    position = 0,
                    isBackedUp = false,
                    googleBackupAddress = null,
                    initialized = true
                ).apply {
                    id = META_ID
                }
            )
        }
    }

    @Test
    fun oversizedTextNeverLeavesSqlProjection() = runBlocking {
        insertRaw(
            clientId = HOSTILE_TEXT,
            name = "client",
            icon = "icon",
            url = CLIENT_URL
        )
        insertRaw(
            clientId = NAME_CLIENT_ID,
            name = HOSTILE_TEXT,
            icon = "icon",
            url = NAME_URL
        )
        insertRaw(
            clientId = ICON_CLIENT_ID,
            name = "icon",
            icon = HOSTILE_TEXT,
            url = ICON_URL
        )
        insertRaw(
            clientId = URL_CLIENT_ID,
            name = "url",
            icon = "icon",
            url = HOSTILE_URL
        )

        val listed = dao.getTonConnections(META_ID, ConnectionSource.QR)
        val observed = dao.observeTonConnections(
            META_ID,
            ConnectionSource.QR
        ).first()

        assertEquals(4, listed.size)
        assertCursorSafe(listed)
        assertCursorSafe(observed)
        assertEquals(
            "",
            listed.single { it.url == CLIENT_URL }.clientId
        )
        assertEquals(
            "",
            listed.single { it.url == NAME_URL }.name
        )
        assertEquals(
            "",
            listed.single { it.url == ICON_URL }.icon
        )
        assertEquals(
            "",
            listed.single { it.clientId == URL_CLIENT_ID }.url
        )

        val exact = checkNotNull(
            dao.getTonConnection(
                META_ID,
                NAME_URL,
                ConnectionSource.QR
            )
        )
        assertFalse(exact.rowWithinBounds)
        assertEquals("", exact.name)

        val owners = dao.getTonConnectionsByClientId(ICON_CLIENT_ID)
        assertEquals(1, owners.size)
        assertFalse(owners.single().rowWithinBounds)
        assertEquals("", owners.single().icon)
    }

    @Test
    fun multibyteClientIdOverflowNeverLeavesSqlProjection() = runBlocking {
        val multibyteClientId = "\uD83D\uDD10".repeat(17)
        insertRaw(
            clientId = multibyteClientId,
            name = "multibyte client",
            icon = "icon",
            url = MULTIBYTE_CLIENT_URL
        )

        val projected = dao.getTonConnections(
            META_ID,
            ConnectionSource.QR
        ).single()

        assertTrue(multibyteClientId.length <= 64)
        assertTrue(multibyteClientId.toByteArray().size > 64)
        assertFalse(projected.rowWithinBounds)
        assertEquals("", projected.clientId)
        assertEquals(
            multibyteClientId.toByteArray().size.toLong(),
            projected.clientIdUtf8Bytes
        )
    }

    @Test
    fun blobAndIntegerTextStorageNeverLeavesSqlProjection() = runBlocking {
        recreateTonConnectionTableWithBlobNameAffinity()
        insertRaw(
            clientId = byteArrayOf(1, 2, 3),
            name = "BLOB client id",
            icon = "icon",
            url = BLOB_CLIENT_URL
        )
        insertRaw(
            clientId = INTEGER_NAME_CLIENT_ID,
            name = 42L,
            icon = "icon",
            url = INTEGER_NAME_URL
        )

        val projected = dao.getTonConnections(
            META_ID,
            ConnectionSource.QR
        )
        val blobClient = projected.single { it.url == BLOB_CLIENT_URL }
        val integerName = projected.single { it.url == INTEGER_NAME_URL }

        assertFalse(blobClient.rowWithinBounds)
        assertEquals("", blobClient.clientId)
        assertEquals(-1L, blobClient.clientIdUtf8Bytes)
        assertFalse(integerName.rowWithinBounds)
        assertEquals("", integerName.name)
        assertEquals(-1L, integerName.nameUtf8Bytes)
    }

    @Test
    fun invalidMetaIdsAndSourceFailClosedInSqlProjection() = runBlocking {
        recreateTonConnectionTableWithoutForeignKey()
        insertRaw(
            clientId = ZERO_META_CLIENT_ID,
            name = "zero meta id",
            icon = "icon",
            url = ZERO_META_URL,
            metaId = 0L
        )
        insertRaw(
            clientId = NEGATIVE_META_CLIENT_ID,
            name = "negative meta id",
            icon = "icon",
            url = NEGATIVE_META_URL,
            metaId = -1L
        )
        insertRaw(
            clientId = TEXT_META_CLIENT_ID,
            name = "text meta id",
            icon = "icon",
            url = TEXT_META_URL,
            metaId = "not-an-integer"
        )
        insertRaw(
            clientId = INVALID_SOURCE_CLIENT_ID,
            name = "invalid source",
            icon = "icon",
            url = INVALID_SOURCE_URL,
            source = "INVALID"
        )

        listOf(
            ZERO_META_CLIENT_ID,
            NEGATIVE_META_CLIENT_ID,
            TEXT_META_CLIENT_ID
        ).forEach { clientId ->
            val projected = dao.getTonConnectionsByClientId(
                clientId
            ).single()
            assertFalse(projected.rowWithinBounds)
            assertEquals(0L, projected.metaId)
        }

        val invalidSource = dao.getTonConnectionsByClientId(
            INVALID_SOURCE_CLIENT_ID
        ).single()
        assertFalse(invalidSource.rowWithinBounds)
        assertEquals("", invalidSource.source)
    }

    @Test
    fun exactTextLimitsRemainAvailableInSqlProjection() = runBlocking {
        val exactClientId = "c".repeat(64)
        val exactName = "n".repeat(512)
        val exactIcon = "i".repeat(4_096)
        val exactUrl = "u".repeat(4_096)
        insertRaw(
            clientId = exactClientId,
            name = exactName,
            icon = exactIcon,
            url = exactUrl
        )

        val projected = dao.getTonConnections(
            META_ID,
            ConnectionSource.QR
        ).single()

        assertTrue(projected.rowWithinBounds)
        assertEquals(exactClientId, projected.clientId)
        assertEquals(exactName, projected.name)
        assertEquals(exactIcon, projected.icon)
        assertEquals(exactUrl, projected.url)
        assertEquals(64L, projected.clientIdUtf8Bytes)
        assertEquals(512L, projected.nameUtf8Bytes)
        assertEquals(4_096L, projected.iconUtf8Bytes)
        assertEquals(4_096L, projected.urlUtf8Bytes)
    }

    @Test
    fun listProjectionReturnsThe257RowCorruptionSentinel() = runBlocking {
        db.runInTransaction {
            repeat(258) { index ->
                insertRaw(
                    clientId = "client-$index",
                    name = "connection $index",
                    icon = "icon",
                    url = "https://example.com/limit/$index"
                )
            }
        }

        val projected = dao.getTonConnections(
            META_ID,
            ConnectionSource.QR
        )

        assertEquals(257, projected.size)
        assertTrue(projected.all { it.rowWithinBounds })
    }

    private fun assertCursorSafe(
        rows: List<TonConnectionReadProjection>
    ) {
        assertTrue(rows.all { !it.rowWithinBounds })
        assertTrue(rows.all { it.clientId.length <= 64 })
        assertTrue(rows.all { it.name.length <= 512 })
        assertTrue(rows.all { it.icon.length <= 4_096 })
        assertTrue(rows.all { it.url.length <= 4_096 })
        assertTrue(rows.all { it.source.length <= 3 })
        assertTrue(rows.none { HOSTILE_TEXT in it.clientId })
        assertTrue(rows.none { HOSTILE_TEXT in it.name })
        assertTrue(rows.none { HOSTILE_TEXT in it.icon })
        assertTrue(rows.none { HOSTILE_TEXT in it.url })
    }

    private fun insertRaw(
        clientId: Any,
        name: Any,
        icon: Any,
        url: Any,
        metaId: Any = META_ID,
        source: Any = ConnectionSource.QR.name
    ) {
        db.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO ton_connection(
                metaId,
                clientId,
                name,
                icon,
                url,
                source
            ) VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf(
                metaId,
                clientId,
                name,
                icon,
                url,
                source
            )
        )
    }

    private fun recreateTonConnectionTableWithBlobNameAffinity() {
        db.openHelper.writableDatabase.apply {
            execSQL("DROP TABLE ton_connection")
            execSQL(
                """
                CREATE TABLE ton_connection(
                    metaId INTEGER NOT NULL,
                    clientId TEXT NOT NULL,
                    name BLOB NOT NULL,
                    icon TEXT NOT NULL,
                    url TEXT NOT NULL,
                    source TEXT NOT NULL,
                    PRIMARY KEY(metaId, url, source),
                    FOREIGN KEY(metaId) REFERENCES meta_accounts(id)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
        }
    }

    private fun recreateTonConnectionTableWithoutForeignKey() {
        db.openHelper.writableDatabase.apply {
            execSQL("DROP TABLE ton_connection")
            execSQL(
                """
                CREATE TABLE ton_connection(
                    metaId INTEGER NOT NULL,
                    clientId TEXT NOT NULL,
                    name TEXT NOT NULL,
                    icon TEXT NOT NULL,
                    url TEXT NOT NULL,
                    source TEXT NOT NULL,
                    PRIMARY KEY(metaId, url, source)
                )
                """.trimIndent()
            )
        }
    }

    private companion object {
        const val META_ID = 1L
        const val CLIENT_URL = "https://example.com/client"
        const val NAME_URL = "https://example.com/name"
        const val ICON_URL = "https://example.com/icon"
        const val MULTIBYTE_CLIENT_URL =
            "https://example.com/multibyte-client"
        const val BLOB_CLIENT_URL =
            "https://example.com/blob-client"
        const val INTEGER_NAME_URL =
            "https://example.com/integer-name"
        const val ZERO_META_URL =
            "https://example.com/zero-meta"
        const val NEGATIVE_META_URL =
            "https://example.com/negative-meta"
        const val TEXT_META_URL =
            "https://example.com/text-meta"
        const val INVALID_SOURCE_URL =
            "https://example.com/invalid-source"
        const val INTEGER_NAME_CLIENT_ID = "integer-name-client"
        const val ZERO_META_CLIENT_ID = "zero-meta-client"
        const val NEGATIVE_META_CLIENT_ID = "negative-meta-client"
        const val TEXT_META_CLIENT_ID = "text-meta-client"
        const val INVALID_SOURCE_CLIENT_ID = "invalid-source-client"
        val NAME_CLIENT_ID = "1".repeat(64)
        val ICON_CLIENT_ID = "2".repeat(64)
        val URL_CLIENT_ID = "3".repeat(64)
        val HOSTILE_TEXT = "x".repeat(100_000)
        val HOSTILE_URL = "https://example.com/" + "u".repeat(100_000)
    }
}
