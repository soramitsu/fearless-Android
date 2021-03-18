package jp.co.soramitsu.feature_staking_impl.presentation.staking.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.utils.makeInvisible
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.feature_staking_impl.R
import kotlinx.android.synthetic.main.view_staking_gain.view.stakingInfoExtraBlockValue
import kotlinx.android.synthetic.main.view_staking_gain.view.stakingInfoBody
import kotlinx.android.synthetic.main.view_staking_gain.view.stakingInfoBodyShimmer
import kotlinx.android.synthetic.main.view_staking_gain.view.stakingInfoExtraBlock
import kotlinx.android.synthetic.main.view_staking_gain.view.stakingInfoExtraBlockAdditional
import kotlinx.android.synthetic.main.view_staking_gain.view.stakingInfoExtraBlockShimmer
import kotlinx.android.synthetic.main.view_staking_gain.view.stakingInfoExtraBlockValueView
import kotlinx.android.synthetic.main.view_staking_gain.view.stakingInfoTitle

class StakingInfoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : LinearLayout(context, attrs, defStyle) {

    init {
        View.inflate(context, R.layout.view_staking_gain, this)

        orientation = VERTICAL

        attrs?.let { applyAttributes(it) }
    }

    private fun applyAttributes(attributeSet: AttributeSet) {
        val typedArray = context.obtainStyledAttributes(attributeSet, R.styleable.StakingInfoView)

        val title = typedArray.getString(R.styleable.StakingInfoView_titleText)
        title?.let { setTitle(title) }

        val includeExtraBlock = typedArray.getBoolean(R.styleable.StakingInfoView_includeExtraBlock, true)
        if (includeExtraBlock) {
            showWholeExtraBlock()
        } else {
            hideWholeExtraBlock()
        }

        typedArray.recycle()
    }

    fun setTitle(title: String) {
        stakingInfoTitle.text = title
    }

    fun setBody(body: String) {
        stakingInfoBody.text = body
    }

    fun setExtraBlockValueText(text: String) {
        stakingInfoExtraBlockValue.text = text
    }

    fun showExtraBlockValue() {
        stakingInfoExtraBlockValue.makeVisible()
    }

    fun hideExtraBlockValue() {
        stakingInfoExtraBlockValue.makeGone()
    }

    fun setExtraBlockAdditionalText(text: String) {
        stakingInfoExtraBlockAdditional.text = text
    }

    fun showWholeExtraBlock() {
        stakingInfoExtraBlock.makeVisible()
    }

    fun hideWholeExtraBlock() {
        stakingInfoExtraBlock.makeGone()
    }

    fun makeExtraBlockInvisible() {
        stakingInfoExtraBlock.makeInvisible()
    }

    fun showLoading() {
        stakingInfoBodyShimmer.makeVisible()
        stakingInfoBody.makeGone()
        stakingInfoExtraBlockValueView.makeGone()
        stakingInfoExtraBlockShimmer.makeVisible()
    }

    fun hideLoading() {
        stakingInfoBody.makeVisible()
        stakingInfoBodyShimmer.makeGone()
        stakingInfoExtraBlockValueView.makeVisible()
        stakingInfoExtraBlockShimmer.makeGone()
    }
}
