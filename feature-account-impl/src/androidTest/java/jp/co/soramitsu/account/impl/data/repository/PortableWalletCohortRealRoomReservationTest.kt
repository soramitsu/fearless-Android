package jp.co.soramitsu.account.impl.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.co.soramitsu.coredb.AppDatabase
import jp.co.soramitsu.coredb.model.PortableWalletReservationLocal
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.testshared.HashMapEncryptedPreferences
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the staged identity-to-local-ID binding through real Room transactions. */
@RunWith(AndroidJUnit4::class)
class PortableWalletCohortRealRoomReservationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "portable-wallet-cohort-real-room-reservation"
    private var database: AppDatabase? = null
    private val role = PortableWalletSemanticMaterial.Role
    private val field = PortableWalletSemanticMaterial.FieldId

    @Before
    fun prepare() {
        context.deleteDatabase(databaseName)
    }

    @After
    fun close() {
        database?.close()
        database = null
        context.deleteDatabase(databaseName)
    }

    @Test
    fun twoWalletOriginsSurviveRoomRestartWithoutPublishingSignableRows() = runBlocking {
        val preferences = HashMapEncryptedPreferences()
        val semantic = semantic()
        try {
            val firstDatabase = open()
            val first = PortableWalletCohortFreshInstallStager(
                firstDatabase, firstDatabase.metaAccountDao(), preferences, Ids(listOf(41L, 42L)),
            )
            val token = first.stage(semantic)
            val sidecar = requireNotNull(
                preferences.getDecryptedString(PortableWalletCohortJournalStore.ORIGINAL_SOURCE_KEY)
            )
            val original = PortableWalletOriginalSourceSidecar.decode(sidecar)
            try {
                assertEquals(listOf(41L, 42L), original.wallets.map { it.localMetaId })
                assertArrayEquals(
                    byteArrayOf(0x22, 0x33),
                    original.wallets.first().sources.single().fieldCopy(field.SOURCE_BYTES),
                )
            } finally {
                original.clearSecrets()
            }
            assertEquals(listOf(41L, 42L), firstDatabase.portableWalletReservationDao().all().map { it.metaId })
            assertEquals(
                listOf(0xffff_ffffL, 0L),
                firstDatabase.portableWalletReservationDao().all().map { it.sourcePosition },
            )
            assertTrue(firstDatabase.metaAccountDao().getMetaAccounts().isEmpty())
            firstDatabase.close()
            database = null

            val restarted = open()
            val replay = PortableWalletCohortFreshInstallStager(
                restarted, restarted.metaAccountDao(), preferences, Ids(emptyList()),
            )
            assertEquals(token.afterImageSha256, replay.reconcile()?.afterImageSha256)
            assertEquals(sidecar, preferences.getDecryptedString(PortableWalletCohortJournalStore.ORIGINAL_SOURCE_KEY))
            assertEquals(
                listOf((1..16).hex(), (17..32).hex()),
                restarted.portableWalletReservationDao().all().map { it.portableIdHex },
            )
            val journal = requireNotNull(PortableWalletCohortJournalStore(preferences).load())
            try {
                val retained = journal.afterImage.semanticCopy()
                try {
                    assertArrayEquals(semantic, retained)
                } finally {
                    retained.fill(0)
                }
            } finally {
                journal.clearSecrets()
            }
            assertTrue(restarted.metaAccountDao().getMetaAccounts().isEmpty())
            replay.abandon(token)
            assertFalse(preferences.hasKey(PortableWalletCohortJournalStore.JOURNAL_KEY))
            assertFalse(preferences.hasKey(PortableWalletCohortJournalStore.ORIGINAL_SOURCE_KEY))
            assertEquals(
                listOf(PortableWalletReservationLocal.ABANDONED, PortableWalletReservationLocal.ABANDONED),
                restarted.portableWalletReservationDao().all().map { it.state },
            )
            assertTrue(restarted.metaAccountDao().getMetaAccounts().isEmpty())
        } finally {
            semantic.fill(0)
        }
    }

    @Test
    fun legacyPendingOriginsAreBoundByTheExactEncryptedJournalOnRoomReplay() = runBlocking {
        val preferences = HashMapEncryptedPreferences()
        val semantic = semantic()
        try {
            val firstDatabase = open()
            val staged = PortableWalletCohortFreshInstallStager(
                firstDatabase, firstDatabase.metaAccountDao(), preferences, Ids(listOf(41L, 42L)),
            )
            val token = staged.stage(semantic)
            firstDatabase.openHelper.writableDatabase.execSQL(
                "UPDATE portable_wallet_reservations SET portableIdHex = NULL, sourcePosition = NULL " +
                    "WHERE state = 0",
            )
            firstDatabase.close()
            database = null

            val restarted = open()
            val replay = PortableWalletCohortFreshInstallStager(
                restarted, restarted.metaAccountDao(), preferences, Ids(emptyList()),
            )
            val resumed = requireNotNull(replay.reconcile())
            assertEquals(token.operationId, resumed.operationId)
            assertEquals(token.afterImageSha256, resumed.afterImageSha256)
            assertEquals(
                listOf((1..16).hex(), (17..32).hex()),
                restarted.portableWalletReservationDao().all().map { it.portableIdHex },
            )
            assertEquals(
                listOf(0xffff_ffffL, 0L),
                restarted.portableWalletReservationDao().all().map { it.sourcePosition },
            )
            assertTrue(restarted.metaAccountDao().getMetaAccounts().isEmpty())
        } finally {
            semantic.fill(0)
        }
    }

    private fun open(): AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
        .build().also { database = it }

    private fun semantic(): ByteArray {
        val first = SubstrateKeypairFactory.generate(EncryptionType.ED25519, ByteArray(32) { 1 }, emptyList())
        val second = SubstrateKeypairFactory.generate(EncryptionType.ED25519, ByteArray(32) { 2 }, emptyList())
        val source = PortableWalletSemanticMaterial.Snapshot(
            1,
            listOf(
                wallet(1, 0xffff_ffffL, first.publicKey, first.privateKey, true),
                wallet(17, 0L, second.publicKey, second.privateKey, false),
            ),
        )
        return try {
            PortableWalletSemanticMaterial.encode(source)
        } finally {
            source.clearSecrets()
        }
    }

    private fun wallet(
        start: Int,
        position: Long,
        publicKey: ByteArray,
        privateKey: ByteArray,
        withSidecar: Boolean,
    ): PortableWalletSemanticMaterial.Wallet {
        val root = PortableWalletSemanticMaterial.Slot(
            role.SUBSTRATE_ROOT,
            "",
            listOf(
                bytes(field.PUBLIC_KEY, publicKey),
                bytes(field.PRIVATE_KEY, privateKey),
                bytes(field.ACCOUNT_ID_OR_ADDRESS, publicKey),
                bytes(field.CRYPTO_TYPE, byteArrayOf(2)),
                bytes(field.SOURCE_RECIPE, byteArrayOf(0)),
            ).sortedBy { it.id },
        )
        val slots = if (withSidecar) {
            listOf(
                root,
                PortableWalletSemanticMaterial.Slot(
                    role.AUXILIARY_SOURCE,
                    "0000",
                    listOf(
                        bytes(field.SOURCE_RECIPE, byteArrayOf(0)),
                        bytes(field.SOURCE_PLATFORM, byteArrayOf(1)),
                        bytes(field.SOURCE_SLOT_ROLE, byteArrayOf(11)),
                        bytes(field.BINDING_KIND, byteArrayOf(2)),
                        bytes(field.SOURCE_FORMAT, byteArrayOf(4)),
                        bytes(field.SOURCE_BYTES, byteArrayOf(0x22, 0x33)),
                    ).sortedBy { it.id },
                ),
            )
        } else {
            listOf(root)
        }
        return PortableWalletSemanticMaterial.Wallet(
            ByteArray(16) { (start + it).toByte() }, position, true, "Wallet $start", emptyList(), slots,
        )
    }

    private fun bytes(id: Int, value: ByteArray) = PortableWalletSemanticMaterial.Field(id, value.copyOf())

    private fun IntRange.hex(): String = joinToString("") { "%02x".format(it) }

    private class Ids(metaIds: List<Long>) : WalletMutationIdentifierSource {
        private val candidates = ArrayDeque(metaIds)
        override fun nextMetaIdCandidate(): Long = candidates.removeFirst()
        override fun nextOperationId(): String = "123e4567-e89b-42d3-a456-426614174000"
    }
}
