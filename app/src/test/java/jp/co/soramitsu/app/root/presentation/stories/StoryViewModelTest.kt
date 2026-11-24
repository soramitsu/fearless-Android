package jp.co.soramitsu.app.root.presentation.stories

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import jp.co.soramitsu.common.presentation.StoryElement
import jp.co.soramitsu.common.presentation.StoryGroupModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class StoryViewModelTest {
    @get:Rule val instant = InstantTaskExecutorRule()

    private val first = StoryElement.Staking(1, 2, "https://wiki.example/staking")
    private val second = StoryElement.Onboarding(3, 4, 5, null)
    private val third = StoryElement.Staking(6, 7, null)
    private var backs = 0

    private fun model(vararg stories: StoryElement) = StoryViewModel(
        back = { backs++ },
        savedStateHandle = SavedStateHandle(mapOf(StoryFragment.KEY_STORY to StoryGroupModel(stories.toList())))
    )

    @Test
    fun storiesStartAtTheFirstAndStepWithinTheirBounds() {
        val model = model(first, second, third)
        assertEquals(listOf(first, second, third), model.storyLiveData.value)
        assertEquals(first, model.currentStoryLiveData.value)

        model.previousStory()
        assertEquals(first, model.currentStoryLiveData.value)
        model.nextStory()
        model.nextStory()
        assertEquals(third, model.currentStoryLiveData.value)
        model.nextStory()
        assertEquals(third, model.currentStoryLiveData.value)
        model.previousStory()
        assertEquals(second, model.currentStoryLiveData.value)
    }

    @Test
    fun learnMoreOpensOnlyAStakingStoryWithALink() {
        val model = model(first, second, third)
        model.learnMoreClicked()
        assertEquals("https://wiki.example/staking", model.openBrowserEvent.value?.peekContent())

        model.openBrowserEvent.value = null
        model.nextStory()
        model.learnMoreClicked()
        assertNull("Onboarding stories have no link", model.openBrowserEvent.value)
        model.nextStory()
        model.learnMoreClicked()
        assertNull("A staking story without a link opens nothing", model.openBrowserEvent.value)
    }

    @Test
    fun backAndCompletionBothLeaveTheStories() {
        val model = model(first)
        model.backClicked()
        model.complete()
        assertEquals(2, backs)
    }

    @Test
    fun anEmptyGroupHasNoCurrentStoryAndIgnoresStepping() {
        val model = model()
        assertNull(model.currentStoryLiveData.value)
        model.nextStory()
        model.previousStory()
        model.learnMoreClicked()
        assertNull(model.currentStoryLiveData.value)
        assertNull(model.openBrowserEvent.value)
    }
}
