package jp.co.soramitsu.feature_staking_impl.presentation.story

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.feature_staking_api.di.StakingFeatureApi
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.di.StakingFeatureComponent
import jp.co.soramitsu.feature_staking_impl.presentation.staking.model.StakingStoryModel
import jp.shts.android.storiesprogressview.StoriesProgressView
import kotlinx.android.synthetic.main.fragment_story.stories
import kotlinx.android.synthetic.main.fragment_story.storyBody
import kotlinx.android.synthetic.main.fragment_story.storyCloseIcon
import kotlinx.android.synthetic.main.fragment_story.storyLeftSide
import kotlinx.android.synthetic.main.fragment_story.storyRightSide
import kotlinx.android.synthetic.main.fragment_story.storyTitle

class StoryFragment : BaseFragment<StoryViewModel>(), StoriesProgressView.StoriesListener {

    companion object {
        private const val KEY_STORY = "story"
        private const val STORY_DURATION = 6200L

        fun getBundle(story: StakingStoryModel): Bundle {
            return Bundle().apply {
                putParcelable(KEY_STORY, story)
            }
        }
    }

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

        storyLeftSide.setOnClickListener { stories.reverse() }
        storyRightSide.setOnClickListener { stories.skip() }
    }

    override fun inject() {
        val story = argument<StakingStoryModel>(KEY_STORY)

        FeatureUtils.getFeature<StakingFeatureComponent>(
            requireContext(),
            StakingFeatureApi::class.java
        )
            .storyComponentFactory()
            .create(this, story)
            .inject(this)
    }

    override fun subscribe(viewModel: StoryViewModel) {
        viewModel.storyLiveData.observe { story ->
            stories.setStoriesCount(story.elements.size)
            stories.setStoryDuration(STORY_DURATION)
            stories.startStories()
        }

        viewModel.currentStoryLiveData.observe {
            storyTitle.text = it.title
            storyBody.text = it.body
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
}
