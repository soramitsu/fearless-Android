package jp.co.soramitsu.walletconnect.impl.data.legacy

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.walletconnect.impl.data.EncryptedWalletConnectStateStorage
import jp.co.soramitsu.walletconnect.impl.data.LegacyWalletConnectImport.Outcome
import jp.co.soramitsu.walletconnect.impl.data.legacy.LegacyFixtures.section
import jp.co.soramitsu.walletconnect.impl.protocol.WcMemoryStorage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.security.GeneralSecurityException

class ReownLegacyWalletConnectImportTest {

    private val now = section("reown")["now"].asLong
    private val storage = WcMemoryStorage()
    private val attempts = MemoryImportAttempts()

    private fun reownImport(source: ReownLegacySource = ReownFixtureSource()) =
        ReownLegacyWalletConnectImport(ReownSessionImporter(source, attempts, { now }))

    @Test
    fun `only a result that lets the SDK's files go allows the starter's cleanup`() {
        val complete = ReownImportSummary(4, 1, 2, true, emptyList())
        val incomplete = complete.copy(skipped = listOf(ReownSkipReason.LIMIT_REACHED))
        val expected = mapOf(
            ReownImportResult.NothingToImport to Outcome.NOTHING_TO_IMPORT,
            ReownImportResult.Imported(complete) to Outcome.IMPORTED,
            ReownImportResult.Imported(incomplete) to Outcome.FAILED,
            ReownImportResult.AlreadyImported(keepsLegacyFiles = false) to Outcome.IMPORTED,
            ReownImportResult.AlreadyImported(keepsLegacyFiles = true) to Outcome.FAILED
        ) + ReownImportFailure.entries.associate { ReownImportResult.Failed(it) to Outcome.FAILED }

        expected.forEach { (result, outcome) -> assertEquals(result.toString(), outcome, result.toOutcome()) }
    }

    @Test
    fun `the starter sees the upgrade's import, and a start after it, as imported`() {
        assertEquals(Outcome.IMPORTED, runBlocking { reownImport().importInto(storage) })
        assertEquals(Outcome.IMPORTED, runBlocking { reownImport().importInto(storage) })

        assertEquals(4, WcStoredStateJson.decodeOrEmpty(storage.json).sessions.size)
        assertEquals(1, attempts.value)
    }

    @Test
    fun `unreadable SDK data reaches the starter as failed, and a device without any as nothing to import`() {
        val unreadable = ReownFixtureSource(failure = GeneralSecurityException("keystore says no"))
        val neverRan = ReownFixtureSource(hasData = false)

        assertEquals(Outcome.FAILED, runBlocking { reownImport(unreadable).importInto(storage) })
        assertEquals(Outcome.NOTHING_TO_IMPORT, runBlocking { reownImport(neverRan).importInto(storage) })
    }

    @Test
    fun `the app's import counts its attempts in the client's preferences file`() {
        val preferences = mockk<SharedPreferences>(relaxed = true)
        val context = mockk<Context> {
            every { getSharedPreferences(EncryptedWalletConnectStateStorage.FILE_NAME, Context.MODE_PRIVATE) } returns preferences
        }

        assertNotNull(ReownLegacyWalletConnectImport.create(context))
        verify { context.getSharedPreferences(EncryptedWalletConnectStateStorage.FILE_NAME, Context.MODE_PRIVATE) }
    }
}
