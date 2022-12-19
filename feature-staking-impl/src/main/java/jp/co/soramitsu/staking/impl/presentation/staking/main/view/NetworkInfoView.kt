package jp.co.soramitsu.staking.impl.presentation.staking.main.view

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.common.presentation.StakingStoryModel
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.common.view.shape.getCutCornerDrawable
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.presentation.staking.main.StakingStoriesAdapter

abstract class NetworkInfoView @JvmOverloads constructor(
    context: Context,
    private val attrs: AttributeSet? = null
) : LinearLayout(context, attrs), StakingStoriesAdapter.StoryItemHandler {

    companion object {
        private const val ANIMATION_DURATION = 220L
    }

    enum class State {
        EXPANDED,
        COLLAPSED
    }

    var storyItemHandler: (StakingStoryModel) -> Unit = {}

    private val storiesAdapter = StakingStoriesAdapter(object : StakingStoriesAdapter.StoryItemHandler {
        override fun storyClicked(story: StakingStoryModel) {
            storyItemHandler(story)
        }
    })

    private var currentState = State.EXPANDED

    protected abstract val storiesList: RecyclerView
    protected abstract val infoTitle: TextView
    protected abstract val collapsibleView: ConstraintLayout

    init {
        with(context) {
            background = getCutCornerDrawable(R.color.blurColor)
        }

        orientation = VERTICAL
    }

    protected fun setup() {
        setupViews()
        applyAttributes(attrs)
    }

    private fun setupViews() {
        storiesList.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        storiesList.adapter = storiesAdapter

        infoTitle.setOnClickListener { changeExpandableState() }
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
        infoTitle.text = resources.getString(R.string.staking_main_network_title, title)
    }

    fun submitStories(stories: List<StakingStoryModel>) {
        storiesAdapter.submitList(stories)
    }

    abstract fun showLoading()

    abstract fun hideLoading()

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
        infoTitle.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_chevron_down_white, 0)
        currentState = State.COLLAPSED
        collapsibleView.animate()
            .setDuration(ANIMATION_DURATION)
            .alpha(0f)
            .withEndAction { collapsibleView.makeGone() }
    }

    private fun expand() {
        infoTitle.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_chevron_up_white, 0)
        collapsibleView.makeVisible()
        currentState = State.EXPANDED
        collapsibleView.animate()
            .setDuration(ANIMATION_DURATION)
            .alpha(1f)
    }
}
