package jp.co.soramitsu.common.view

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.utils.getDrawableCompat
import jp.co.soramitsu.common.utils.makeVisible
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

        background = context.getDrawableCompat(R.drawable.bg_input_shape_selector)

        applyAttributes(attrs)
    }

    private fun applyAttributes(attrs: AttributeSet?) {
        attrs?.let {
            val typedArray = context.obtainStyledAttributes(attrs, R.styleable.LabeledTextView)

            val actionIcon = typedArray.getDrawable(R.styleable.LabeledTextView_actionIcon)
            actionIcon?.let(::setActionIcon)

            val label = typedArray.getString(R.styleable.LabeledTextView_label)
            label?.let(::setLabel)

            val textIcon = typedArray.getDrawable(R.styleable.LabeledTextView_textIcon)
            textIcon?.let(::setTextIcon)

            typedArray.recycle()
        }
    }

    fun setLabel(label: String) {
        labeledTextLabel.text = label
    }

    fun setActionIcon(icon: Drawable) {
        labeledTextAction.setImageDrawable(icon)
    }

    fun setText(text: String) {
        labeledTextText.text = text
    }

    fun setTextIcon(icon: Drawable) {
        labeledTextIcon.makeVisible()
        labeledTextIcon.setImageDrawable(icon)
    }

    fun setActionClickListener(listener: (View) -> Unit) {
        labeledTextAction.setOnClickListener(listener)
    }
}