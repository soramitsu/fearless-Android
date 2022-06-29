package jp.co.soramitsu.feature_staking_impl.presentation.staking.main.view

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.utils.makeInvisible
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.common.utils.setVisible
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.ViewStakingInfoBinding

class StakingInfoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : LinearLayout(context, attrs, defStyle) {

    private val binding: ViewStakingInfoBinding

    init {
        inflate(context, R.layout.view_staking_info, this)
        binding = ViewStakingInfoBinding.bind(this)

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
        binding.stakingInfoTitle.text = title
    }

    fun setTitleVisibility(isVisible: Boolean) {
        binding.stakingInfoTitle.setVisible(isVisible)
    }

    fun setTitleDetail(titleDetail: String) {
        binding.stakingInfoTitleDetail.text = titleDetail
    }

    fun setBody(body: String) {
        binding.stakingInfoBody.text = body
    }

    fun setExtraBlockValueText(text: String) {
        binding.stakingInfoExtraBlockValue.text = text
    }

    fun showExtraBlockValue() {
        binding.stakingInfoExtraBlockValue.makeVisible()
    }

    fun hideExtraBlockValue() {
        binding.stakingInfoExtraBlockValue.makeGone()
    }

    fun setExtraBlockAdditionalText(text: String) {
        binding.stakingInfoExtraTitleDetailView.makeVisible()

        binding.stakingInfoExtraBlockAdditional.text = text
    }

    fun showWholeExtraBlock() {
        binding.stakingInfoExtraBlock.makeVisible()
    }

    fun hideWholeExtraBlock() {
        binding.stakingInfoExtraBlock.makeGone()
    }

    fun makeExtraBlockInvisible() {
        binding.stakingInfoExtraBlock.makeInvisible()
    }

    fun showLoading() {
        binding.stakingInfoBodyShimmer.makeVisible()
        binding.stakingInfoBody.makeGone()
        binding.stakingInfoExtraBlockValue.makeGone()
        binding.stakingInfoExtraBlockShimmer.makeVisible()
    }

    fun hideLoading() {
        binding.stakingInfoBody.makeVisible()
        binding.stakingInfoBodyShimmer.makeGone()
        binding.stakingInfoExtraBlockValue.makeVisible()
        binding.stakingInfoExtraBlockShimmer.makeGone()
    }
}
