package jp.co.soramitsu.common.view

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.constraintlayout.widget.ConstraintLayout
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.utils.getDrawableCompat
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.common.utils.setVisible
import jp.co.soramitsu.common.view.shape.addRipple
import jp.co.soramitsu.common.view.shape.getCutCornersStateDrawable
import kotlinx.android.synthetic.main.view_labeled_text.view.labeledTextAction
import kotlinx.android.synthetic.main.view_labeled_text.view.labeledTextIcon
import kotlinx.android.synthetic.main.view_labeled_text.view.labeledTextLabel
import kotlinx.android.synthetic.main.view_labeled_text.view.labeledTextText

class LabeledTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {
    init {
        View.inflate(context, R.layout.view_labeled_text, this)

        with(context) {
            background = addRipple(getCutCornersStateDrawable())
        }

        applyAttributes(attrs)
    }

    private var singleLine: Boolean = true

    private fun applyAttributes(attrs: AttributeSet?) {
        attrs?.let {
            val typedArray = context.obtainStyledAttributes(attrs, R.styleable.LabeledTextView)

            val label = typedArray.getString(R.styleable.LabeledTextView_label)
            label?.let(::setLabel)

            val message = typedArray.getString(R.styleable.LabeledTextView_message)
            message?.let(::setMessage)

            val textIcon = typedArray.getDrawable(R.styleable.LabeledTextView_textIcon)
            textIcon?.let(::setTextIcon)

            val enabled = typedArray.getBoolean(R.styleable.LabeledTextView_enabled, true)
            isEnabled = enabled

            val actionIcon = typedArray.getDrawable(R.styleable.LabeledTextView_actionIcon)
            actionIcon?.let(::setActionIcon)

            singleLine = typedArray.getBoolean(R.styleable.LabeledTextView_android_singleLine, true)
            labeledTextText.isSingleLine = singleLine

            typedArray.recycle()
        }
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)

        labeledTextAction.setVisible(enabled)
    }

    fun setLabel(label: String) {
        labeledTextLabel.text = label
    }

    fun setActionIcon(icon: Drawable) {
        labeledTextAction.setImageDrawable(icon)

        labeledTextAction.setVisible(true)
    }

    fun setMessage(@StringRes messageRes: Int) = setMessage(context.getString(messageRes))

    fun setMessage(text: String) {
        labeledTextText.text = text
    }

    fun setTextIcon(@DrawableRes iconRes: Int) = setTextIcon(context.getDrawableCompat(iconRes))

    fun setTextIcon(icon: Drawable) {
        labeledTextIcon.makeVisible()
        labeledTextIcon.setImageDrawable(icon)
    }

    fun setActionClickListener(listener: (View) -> Unit) {
        labeledTextAction.setOnClickListener(listener)
    }

    fun setWholeClickListener(listener: (View) -> Unit) {
        setOnClickListener(listener)

        setActionClickListener(listener)
    }
}
