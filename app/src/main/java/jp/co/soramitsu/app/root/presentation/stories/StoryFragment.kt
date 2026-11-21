package jp.co.soramitsu.app.root.presentation.stories

import android.annotation.SuppressLint
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

@AndroidEntryPoint
class StoryFragment : BaseFragment<StoryViewModel>(R.layout.fragment_story) {

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

    private val binding by viewBinding(FragmentStoryBinding::bind)

    override val viewModel: StoryViewModel by viewModels()

    private var lastActionDown = 0L

    @SuppressLint("ClickableViewAccessibility")
    override fun initViews() {
        binding.storyCloseIcon.setOnClickListener { viewModel.backClicked() }

        binding.stories.onNext = viewModel::nextStory
        binding.stories.onPrevious = viewModel::previousStory
        binding.stories.onFinished = viewModel::complete

        binding.storyContainer.setOnTouchListener(::handleStoryTouchEvent)
    }

    override fun subscribe(viewModel: StoryViewModel) {
        observeBrowserEvents(viewModel)

        viewModel.storyLiveData.observe {
            binding.stories.start(it.size, it.indexOf(viewModel.currentStoryLiveData.value), STORY_DURATION)
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
            MotionEvent.ACTION_CANCEL -> binding.stories.resume()
        }
        return true
    }
}
