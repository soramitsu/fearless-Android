package jp.co.soramitsu.common.view

import android.content.Context
import android.util.AttributeSet
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.databinding.ViewGoNextBinding
import jp.co.soramitsu.common.utils.setTextOrHide
import jp.co.soramitsu.common.utils.setVisible

class GoNextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val binding: ViewGoNextBinding

    init {
        inflate(context, R.layout.view_go_next, this)
        binding = ViewGoNextBinding.bind(this)

        setBackgroundResource(R.drawable.bg_primary_list_item)

        attrs?.let(this::applyAttributes)
    }

    val icon: ImageView
        get() = binding.goNextIcon

    val title: TextView
        get() = binding.goNextTitle

    fun setInProgress(inProgress: Boolean) {
        isEnabled = !inProgress

        binding.goNextActionImage.setVisible(!inProgress)
        binding.goNextProgress.setVisible(inProgress)
    }

    fun setBadgeText(badgeText: String?) {
        binding.goNextBadgeText.setTextOrHide(badgeText)
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
        binding.goNextActionImage.setImageDrawable(actionIconDrawable)

        typedArray.recycle()
    }
}
