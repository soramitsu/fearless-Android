package jp.co.soramitsu.common.view

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.PictureDrawable
import android.util.AttributeSet
import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.constraintlayout.widget.ConstraintLayout
import coil.ImageLoader
import coil.load
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.databinding.ViewLabeledTextBinding
import jp.co.soramitsu.common.utils.getDrawableCompat
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.common.utils.setVisible
import jp.co.soramitsu.common.view.shape.addRipple
import jp.co.soramitsu.common.view.shape.getCutCornersStateDrawable

class LabeledTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val binding: ViewLabeledTextBinding

    init {
        inflate(context, R.layout.view_labeled_text, this)
        binding = ViewLabeledTextBinding.bind(this)

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
            binding.labeledTextText.isSingleLine = singleLine

            typedArray.recycle()
        }
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)

        binding.labeledTextAction.setVisible(enabled)
    }

    fun setLabel(label: String) {
        binding.labeledTextLabel.text = label
    }

    fun setLabel(@StringRes label: Int) {
        binding.labeledTextLabel.setText(label)
    }

    fun setActionIcon(icon: Drawable) {
        binding.labeledTextAction.setImageDrawable(icon)
    }

    fun setMessage(@StringRes messageRes: Int) = setMessage(context.getString(messageRes))

    fun setMessage(text: String) {
        binding.labeledTextText.text = text
    }

    fun setTextIcon(@DrawableRes iconRes: Int) = setTextIcon(context.getDrawableCompat(iconRes))

    fun setTextIcon(icon: Drawable) {
        binding.labeledTextIcon.makeVisible()
        binding.labeledTextIcon.setImageDrawable(icon)
    }

    fun loadIcon(imageUrl: String, imageLoader: ImageLoader) {
        binding.labeledTextIcon.makeVisible()
        binding.labeledTextIcon.load(imageUrl, imageLoader)
    }

    fun loadIcon(pictureDrawable: PictureDrawable) {
        binding.labeledTextIcon.makeVisible()
        binding.labeledTextIcon.setImageDrawable(pictureDrawable)
    }

    fun setActionClickListener(listener: (View) -> Unit) {
        binding.labeledTextAction.setOnClickListener(listener)
    }

    fun setWholeClickListener(listener: (View) -> Unit) {
        setOnClickListener(listener)

        setActionClickListener(listener)
    }
}
