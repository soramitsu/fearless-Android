package jp.co.soramitsu.account.impl.domain

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.NomisScoreData
import jp.co.soramitsu.account.impl.presentation.VmTestFixtures
import jp.co.soramitsu.common.data.network.nomis.NOMIS_SCORING_ENABLED_KEY
import jp.co.soramitsu.common.data.network.nomis.NomisApi
import jp.co.soramitsu.common.data.network.nomis.NomisResponse
import jp.co.soramitsu.common.data.network.nomis.NomisResponseData
import jp.co.soramitsu.common.data.network.nomis.NomisStats
import jp.co.soramitsu.common.data.storage.Preferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class NomisScoreInteractorImplTest {
    private val repository = mockk<AccountRepository>()
    private val preferences = mockk<Preferences>(relaxed = true)
    private val api = mockk<NomisApi>()
    private val enabled = MutableStateFlow(true)
    private val interactor = NomisScoreInteractorImpl(repository, preferences, api, UnconfinedTestDispatcher())

    private fun configure() {
        every { preferences.getBoolean(NOMIS_SCORING_ENABLED_KEY, true) } answers { enabled.value }
        every { preferences.booleanFlow(NOMIS_SCORING_ENABLED_KEY, true) } returns enabled
    }

    @Test
    fun enabledScoreFlowsFollowWalletSelectionAndDisabledFlowsHideScores() = runTest {
        configure()
        val score = mockk<NomisScoreData>()
        val wallet = VmTestFixtures.meta()
        every { repository.observeNomisScores() } returns flowOf(listOf(score))
        every { repository.selectedMetaAccountFlow() } returns flowOf(wallet)
        every { repository.observeNomisScore(7) } returns flowOf(score)
        coEvery { repository.getMetaAccount(7) } returns wallet
        assertEquals(listOf(score), interactor.observeNomisScores().first())
        assertSame(score, interactor.observeCurrentAccountScore().first())
        assertSame(score, interactor.observeAccountScore(7).first())
        enabled.value = false
        assertEquals(emptyList<NomisScoreData>(), interactor.observeNomisScores().first())
        assertNull(interactor.observeCurrentAccountScore().first())
        interactor.nomisMultichainScoreEnabled = false
        verify { preferences.putBoolean(NOMIS_SCORING_ENABLED_KEY, false) }
    }

    @Test
    fun successfulScoreIsMappedAndCachedOnlyWhileTheFeatureIsEnabled() = runTest {
        configure()
        coEvery { api.getNomisScore("address") } returns NomisResponse(NomisResponseData("address", 0.81,
            NomisStats(1.5, 2.5, 9, 10, 2, 3.0, 4.0, 1.0, "not-a-date")))
        val score = interactor.getNomisScore("address")!!
        assertEquals(81, score.score)
        assertEquals(-1L, score.metaId)
        assertEquals(10L, score.totalTransactions)
        assertNull(score.scoredAt)
        assertSame(score, interactor.getNomisScore("address"))
        assertSame(score, interactor.getNomisScoreFromMemoryCache("address"))
        coVerify(exactly = 1) { api.getNomisScore("address") }
        enabled.value = false
        assertNull(interactor.getNomisScore("address"))
        assertNull(interactor.getNomisScoreFromMemoryCache("address"))
    }

    @Test
    fun failedRequestsDoNotPoisonTheCache() = runTest {
        configure()
        coEvery { api.getNomisScore("address") } throws IllegalStateException("offline")
        assertNull(interactor.getNomisScore("address"))
        assertNull(interactor.getNomisScoreFromMemoryCache("address"))
        assertNull(interactor.getNomisScore("address"))
        coVerify(exactly = 2) { api.getNomisScore("address") }
    }
}
