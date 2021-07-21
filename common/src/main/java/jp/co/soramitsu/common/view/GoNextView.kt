package jp.co.soramitsu.common.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.utils.setTextOrHide
import jp.co.soramitsu.common.utils.setVisible
import kotlinx.android.synthetic.main.view_go_next.view.goNextActionImage
import kotlinx.android.synthetic.main.view_go_next.view.goNextBadgeText
import kotlinx.android.synthetic.main.view_go_next.view.goNextIcon
import kotlinx.android.synthetic.main.view_go_next.view.goNextProgress
import kotlinx.android.synthetic.main.view_go_next.view.goNextTitle

class GoNextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    init {
        View.inflate(context, R.layout.view_go_next, this)

        setBackgroundResource(R.drawable.bg_primary_list_item)

        attrs?.let(this::applyAttributes)
    }

    val icon: ImageView
        get() = goNextIcon

    val title: TextView
        get() = goNextTitle

    fun setInProgress(inProgress: Boolean) {
        isEnabled = !inProgress

        goNextActionImage.setVisible(!inProgress)
        goNextProgress.setVisible(inProgress)
    }

    fun setBadgeText(badgeText: String?) {
        goNextBadgeText.setTextOrHide(badgeText)
    }

    private fun applyAttributes(attributeSet: AttributeSet?) {
        val typedArray = context.obtainStyledAttributes(attributeSet, R.styleable.GoNextView)

        val titleDisplay = typedArray.getString(R.styleable.GoNextView_android_text)
        title.text = titleDisplay

        val inProgress = typedArray.getBoolean(R.styleable.GoNextView_inProgress, false)
        setInProgress(inProgress)

        val iconDrawable = typedArray.getDrawable(R.styleable.GoNextView_icon)
        icon.setImageDrawable(iconDrawable)

        val actionIconDrawable = typedArray.getDrawable(R.styleable.GoNextView_actionIcon)
        goNextActionImage.setImageDrawable(actionIconDrawable)

        typedArray.recycle()
    }
}
