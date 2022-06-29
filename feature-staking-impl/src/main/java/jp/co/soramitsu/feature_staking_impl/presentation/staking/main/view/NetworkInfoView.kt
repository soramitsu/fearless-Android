package jp.co.soramitsu.feature_staking_impl.presentation.staking.main.view

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import androidx.recyclerview.widget.LinearLayoutManager
import jp.co.soramitsu.common.presentation.StakingStoryModel
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.common.view.shape.getCutCornerDrawable
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.ViewNetworkInfoBinding
import jp.co.soramitsu.feature_staking_impl.presentation.staking.main.StakingStoriesAdapter

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

    private val binding: ViewNetworkInfoBinding

    var storyItemHandler: (StakingStoryModel) -> Unit = {}

    private val storiesAdapter = StakingStoriesAdapter(this)

    private var currentState = State.EXPANDED

    init {
        inflate(context, R.layout.view_network_info, this)
        binding = ViewNetworkInfoBinding.bind(this)

        with(context) {
            background = getCutCornerDrawable(R.color.blurColor)
        }

        orientation = VERTICAL

        applyAttributes(attrs)

        binding.stakingStoriesList.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        binding.stakingStoriesList.adapter = storiesAdapter

        binding.stakingNetworkInfoTitle.setOnClickListener { changeExpandableState() }
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
        binding.stakingNetworkInfoTitle.text = title
    }

    fun submitStories(stories: List<StakingStoryModel>) {
        storiesAdapter.submitList(stories)
    }

    fun showLoading() {
        binding.totalStakeView.showLoading()
        binding.minimumStakeView.showLoading()
        binding.activeNominatorsView.showLoading()
        binding.lockUpPeriodView.showLoading()
    }

    fun hideLoading() {
        binding.totalStakeView.hideLoading()
        binding.minimumStakeView.hideLoading()
        binding.activeNominatorsView.hideLoading()
        binding.lockUpPeriodView.hideLoading()
    }

    fun setTotalStake(totalStake: String) {
        binding.totalStakeView.setBody(totalStake)
    }

    fun setNominatorsCount(nominatorsCount: String) {
        binding.activeNominatorsView.setBody(nominatorsCount)
    }

    fun setMinimumStake(minimumStake: String) {
        binding.minimumStakeView.setBody(minimumStake)
    }

    fun setLockupPeriod(period: String) {
        binding.lockUpPeriodView.setBody(period)
    }

    fun setTotalStakeFiat(totalStake: String) {
        binding.totalStakeView.setExtraBlockValueText(totalStake)
    }

    fun showTotalStakeFiat() {
        binding.totalStakeView.showWholeExtraBlock()
    }

    fun hideTotalStakeFiat() {
        binding.totalStakeView.makeExtraBlockInvisible()
    }

    fun setMinimumStakeFiat(minimumStake: String) {
        binding.minimumStakeView.setExtraBlockValueText(minimumStake)
    }

    fun showMinimumStakeFiat() {
        binding.minimumStakeView.showWholeExtraBlock()
    }

    fun hideMinimumStakeFiat() {
        binding.minimumStakeView.makeExtraBlockInvisible()
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
        binding.stakingNetworkInfoTitle.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_chevron_down_white, 0)
        currentState = State.COLLAPSED
        binding.stakingNetworkCollapsibleView.animate()
            .setDuration(ANIMATION_DURATION)
            .alpha(0f)
            .withEndAction { binding.stakingNetworkCollapsibleView.makeGone() }
    }

    private fun expand() {
        binding.stakingNetworkInfoTitle.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_chevron_up_white, 0)
        binding.stakingNetworkCollapsibleView.makeVisible()
        currentState = State.EXPANDED
        binding.stakingNetworkCollapsibleView.animate()
            .setDuration(ANIMATION_DURATION)
            .alpha(1f)
    }
}
