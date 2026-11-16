package jp.co.soramitsu.account.impl.data.repository.datasource

import com.google.gson.Gson
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import jp.co.soramitsu.account.api.domain.model.AuthType
import jp.co.soramitsu.account.api.domain.model.MetaAccountOrdering
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.core.model.Language
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.coredb.dao.MetaAccountDao
import jp.co.soramitsu.coredb.model.chain.FavoriteChainLocal
import jp.co.soramitsu.coredb.model.MetaAccountLocal
import jp.co.soramitsu.coredb.model.MetaAccountPositionUpdate
import jp.co.soramitsu.coredb.model.RelationJoinedMetaAccountInfo
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AccountDataSourceImplDaoTest {

    private val preferences = mockk<Preferences>(relaxed = true)
    private val dao = mockk<MetaAccountDao>(relaxed = true)
    private val chains = mockk<ChainsRepository>()
    private val selected = MutableStateFlow<RelationJoinedMetaAccountInfo?>(null)

    private fun source(): AccountDataSourceImpl {
        every { dao.selectedMetaAccountInfoFlow() } returns selected
        every { chains.chainsByIdFlow() } returns flowOf(emptyMap())
        coEvery { chains.getChainsById() } returns emptyMap()
        return AccountDataSourceImpl(preferences, Gson(), dao, mockk(), chains, mockk())
    }

    private fun local(id: Long, name: String = "wallet$id", crypto: CryptoType = CryptoType.ED25519) =
        RelationJoinedMetaAccountInfo(
            MetaAccountLocal(
                substratePublicKey = byteArrayOf(id.toByte()), substrateCryptoType = crypto,
                substrateAccountId = byteArrayOf(id.toByte()), ethereumPublicKey = null, ethereumAddress = null,
                tonPublicKey = null, name = name, isSelected = true, position = id.toInt(), isBackedUp = false,
                googleBackupAddress = null, initialized = true
            ).also { it.id = id },
            emptyList(),
            emptyList()
        )

    @Test
    fun authTypeDefaultsToPinAndRoundTripsThroughPreferences(): Unit = runBlocking {
        val source = source()
        every { preferences.getString("auth_type") } returns null
        assertEquals(AuthType.PINCODE, source.getAuthType())

        every { preferences.getString("auth_type") } returns "BIOMETRY"
        assertEquals(AuthType.BIOMETRY, source.getAuthType())

        source.saveAuthType(AuthType.PINCODE)
        verify { preferences.putString("auth_type", "PINCODE") }
    }

    @Test
    fun languageIsReadAndSavedByIsoCodeAndAMissingOneIsAnError(): Unit = runBlocking {
        val source = source()
        every { preferences.getCurrentLanguage() } returns Language("ja")
        assertEquals(Language("ja"), source.getSelectedLanguage())

        source.changeSelectedLanguage(Language("en"))
        verify { preferences.saveCurrentLanguage("en") }

        every { preferences.getCurrentLanguage() } returns null
        try {
            source.getSelectedLanguage()
            fail("a missing language must not fall back silently")
        } catch (expected: IllegalArgumentException) {
            assertEquals("No language selected", expected.message)
        }
    }

    @Test
    fun walletsAreReadFromTheDaoAndMapped(): Unit = runBlocking {
        val source = source()
        coEvery { dao.getJoinedMetaAccountInfo(5L) } returns local(5L, "Savings", CryptoType.ECDSA)
        coEvery { dao.selectedMetaAccountInfo() } returns local(6L, "Daily")
        every { dao.getJoinedMetaAccountsInfo() } returns listOf(local(1L), local(2L))
        every { dao.getMetaAccountInfo(any()) } returns local(7L, "Found")

        assertEquals("Savings", source.getMetaAccount(5L).name)
        assertEquals("Savings", source.getLightMetaAccount(5L).name)
        assertEquals(CryptoType.ECDSA, source.getPreferredCryptoTypeOrSelected(5L))
        assertEquals(CryptoType.SR25519, source.getPreferredCryptoTypeOrSelected(null))
        assertEquals("Daily", source.getSelectedMetaAccount().name)
        assertEquals("Daily", source.getSelectedLightMetaAccount().name)
        assertEquals(listOf("wallet1", "wallet2"), source.allMetaAccounts().map { it.name })
        assertEquals("Found", source.findMetaAccount(byteArrayOf(7))?.name)

        every { dao.getMetaAccountInfo(any()) } returns null
        assertNull(source.findMetaAccount(byteArrayOf(8)))
    }

    @Test
    fun walletFlowsFollowTheDao(): Unit = runBlocking {
        val source = source()
        every { dao.observeJoinedMetaAccountsInfo() } returns flowOf(listOf(local(1L), local(2L)))
        every { dao.observeOrderedJoinedMetaAccountsInfo() } returns flowOf(listOf(local(2L), local(1L)))
        every { dao.observeJoinedMetaAccountInfo(3L) } returns flowOf(null, local(3L, "Third"))
        val favorites = flowOf(emptyList<FavoriteChainLocal>())
        every { dao.observeFavoriteChains(3L) } returns favorites

        assertEquals(listOf("wallet1", "wallet2"), source.observeAllMetaAccounts().first().map { it.name })
        assertEquals(listOf("wallet2", "wallet1"), source.lightMetaAccountsFlow().first().map { it.name })
        assertEquals("Third", source.lightMetaAccountFlow(3L).first().name)
        assertSame(favorites, source.observeFavoriteChains(3L))

        assertFalse(source.anyAccountSelected())
        selected.value = local(9L, "Selected")
        assertTrue(source.anyAccountSelected())
        assertEquals("Selected", source.selectedMetaAccountFlow().first().name)
        assertEquals("Selected", source.selectedLightMetaAccount().first().name)
    }

    @Test
    fun walletChangesAreWrittenToTheDao(): Unit = runBlocking {
        val source = source()
        val updates = slot<List<MetaAccountPositionUpdate>>()
        coEvery { dao.updatePositions(capture(updates)) } returns Unit
        every { dao.isMetaAccountExists(any()) } returns true

        source.selectMetaAccount(4L)
        source.updateAccountPositions(listOf(MetaAccountOrdering(4L, 1), MetaAccountOrdering(5L, 0)))
        source.updateMetaAccountName(4L, "Renamed")
        source.updateMetaAccountBackedUp(4L)
        source.updateWalletOnGoogleBackupDelete(4L)

        coVerify { dao.selectMetaAccount(4L) }
        assertEquals(listOf(4L to 1, 5L to 0), updates.captured.map { it.id to it.position })
        coVerify { dao.updateName(4L, "Renamed") }
        coVerify { dao.updateBackedUp(4L, true) }
        coVerify { dao.clearGoogleBackupInfo(4L) }
        assertTrue(source.accountExists(byteArrayOf(4)))
    }
}
