package jp.co.soramitsu.feature_staking_impl.presentation.staking.main.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.utils.makeInvisible
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.common.utils.setVisible
import jp.co.soramitsu.feature_staking_impl.R
import kotlinx.android.synthetic.main.view_staking_info.view.*

class StakingInfoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : LinearLayout(context, attrs, defStyle) {

    init {
        View.inflate(context, R.layout.view_staking_info, this)

        orientation = VERTICAL

        attrs?.let { applyAttributes(it) }
    }

    private fun applyAttributes(attributeSet: AttributeSet) {
        val typedArray = context.obtainStyledAttributes(attributeSet, R.styleable.StakingInfoView)

        val title = typedArray.getString(R.styleable.StakingInfoView_titleText)
        title?.let { setTitle(title) }

        val showTitle = typedArray.getBoolean(R.styleable.StakingInfoView_showTitle, true)
        setTitleVisibility(showTitle)

        val titleDetailText = typedArray.getString(R.styleable.StakingInfoView_titleDetail)
        titleDetailText?.let { setTitleDetail(titleDetailText) }

        val includeExtraBlock = typedArray.getBoolean(R.styleable.StakingInfoView_includeExtraBlock, true)
        if (includeExtraBlock) {
            showWholeExtraBlock()
        } else {
            hideWholeExtraBlock()
        }

        val startWithLoading = typedArray.getBoolean(R.styleable.StakingInfoView_startWithLoading, false)
        if (startWithLoading) showLoading()

        typedArray.recycle()
    }

    fun setTitle(title: String) {
        stakingInfoTitle.text = title
    }

    fun setTitleVisibility(isVisible: Boolean) {
        stakingInfoTitle.setVisible(isVisible)
    }

    fun setTitleDetail(titleDetail: String) {
        stakingInfoTitleDetail.text = titleDetail
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
        stakingInfoExtraTitleDetailView.makeVisible()

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
        stakingInfoExtraBlockValue.makeGone()
        stakingInfoExtraBlockShimmer.makeVisible()
    }

    fun hideLoading() {
        stakingInfoBody.makeVisible()
        stakingInfoBodyShimmer.makeGone()
        stakingInfoExtraBlockValue.makeVisible()
        stakingInfoExtraBlockShimmer.makeGone()
    }
}
