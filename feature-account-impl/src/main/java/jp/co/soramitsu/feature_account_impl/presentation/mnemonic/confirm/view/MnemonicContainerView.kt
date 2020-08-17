package jp.co.soramitsu.feature_account_impl.presentation.mnemonic.confirm.view

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.widget.FrameLayout
import jp.co.soramitsu.feature_account_impl.R

class MnemonicContainerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val WORD_MARGIN_DP = 4f
    }

    private val wordMargin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, WORD_MARGIN_DP, resources.displayMetrics).toInt()

    private val childViews = mutableListOf<MnemonicWordView>()

    init {
        View.inflate(context, R.layout.view_mnemonic_container, this)
        setBackgroundResource(R.drawable.bg_mnemonic_container)
        applyAttributes(attrs)
    }

    private fun applyAttributes(attrs: AttributeSet?) {
        attrs?.let {
        }
    }

    fun populate(mnemonic: List<String>, wordClickListener: (MnemonicWordView, String) -> Unit) {
        childViews.clear()
        mnemonic.forEach { addWordView(it, wordClickListener) }
    }

    private fun addWordView(word: String, wordClickListener: (MnemonicWordView, String) -> Unit) {
        val mnemonicWordView = MnemonicWordView(context).apply {
            setWord(word)
        }
        mnemonicWordView.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)

        mnemonicWordView.setOnClickListener {
            removeView(it)
            wordClickListener(it as MnemonicWordView, it.getWord())
        }

        if (childViews.isEmpty()) {
            mnemonicWordView.layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                setMargins(wordMargin, wordMargin, wordMargin, wordMargin)
            }
            addView(mnemonicWordView)
        } else {
            val lastView = getChildAt(childCount - 1)
            val lastViewTop = (lastView.layoutParams as LayoutParams).topMargin
            val lastChildRight = (lastView.layoutParams as LayoutParams).leftMargin + lastView.measuredWidth + wordMargin

            val freeSpace = width - lastChildRight - wordMargin - wordMargin
            val wordSpace = mnemonicWordView.measuredWidth

            if (freeSpace > wordSpace) {
                mnemonicWordView.layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                    setMargins(lastChildRight + wordMargin, lastViewTop, wordMargin, wordMargin)
                }
            } else {
                val lastViewBottom = (lastView.layoutParams as LayoutParams).topMargin + lastView.measuredHeight + wordMargin

                mnemonicWordView.layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                    setMargins(wordMargin, lastViewBottom + wordMargin, wordMargin, wordMargin)
                }
            }

            addView(mnemonicWordView)
        }
        childViews.add(mnemonicWordView)
    }

    fun populateWord(mnemonicWordView: MnemonicWordView) {
        mnemonicWordView.setOnClickListener { }

        if (childViews.isEmpty()) {
            mnemonicWordView.layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                setMargins(wordMargin, wordMargin, wordMargin, wordMargin)
            }
            addView(mnemonicWordView)
        } else {
            val lastView = getChildAt(childCount - 1)
            val lastViewTop = (lastView.layoutParams as LayoutParams).topMargin
            val lastChildRight = (lastView.layoutParams as LayoutParams).leftMargin + lastView.measuredWidth + wordMargin

            val freeSpace = (width - lastChildRight) * 2
            val wordSpace = mnemonicWordView.measuredWidth + wordMargin * 2

            if (freeSpace > wordSpace) {
                mnemonicWordView.layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                    setMargins(lastChildRight + wordMargin, lastViewTop, wordMargin, wordMargin)
                }
            } else {
                val lastViewBottom = (lastView.layoutParams as LayoutParams).topMargin + lastView.measuredHeight + wordMargin

                mnemonicWordView.layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                    setMargins(wordMargin, lastViewBottom + wordMargin, wordMargin, wordMargin)
                }
            }

            addView(mnemonicWordView)
        }
        childViews.add(mnemonicWordView)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        magic()
    }

    private fun magic() {
        if (childViews.isEmpty()) return

        val firstLineTop = (childViews.first().layoutParams as LayoutParams).topMargin
        var secondLineFirstWordIndex = childViews.indexOfFirst { (it.layoutParams as LayoutParams).topMargin != firstLineTop }

        if (secondLineFirstWordIndex == -1) {
            secondLineFirstWordIndex = childViews.size
        }

        var firstLineSpace = 0

        for (i in 0 until secondLineFirstWordIndex) {
            val childView = childViews[i]
            firstLineSpace += childView.measuredWidth + wordMargin * 2
        }

        val rightEmptySpace = (width - firstLineSpace).toFloat()
        val width = width

        for (i in 0 until secondLineFirstWordIndex) {
            val view = childViews[i]
            val viewWidth = view.measuredWidth
            val currentLeft = view.left
            val viewLeft = currentLeft + rightEmptySpace.div(2)
            val currentRight = view.right
            val viewRight = currentRight + rightEmptySpace.div(2)
            view.layout(viewLeft.toInt(), view.top, viewRight.toInt(), view.bottom)
        }

        if (childViews.size == secondLineFirstWordIndex) {
            return
        }

        val secondLineTop = (childViews[secondLineFirstWordIndex].layoutParams as LayoutParams).topMargin
        var thirdLineFirstWordIndex = childViews.indexOfFirst { (it.layoutParams as LayoutParams).topMargin != secondLineTop && (it.layoutParams as LayoutParams).topMargin != firstLineTop }

        if (thirdLineFirstWordIndex == -1) {
            thirdLineFirstWordIndex = childViews.size
        }

        var secondLineSpace = 0

        for (i in secondLineFirstWordIndex until thirdLineFirstWordIndex) {
            secondLineSpace += childViews[i].measuredWidth
            secondLineSpace += wordMargin * 2
        }

        val secondLineRightEmptySpace = width - secondLineSpace

        for (i in secondLineFirstWordIndex until thirdLineFirstWordIndex) {
            val view = childViews[i]
            val viewLeft = view.left + secondLineRightEmptySpace.div(2)
            val viewRight = view.right + secondLineRightEmptySpace.div(2)
            view.layout(viewLeft, view.top, viewRight, view.bottom)
        }

        if (childViews.size == thirdLineFirstWordIndex) {
            return
        }

        var thirdLineSpace = 0

        for (i in thirdLineFirstWordIndex until childViews.size) {
            thirdLineSpace += childViews[i].measuredWidth
            thirdLineSpace += wordMargin * 2
        }

        val thirdLineRightEmptySpace = (width - thirdLineSpace).toFloat()

        for (i in thirdLineFirstWordIndex until childViews.size) {
            val view = childViews[i]
            val viewLeft = view.left + thirdLineRightEmptySpace.div(2)
            val viewRight = view.right + thirdLineRightEmptySpace.div(2)
            view.layout(viewLeft.toInt(), view.top, viewRight.toInt(), view.bottom)
        }
    }
}