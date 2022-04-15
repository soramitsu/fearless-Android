package jp.co.soramitsu.app.root.presentation.stories

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import jp.co.soramitsu.app.R
import jp.co.soramitsu.app.root.di.RootApi
import jp.co.soramitsu.app.root.di.RootComponent
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.mixin.impl.observeBrowserEvents
import jp.co.soramitsu.common.presentation.StoryElement
import jp.co.soramitsu.common.presentation.StoryGroupModel
import jp.shts.android.storiesprogressview.StoriesProgressView
import kotlinx.android.synthetic.main.fragment_story.stakingStoryLearnMore
import kotlinx.android.synthetic.main.fragment_story.stories
import kotlinx.android.synthetic.main.fragment_story.storyBody
import kotlinx.android.synthetic.main.fragment_story.storyCloseIcon
import kotlinx.android.synthetic.main.fragment_story.storyContainer
import kotlinx.android.synthetic.main.fragment_story.storyImage
import kotlinx.android.synthetic.main.fragment_story.storyTitle

class StoryFragment : BaseFragment<StoryViewModel>(), StoriesProgressView.StoriesListener {

    companion object {
        const val KEY_STORY = "story"
        private const val STORY_DURATION = 6200L
        private const val STORY_CLICK_MAX_DURATION = 500L

        fun getBundle(stories: StoryGroupModel): Bundle {
            return Bundle().apply {
                putParcelable(KEY_STORY, stories)
            }
        }
    }

    private var lastActionDown = 0L

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_story, container, false)
    }

    override fun initViews() {

        storyCloseIcon.setOnClickListener { viewModel.backClicked() }

        stories.setStoriesListener(this)

        storyContainer.setOnTouchListener(::handleStoryTouchEvent)
    }

    override fun inject() {
        val stories = argument<StoryGroupModel>(KEY_STORY)

        FeatureUtils.getFeature<RootComponent>(this, RootApi::class.java)
            .storyComponentFactory()
            .create(this, stories)
            .inject(this)
    }

    override fun subscribe(viewModel: StoryViewModel) {
        observeBrowserEvents(viewModel)

        viewModel.storyLiveData.observe {
            stories.setStoriesCount(it.size)
            stories.setStoryDuration(STORY_DURATION)
            stories.startStories()
        }

        viewModel.currentStoryLiveData.observe {
            storyTitle.setText(it.titleRes)
            storyBody.setText(it.bodyRes)
            storyImage.isVisible = it is StoryElement.Onboarding

            if (it is StoryElement.Onboarding) {
                storyImage.setImageResource(it.imageRes)
                stakingStoryLearnMore.isVisible = false
                it.buttonCaptionRes?.let { buttonText ->
                    stakingStoryLearnMore.isVisible = true
                    stakingStoryLearnMore.setText(buttonText)
                    stakingStoryLearnMore.setOnClickListener { viewModel.complete() }
                }
            } else {
                stakingStoryLearnMore.isVisible = true
                stakingStoryLearnMore.setText(R.string.common_learn_more)
                stakingStoryLearnMore.setOnClickListener { viewModel.learnMoreClicked() }
            }
        }
    }

    override fun onComplete() {
        viewModel.complete()
    }

    override fun onPrev() {
        viewModel.previousStory()
    }

    override fun onNext() {
        viewModel.nextStory()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stories.destroy()
    }

    private fun handleStoryTouchEvent(view: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastActionDown = System.currentTimeMillis()
                stories.pause()
            }
            MotionEvent.ACTION_UP -> {
                stories.resume()
                val eventTime = System.currentTimeMillis()
                if (eventTime - lastActionDown < STORY_CLICK_MAX_DURATION) {
                    if (view.width / 2 < event.x) {
                        stories.skip()
                    } else {
                        stories.reverse()
                    }
                } else {
                    view.performClick()
                }
            }
        }
        return true
    }
}
