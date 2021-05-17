package jp.co.soramitsu.feature_staking_impl.presentation.staking.main.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.recyclerview.widget.LinearLayoutManager
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.common.view.shape.getCutCornerDrawable
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.presentation.staking.main.StakingStoriesAdapter
import jp.co.soramitsu.feature_staking_impl.presentation.staking.main.model.StakingStoryModel
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

    companion object {
        private const val ANIMATION_DURATION = 220L
    }

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

        applyAttributes(attrs)

        stakingStoriesList.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        stakingStoriesList.adapter = storiesAdapter

        stakingNetworkInfoTitle.setOnClickListener { changeExpandableState() }
    }

    private fun applyAttributes(attrs: AttributeSet?) {
        attrs?.let {
            val typedArray = context.obtainStyledAttributes(attrs, R.styleable.NetworkInfoView)

            val isExpanded = typedArray.getBoolean(R.styleable.NetworkInfoView_expanded, true)
            if (isExpanded) expand() else collapse()

            typedArray.recycle()
        }
    }

    fun setTitle(title: String) {
        stakingNetworkInfoTitle.text = title
    }

    fun submitStories(stories: List<StakingStoryModel>) {
        storiesAdapter.submitList(stories)
    }

    fun showLoading() {
        totalStakeView.showLoading()
        minimumStakeView.showLoading()
        activeNominatorsView.showLoading()
        lockUpPeriodView.showLoading()
    }

    fun hideLoading() {
        totalStakeView.hideLoading()
        minimumStakeView.hideLoading()
        activeNominatorsView.hideLoading()
        lockUpPeriodView.hideLoading()
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

    private fun changeExpandableState() {
        if (State.EXPANDED == currentState) {
            collapse()
        } else {
            expand()
        }
    }

    private fun collapse() {
        stakingNetworkInfoTitle.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_chevron_down_white, 0)
        currentState = State.COLLAPSED
        stakingNetworkCollapsibleView.animate()
            .setDuration(ANIMATION_DURATION)
            .alpha(0f)
            .withEndAction { stakingNetworkCollapsibleView.makeGone() }
    }

    private fun expand() {
        stakingNetworkInfoTitle.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_chevron_up_white, 0)
        stakingNetworkCollapsibleView.makeVisible()
        currentState = State.EXPANDED
        stakingNetworkCollapsibleView.animate()
            .setDuration(ANIMATION_DURATION)
            .alpha(1f)
    }
}
