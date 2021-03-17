package jp.co.soramitsu.feature_staking_impl.presentation.staking.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.LinearLayoutManager
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.presentation.staking.StakingStoriesAdapter
import jp.co.soramitsu.feature_staking_impl.presentation.staking.model.StakingStoryModel
import kotlinx.android.synthetic.main.view_network_info.view.stakingStoriesList

class NetworkInfoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : ConstraintLayout(context, attrs, defStyle), StakingStoriesAdapter.StoryItemHandler {

    private val storiesAdapter = StakingStoriesAdapter(this)

    var storyItemHandler: (StakingStoryModel) -> Unit = {}

    init {
        View.inflate(context, R.layout.view_network_info, this)

        stakingStoriesList.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        stakingStoriesList.adapter = storiesAdapter
    }

    fun submitStories(stories: List<StakingStoryModel>) {
        storiesAdapter.submitList(stories)
    }

    override fun storyClicked(story: StakingStoryModel) {
        storyItemHandler(story)
    }
}
