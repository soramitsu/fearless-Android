package jp.co.soramitsu.feature_staking_impl.presentation.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import jp.co.soramitsu.common.utils.dp
import jp.co.soramitsu.common.utils.updatePadding
import jp.co.soramitsu.common.view.shape.getCutCornerDrawable
import jp.co.soramitsu.feature_staking_impl.R

class ValidatorSummary @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : LinearLayout(context, attrs, defStyle) {

    init {
        View.inflate(context, R.layout.view_validator_summary, this)

        orientation = VERTICAL

        updatePadding(top = 15.dp, bottom = 24.dp, start = 16.dp, end = 16.dp)

        with(context) {
            background = getCutCornerDrawable(R.color.blurColor)
        }
    }

    private val Int.dp
        get() = dp(context)
}
