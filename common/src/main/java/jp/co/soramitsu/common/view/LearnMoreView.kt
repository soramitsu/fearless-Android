package jp.co.soramitsu.common.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import jp.co.soramitsu.common.R
import kotlinx.android.synthetic.main.view_learn_more.view.learnMoreIcon
import kotlinx.android.synthetic.main.view_learn_more.view.learnMoreTitle

class LearnMoreView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    init {
        View.inflate(context, R.layout.view_learn_more, this)

        setBackgroundResource(R.drawable.bg_primary_list_item)
    }

    val icon: ImageView
        get() = learnMoreIcon

    val title: TextView
        get() = learnMoreTitle
}
