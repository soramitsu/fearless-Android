package jp.co.soramitsu.common.view

import android.content.Context
import android.os.Parcelable
import android.util.AttributeSet
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.databinding.ViewSegmentedButtonBinding
import kotlinx.android.parcel.Parcelize

class SegmentedButtonView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val binding: ViewSegmentedButtonBinding

    init {
        inflate(context, R.layout.view_segmented_button, this)
        binding = ViewSegmentedButtonBinding.bind(this)

        applyAttributes(attrs)
    }

    private var singleLine: Boolean = true
    private var selectedIndex: Int = 0
    private var selectedIndexListener: ((index: Int) -> Unit)? = null

    private fun applyAttributes(attrs: AttributeSet?) {
        attrs?.let {
            val typedArray = context.obtainStyledAttributes(attrs, R.styleable.SegmentedButtonView)

            val labelOne = typedArray.getString(R.styleable.SegmentedButtonView_labelOne)
            labelOne?.let(::setLabelOne)

            val labelTwo = typedArray.getString(R.styleable.SegmentedButtonView_labelTwo)
            labelTwo?.let(::setLabelTwo)

            singleLine = typedArray.getBoolean(R.styleable.SegmentedButtonView_android_singleLine, true)
            binding.optionOne.isSingleLine = singleLine
            binding.optionTwo.isSingleLine = singleLine

            putSelectionBackground()

            typedArray.recycle()
        }
    }

    fun getSelectedIndex() = selectedIndex

    fun setLabelOne(label: String) {
        binding.optionOne.text = label
    }

    fun setLabelTwo(label: String) {
        binding.optionTwo.text = label
    }

    fun toggle() {
        selectedIndex = (selectedIndex + 1).rem(2)
        selectedIndexListener?.invoke(selectedIndex)
        putSelectionBackground()
    }

    private fun putSelectionBackground() {
        val selectedRes = when (selectedIndex) {
            0 -> R.id.optionOne
            else -> R.id.optionTwo
        }

        val layoutParams = binding.selectedBackground.layoutParams as? LayoutParams
        layoutParams?.startToStart = selectedRes
        layoutParams?.endToEnd = selectedRes
        layoutParams?.topToTop = selectedRes
        layoutParams?.bottomToBottom = selectedRes
        binding.selectedBackground.layoutParams = layoutParams
    }

    override fun onSaveInstanceState(): Parcelable {
        val superState = super.onSaveInstanceState()
        return ExtendedState(superState, selectedIndex)
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        val myState = state as? ExtendedState
        super.onRestoreInstanceState(myState?.superSavedState ?: state)

        selectedIndex = myState?.selectedIndex ?: 0
        selectedIndexListener?.invoke(selectedIndex)
        putSelectionBackground()
    }

    fun setOnSelectionChangeListener(listener: ((index: Int) -> Unit)?) {
        selectedIndexListener = listener
    }

    @Parcelize
    private class ExtendedState(val superSavedState: Parcelable?, val selectedIndex: Int) : View.BaseSavedState(superSavedState), Parcelable
}
