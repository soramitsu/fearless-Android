package jp.co.soramitsu.app.root.presentation.stories

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import jp.co.soramitsu.app.HiltShim
import jp.co.soramitsu.app.HiltShimApplication
import jp.co.soramitsu.app.HiltShimHostActivity
import jp.co.soramitsu.app.R
import jp.co.soramitsu.common.presentation.StoryElement
import jp.co.soramitsu.common.presentation.StoryGroupModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltShimApplication::class)
class StoryFragmentTest {
    private val staking = StoryElement.Staking(R.string.common_learn_more, R.string.common_continue, "https://wiki.example/staking")
    private val onboarding = StoryElement.Onboarding(R.string.common_close, R.string.common_retry, R.drawable.ic_close, R.string.common_ok)
    private val plain = StoryElement.Onboarding(R.string.common_cancel, R.string.common_copy, R.drawable.ic_close, null)
    private var backs = 0
    private lateinit var fragment: StoryFragment

    @Before
    fun setUp() {
        HiltShim.reset()
        HiltShim.inject(StoryFragment::class) { }
        HiltShim.viewModel(StoryViewModel::class) { handle -> StoryViewModel({ backs++ }, handle) }
        val host = Robolectric.buildActivity(HiltShimHostActivity::class.java).setup().get()
        fragment = StoryFragment().apply { arguments = StoryFragment.getBundle(StoryGroupModel(listOf(staking, onboarding, plain))) }
        // The progress bar advances stories on its own once the main looper runs; the tests drive it explicitly.
        host.supportFragmentManager.beginTransaction().add(android.R.id.content, fragment).commitNow()
    }

    @After
    fun tearDown() = HiltShim.reset()

    @Test
    fun aStakingStoryShowsItsTextAndOffersItsLink() {
        assertEquals(fragment.getString(staking.titleRes), text(R.id.storyTitle))
        assertEquals(fragment.getString(staking.bodyRes), text(R.id.storyBody))
        assertEquals(View.GONE, view(R.id.storyImage).visibility)
        val learnMore = view(R.id.stakingStoryLearnMore) as TextView
        assertEquals(View.VISIBLE, learnMore.visibility)
        assertEquals(fragment.getString(R.string.common_learn_more), learnMore.text.toString())
        learnMore.performClick()
        assertEquals("https://wiki.example/staking", fragment.viewModel.openBrowserEvent.value?.peekContent())
    }

    @Test
    fun onboardingStoriesShowTheirImageAndOptionalButton() {
        fragment.onNext()
        assertEquals(fragment.getString(onboarding.titleRes), text(R.id.storyTitle))
        assertEquals(View.VISIBLE, view(R.id.storyImage).visibility)
        val button = view(R.id.stakingStoryLearnMore) as TextView
        assertEquals(fragment.getString(R.string.common_ok), button.text.toString())
        button.performClick()
        assertEquals(1, backs)

        fragment.onNext()
        assertEquals(fragment.getString(plain.titleRes), text(R.id.storyTitle))
        assertEquals(View.GONE, view(R.id.stakingStoryLearnMore).visibility)

        fragment.onPrev()
        assertEquals(fragment.getString(onboarding.titleRes), text(R.id.storyTitle))
    }

    @Test
    fun closingOrFinishingTheStoriesGoesBack() {
        view(R.id.storyCloseIcon).performClick()
        fragment.onComplete()
        assertEquals(2, backs)
    }

    @Test
    fun aLongPressPausesAndThenClicksTheContainer() {
        val container = view(R.id.storyContainer)
        var clicked = 0
        container.setOnClickListener { clicked++ }
        touch(container, MotionEvent.ACTION_DOWN, x = 1f)
        Thread.sleep(600)
        touch(container, MotionEvent.ACTION_UP, x = 1f)
        assertEquals(1, clicked)

        touch(container, MotionEvent.ACTION_DOWN, x = 1f)
        touch(container, MotionEvent.ACTION_UP, x = container.width + 1f)
        assertTrue("A quick tap is a skip, not a click", clicked == 1)
    }

    private fun touch(target: View, action: Int, x: Float) {
        val now = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(now, now, action, x, 1f, 0)
        target.dispatchTouchEvent(event)
        event.recycle()
    }

    private fun view(id: Int): View = fragment.requireView().findViewById(id)
    private fun text(id: Int): String = (view(id) as TextView).text.toString()
}
