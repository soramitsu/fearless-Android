package jp.co.soramitsu.app.root.presentation.stories

import android.app.Activity
import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [36], application = android.app.Application::class)
class StoryProgressViewTest {
    @Test
    fun shouldAdvanceOnlyWhileAttachedAndRunning() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val progress = StoryProgressView(activity, null)
        activity.setContentView(progress)
        val events = mutableListOf<String>()
        progress.onNext = { events.add("next") }
        progress.onPrevious = { events.add("previous") }
        progress.onFinished = { events.add("finished") }
        val looper = shadowOf(Looper.getMainLooper())
        progress.start(2, 0, 1000)
        looper.idleFor(Duration.ofMillis(300))
        progress.pause()
        looper.idleFor(Duration.ofSeconds(2))
        assertEquals(emptyList<String>(), events)
        progress.resume()
        looper.idleFor(Duration.ofMillis(700))
        progress.reverse()
        progress.skip()
        activity.setContentView(android.view.View(activity))
        looper.idleFor(Duration.ofSeconds(2))
        assertEquals(listOf("next", "previous", "next"), events)
        activity.setContentView(progress)
        looper.idleFor(Duration.ofSeconds(2))
        progress.skip()
        assertEquals(listOf("next", "previous", "next", "finished"), events)
    }
}
