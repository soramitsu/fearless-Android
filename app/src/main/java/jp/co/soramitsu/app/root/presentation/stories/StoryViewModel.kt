package jp.co.soramitsu.app.root.presentation.stories

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.mixin.api.Browserable
import jp.co.soramitsu.common.presentation.StoryElement
import jp.co.soramitsu.common.presentation.StoryGroupModel
import jp.co.soramitsu.common.utils.Event
import javax.inject.Inject

typealias NavigatorBackTransition = () -> Unit

@HiltViewModel
class StoryViewModel @Inject constructor(
    private val back: NavigatorBackTransition,
    private val savedStateHandle: SavedStateHandle
) : BaseViewModel(), Browserable {

    private val stories = savedStateHandle.getLiveData<StoryGroupModel>(StoryFragment.KEY_STORY).value!!.stories

    private val _storyLiveData = MutableLiveData(stories)
    val storyLiveData: LiveData<List<StoryElement>> = _storyLiveData

    private val _currentStoryLiveData = MutableLiveData<StoryElement>()
    val currentStoryLiveData: LiveData<StoryElement> = _currentStoryLiveData

    override val openBrowserEvent = MutableLiveData<Event<String>>()

    init {
        stories.firstOrNull()?.let {
            _currentStoryLiveData.value = it
        }
    }

    fun backClicked() {
        back()
    }

    fun nextStory() {
        val stories = storyLiveData.value ?: return
        val currentStory = currentStoryLiveData.value ?: return

        val nextStoryIndex = stories.indexOf(currentStory) + 1
        if (nextStoryIndex < stories.size) {
            _currentStoryLiveData.value = stories[nextStoryIndex]
        }
    }

    fun previousStory() {
        val stories = storyLiveData.value ?: return
        val currentStory = currentStoryLiveData.value ?: return

        val previousStoryIndex = stories.indexOf(currentStory) - 1
        if (previousStoryIndex >= 0) {
            _currentStoryLiveData.value = stories[previousStoryIndex]
        }
    }

    fun complete() {
        back()
    }

    fun learnMoreClicked() {
        (currentStoryLiveData.value as? StoryElement.Staking?)?.url?.let {
            openBrowserEvent.value = Event(it)
        }
    }
}
