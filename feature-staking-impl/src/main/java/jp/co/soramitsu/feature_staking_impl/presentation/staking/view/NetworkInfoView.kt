package jp.co.soramitsu.feature_staking_impl.presentation.staking.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.recyclerview.widget.LinearLayoutManager
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.common.view.shape.getCutCornerDrawable
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.presentation.staking.StakingStoriesAdapter
import jp.co.soramitsu.feature_staking_impl.presentation.staking.model.StakingStoryModel
import kotlinx.android.synthetic.main.view_network_info.view.activeNominatorsView
import kotlinx.android.synthetic.main.view_network_info.view.lockUpPeriodView
import kotlinx.android.synthetic.main.view_network_info.view.minimumStakeView
import kotlinx.android.synthetic.main.view_network_info.view.stakingNetworkCollapsibleView
import kotlinx.android.synthetic.main.view_network_info.view.stakingNetworkInfoTitle
import kotlinx.android.synthetic.main.view_network_info.view.stakingStoriesList
import kotlinx.android.synthetic.main.view_network_info.view.totalStakeView

class NetworkInfoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : LinearLayout(context, attrs, defStyle), StakingStoriesAdapter.StoryItemHandler {

    enum class State {
        EXPANDED,
        COLLAPSED
    }

    var storyItemHandler: (StakingStoryModel) -> Unit = {}

    private val storiesAdapter = StakingStoriesAdapter(this)

    private var currentState = State.EXPANDED

    init {
        View.inflate(context, R.layout.view_network_info, this)

        with(context) {
            background = getCutCornerDrawable(R.color.blurColor)
        }

        orientation = VERTICAL

        stakingStoriesList.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        stakingStoriesList.adapter = storiesAdapter

        stakingNetworkInfoTitle.setOnClickListener { changeState() }
    }

    fun submitStories(stories: List<StakingStoryModel>) {
        storiesAdapter.submitList(stories)
    }

    fun setTotalStake(totalStake: String) {
        totalStakeView.setBody(totalStake)
    }

    fun setNominatorsCount(nominatorsCount: String) {
        activeNominatorsView.setBody(nominatorsCount)
    }

    fun setMinimumStake(minimumStake: String) {
        minimumStakeView.setBody(minimumStake)
    }

    fun setLockupPeriod(period: String) {
        lockUpPeriodView.setBody(period)
    }

    fun setTotalStakeFiat(totalStake: String) {
        totalStakeView.setExtraBlockValueText(totalStake)
    }

    fun showTotalStakeFiat() {
        totalStakeView.showWholeExtraBlock()
    }

    fun hideTotalStakeFiat() {
        totalStakeView.makeExtraBlockInvisible()
    }

    fun setMinimumStakeFiat(minimumStake: String) {
        minimumStakeView.setExtraBlockValueText(minimumStake)
    }

    fun showMinimumStakeFiat() {
        minimumStakeView.showWholeExtraBlock()
    }

    fun hideMinimumStakeFiat() {
        minimumStakeView.makeExtraBlockInvisible()
    }

    override fun storyClicked(story: StakingStoryModel) {
        storyItemHandler(story)
    }

    private fun changeState() {
        if (State.EXPANDED == currentState) {
            stakingNetworkInfoTitle.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_chevron_down_white, 0)
            stakingNetworkCollapsibleView.makeGone()
            currentState = State.COLLAPSED
        } else {
            stakingNetworkInfoTitle.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_chevron_up_white, 0)
            stakingNetworkCollapsibleView.makeVisible()
            currentState = State.EXPANDED
        }
    }
}
