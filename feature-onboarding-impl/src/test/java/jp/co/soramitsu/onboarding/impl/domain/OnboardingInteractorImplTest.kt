package jp.co.soramitsu.onboarding.impl.domain

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.domain.AppVersion
import jp.co.soramitsu.onboarding.api.data.OnboardingConfig
import jp.co.soramitsu.onboarding.api.data.OnboardingRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingInteractorImplTest {

    private val repository = mockk<OnboardingRepository>()
    private val preferences = mockk<Preferences>(relaxed = true)
    private val interactor = OnboardingInteractorImpl(repository, preferences)
    private val current = AppVersion.current()

    private fun item(version: String) = OnboardingConfig.OnboardingConfigItem(
        minVersion = version,
        background = "bg-$version",
        enEn = OnboardingConfig.OnboardingConfigItem.Variants(emptyList(), emptyList())
    )

    @Test
    fun configComesStraightFromTheRepository(): Unit = runBlocking {
        val config = OnboardingConfig(listOf(item("1.0.0")))
        coEvery { repository.getConfig() } returns Result.success(config)

        assertSame(config, interactor.getConfig().getOrThrow())
    }

    @Test
    fun supportedConfigIsTheNewestForThisMajorAndMinorVersion(): Unit = runBlocking {
        val major = current.major
        val minor = current.minor
        coEvery { repository.getConfig() } returns Result.success(
            OnboardingConfig(
                listOf(
                    item("$major.$minor.0"),
                    item("$major.$minor.7"),
                    item("$major.${minor + 1}.0"),
                    item("${major + 1}.$minor.9"),
                    item("$major.$minor.3")
                )
            )
        )

        assertEquals("$major.$minor.7", interactor.getAppVersionSupportedConfig().getOrThrow()!!.minVersion)
    }

    @Test
    fun noConfigForThisVersionIsEmptyAndAFailureStaysAFailure(): Unit = runBlocking {
        coEvery { repository.getConfig() } returns Result.success(OnboardingConfig(listOf(item("${current.major + 1}.0.0"))))
        assertNull(interactor.getAppVersionSupportedConfig().getOrThrow())

        val error = IllegalStateException("offline")
        coEvery { repository.getConfig() } returns Result.failure(error)
        assertSame(error, interactor.getAppVersionSupportedConfig().exceptionOrNull())
    }

    @Test
    fun shownWelcomeVersionIsRememberedInPreferences() {
        every { preferences.getString("prefs_shown_welcome_version") } returns "4.3.0"

        assertEquals("4.3.0", interactor.getWelcomeSlidesShownVersion())
        assertFalse(interactor.shouldShowWelcomeSlides("4.3.0"))
        assertTrue(interactor.shouldShowWelcomeSlides("4.4.0"))

        interactor.saveWelcomeSlidesShownVersion("4.4.0")
        verify { preferences.putString("prefs_shown_welcome_version", "4.4.0") }
    }

    @Test
    fun slidesAreShownWhenNoVersionWasEverShown() {
        every { preferences.getString("prefs_shown_welcome_version") } returns null

        assertTrue(interactor.shouldShowWelcomeSlides("1.0.0"))
    }
}
