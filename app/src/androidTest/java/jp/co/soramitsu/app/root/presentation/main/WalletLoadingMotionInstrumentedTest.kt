package jp.co.soramitsu.app.root.presentation.main

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import jp.co.soramitsu.common.compose.component.FearlessProgress
import jp.co.soramitsu.common.compose.component.Shimmer
import jp.co.soramitsu.common.compose.component.ShimmerRectangle
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.fw.FwEffects
import jp.co.soramitsu.common.compose.theme.fw.FwStandard
import jp.co.soramitsu.common.compose.theme.fw.FwTier
import jp.co.soramitsu.common.compose.theme.fw.LocalFwEffects
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Actual rendered animation frames, using public placeholders and no wallet state. */
@RunWith(AndroidJUnit4::class)
class WalletLoadingMotionInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<PolkaswapNavigationLayoutTestActivity>()

    @Test fun reducedMotionKeepsBothSkeletonShapesAndProgressStill() = checkFrames(reduceMotion = true)

    @Test fun ordinaryMotionKeepsActualLoadingAnimation() = checkFrames(reduceMotion = false)

    private fun checkFrames(reduceMotion: Boolean) {
        compose.mainClock.autoAdvance = false
        var still by mutableStateOf(!reduceMotion)
        compose.setContent {
            FearlessTheme {
                CompositionLocalProvider(LocalFwEffects provides FwEffects(FwTier.Full, false, still)) {
                    Column(Modifier.requiredWidth(320.dp).background(FwStandard.bgBase).padding(16.dp)) {
                        Box(Modifier.testTag("pill")) { Shimmer(Modifier.width(240.dp).height(24.dp)) }
                        Box(Modifier.testTag("rectangle").padding(top = 16.dp)) {
                            ShimmerRectangle(Modifier.width(240.dp).height(72.dp))
                        }
                        Box(Modifier.testTag("progress").padding(top = 16.dp)) { FearlessProgress() }
                    }
                }
            }
        }
        // A live preference change must replace the effect, rather than only affect new screens.
        compose.runOnUiThread { still = reduceMotion }
        val arguments = InstrumentationRegistry.getArguments()
        val label = arguments.getString("loadingEvidenceLabel") ?: "motion"
        check(label.matches(Regex("[a-zA-Z0-9_-]+")))
        val dir = File(compose.activity.getExternalFilesDir(null), "loading-$label").apply { mkdirs() }
        val tags = listOf("pill", "rectangle", "progress")
        val frames = tags.associateWith { mutableListOf<Bitmap>() }
        for (frame in 0..3) {
            compose.mainClock.advanceTimeBy(if (frame == 0) 1600 else 320)
            compose.waitForIdle()
            for (tag in tags) {
                val bitmap = compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
                File(dir, "$reduceMotion-$tag-$frame.png").outputStream().use {
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
                frames.getValue(tag).add(bitmap)
            }
        }
        val results = JSONArray()
        val failures = mutableListOf<String>()
        val expectedStill = reduceMotion && arguments.getString("loadingExpectStill", "true") == "true"
        for ((tag, images) in frames) {
            val equal = images.drop(1).all { images.first().sameAs(it) }
            results.put(JSONObject().put("component", tag).put("reduceMotion", reduceMotion)
                .put("frames", images.size).put("allPixelsEqual", equal).put("expectedStill", expectedStill))
            if (equal != expectedStill) failures += "$tag: allPixelsEqual=$equal expectedStill=$expectedStill"
        }
        File(dir, "frames-$reduceMotion.json").writeText(results.toString(2))
        assertTrue(failures.joinToString("; "), failures.isEmpty())
    }
}
