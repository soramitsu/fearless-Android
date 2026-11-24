package jp.co.soramitsu.app

import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.core.app.ApplicationProvider
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.common.data.network.OptionsProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The production Application with its four injected services replaced by mocks (the Hilt graph is skipped). */
class ShimmedApp : App() {
    init {
        val generatedBase = Class.forName("jp.co.soramitsu.app.Hilt_App")
        if (generatedBase.isInstance(this)) {
            generatedBase.getDeclaredField("injected").apply { isAccessible = true }.setBoolean(this, true)
        }
        mutationAuthorizationRefresher = mockk(relaxed = true)
        soraCardLeftoverCleanup = mockk(relaxed = true)
        sharedExportCleanup = mockk(relaxed = true)
        walletConnectStarter = mockk(relaxed = true)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(application = ShimmedApp::class)
class AppTest {
    private val app = ApplicationProvider.getApplicationContext<ShimmedApp>()

    @Test
    fun startupPublishesBuildMetadataAndStartsTheBackgroundServices() {
        assertEquals(BuildConfig.LIBRARY_PACKAGE_NAME, OptionsProvider.APPLICATION_ID)
        assertEquals(BuildConfig.VERSION_CODE, OptionsProvider.CURRENT_VERSION_CODE)
        assertEquals(BuildConfig.VERSION_NAME, OptionsProvider.CURRENT_VERSION_NAME)
        assertEquals(BuildConfig.BUILD_TYPE, OptionsProvider.CURRENT_BUILD_TYPE)
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, AppCompatDelegate.getDefaultNightMode())
        verify(exactly = 1) { app.mutationAuthorizationRefresher.start(any()) }
        verify(exactly = 1) { app.soraCardLeftoverCleanup.start(any()) }
        verify(exactly = 1) { app.sharedExportCleanup.start(any()) }
        verify(exactly = 1) { app.walletConnectStarter.start() }
    }

    @Test
    fun aConfigurationChangeKeepsTheWalletLanguage() {
        val before = app.resources.configuration.locales[0].language
        app.onConfigurationChanged(Configuration(app.resources.configuration))
        assertEquals(before, app.resources.configuration.locales[0].language)
    }
}
