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
import kotlinx.android.synthetic.main.fragment_story.storyBody
import kotlinx.android.synthetic.main.fragment_story.storyCloseIcon
import kotlinx.android.synthetic.main.fragment_story.storyTitle

class StoryFragment : BaseFragment<StoryViewModel>() {

    companion object {
        private const val KEY_STORY = "story"

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
        viewModel.storyLiveData.observe {
            storyTitle.text = it.elements[0].title
            storyBody.text = it.elements[0].body
        }
    }
}
