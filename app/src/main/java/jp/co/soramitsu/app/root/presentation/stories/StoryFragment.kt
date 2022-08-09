package jp.co.soramitsu.app.root.presentation.stories

import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.app.R
import jp.co.soramitsu.app.databinding.FragmentStoryBinding
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.mixin.impl.observeBrowserEvents
import jp.co.soramitsu.common.presentation.StoryElement
import jp.co.soramitsu.common.presentation.StoryGroupModel
import jp.co.soramitsu.common.view.viewBinding
import jp.shts.android.storiesprogressview.StoriesProgressView
import javax.inject.Inject

@AndroidEntryPoint
class StoryFragment : BaseFragment<StoryViewModel>(R.layout.fragment_story), StoriesProgressView.StoriesListener {

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

    @Inject
    lateinit var factory: StoryViewModel.StoryViewModelFactory

    private val binding by viewBinding(FragmentStoryBinding::bind)

    private val vm: StoryViewModel by viewModels {
        StoryViewModel.provideFactory(factory, requireArguments().getParcelable<StoryGroupModel>(KEY_STORY) as StoryGroupModel)
    }
    override val viewModel: StoryViewModel
        get() = vm

    private var lastActionDown = 0L

    override fun initViews() {

        binding.storyCloseIcon.setOnClickListener { viewModel.backClicked() }

        binding.stories.setStoriesListener(this)

        binding.storyContainer.setOnTouchListener(::handleStoryTouchEvent)
    }

    override fun subscribe(viewModel: StoryViewModel) {
        observeBrowserEvents(viewModel)

        viewModel.storyLiveData.observe {
            binding.stories.setStoriesCount(it.size)
            binding.stories.setStoryDuration(STORY_DURATION)
            binding.stories.startStories()
        }

        viewModel.currentStoryLiveData.observe {
            binding.storyTitle.setText(it.titleRes)
            binding.storyBody.setText(it.bodyRes)
            binding.storyImage.isVisible = it is StoryElement.Onboarding

            if (it is StoryElement.Onboarding) {
                binding.storyImage.setImageResource(it.imageRes)
                binding.stakingStoryLearnMore.isVisible = false
                it.buttonCaptionRes?.let { buttonText ->
                    binding.stakingStoryLearnMore.isVisible = true
                    binding.stakingStoryLearnMore.setText(buttonText)
                    binding.stakingStoryLearnMore.setOnClickListener { viewModel.complete() }
                }
            } else {
                binding.stakingStoryLearnMore.isVisible = !(it as? StoryElement.Staking)?.url.isNullOrEmpty()
                binding.stakingStoryLearnMore.setText(R.string.common_learn_more)
                binding.stakingStoryLearnMore.setOnClickListener { viewModel.learnMoreClicked() }
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

    private fun handleStoryTouchEvent(view: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastActionDown = System.currentTimeMillis()
                binding.stories.pause()
            }
            MotionEvent.ACTION_UP -> {
                binding.stories.resume()
                val eventTime = System.currentTimeMillis()
                if (eventTime - lastActionDown < STORY_CLICK_MAX_DURATION) {
                    if (view.width / 2 < event.x) {
                        binding.stories.skip()
                    } else {
                        binding.stories.reverse()
                    }
                } else {
                    view.performClick()
                }
            }
        }
        return true
    }
}
