package jp.co.soramitsu.staking.impl.presentation

import android.content.Context
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import java.time.Duration
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.fw.FwRestyle
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowSystemClock

/** Robolectric base for the staking screens: theme, double-tap guard clock, and the legacy palette switch. */
@RunWith(RobolectricTestRunner::class)
abstract class StakingComposeTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun moveClockPastTheDoubleTapGuard() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        tick()
    }

    @After
    fun restorePalette() {
        unmockkObject(FwRestyle)
    }

    protected fun tick() {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
    }

    protected fun render(legacy: Boolean = false, content: @Composable () -> Unit) {
        if (legacy) {
            mockkObject(FwRestyle)
            every { FwRestyle.isEnabled(any()) } returns false
            every { FwRestyle.paletteEnabled } returns false
        }
        compose.setContent { FearlessAppTheme { content() } }
        compose.waitForIdle()
    }

    protected fun str(@StringRes id: Int, vararg args: Any): String =
        ApplicationProvider.getApplicationContext<Context>().getString(id, *args)
}
