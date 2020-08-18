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
    private val elements = mutableListOf<Element>()

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
        elements.clear()
        mnemonic.forEach { populateWord(it, wordClickListener) }
    }

    fun removeWordView(mnemonicWordView: MnemonicWordView) {
        removeView(mnemonicWordView)
    }

    fun populateWord(mnemonicWord: String, wordClickListener: (MnemonicWordView, String) -> Unit) {
        val mnemonicWordView = MnemonicWordView(context).apply {
            setWord(mnemonicWord)
            setOnClickListener {
                wordClickListener(this, mnemonicWord)
            }
        }
        mnemonicWordView.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
        mnemonicWordView.layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            setMargins(wordMargin, wordMargin, wordMargin, wordMargin)
        }
        if (childViews.isEmpty()) {
            elements.add(Element(mnemonicWordView, 1))
        } else {
            val lastView = childViews.last()
            val lastElement = elements.last()

            val lastViewTop = (lastView.layoutParams as LayoutParams).topMargin
            val lastChildRight = (lastView.layoutParams as LayoutParams).leftMargin + lastView.measuredWidth + wordMargin

            val freeSpace = width - lastChildRight
            val wordSpace = mnemonicWordView.measuredWidth + wordMargin * 2

            if (freeSpace > wordSpace) {
                val leftMargin = lastChildRight + wordMargin
                mnemonicWordView.layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                    setMargins(leftMargin, lastViewTop, wordMargin, wordMargin)
                }
                elements.add(Element(mnemonicWordView, lastElement.line))
            } else {
                val lastViewBottom = (lastView.layoutParams as LayoutParams).topMargin + lastView.measuredHeight + wordMargin
                mnemonicWordView.layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                    setMargins(wordMargin, lastViewBottom + wordMargin, wordMargin, wordMargin)
                }
                elements.add(Element(mnemonicWordView, lastElement.line + 1))
            }
        }
        childViews.add(mnemonicWordView)
        addView(mnemonicWordView)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (childViews.isEmpty()) return

        for (line in 1..3) {
            var previousLineElementIndex = elements.indexOfLast { it.line == line - 1 } + 1

            if (previousLineElementIndex == -1) {
                previousLineElementIndex = 0
            }

            var nextLineElementIndex = elements.indexOfFirst { it.line == line + 1 }

            if (nextLineElementIndex == -1) {
                nextLineElementIndex = elements.size
            }

            var currentLineSpace = 0

            for (i in previousLineElementIndex until nextLineElementIndex) {
                val childView = childViews[i]
                currentLineSpace += childView.width + wordMargin + wordMargin
            }

            val rightEmptySpace = (width - currentLineSpace).toFloat()
            val dividedSpace = rightEmptySpace.div(2)

            for (i in previousLineElementIndex until nextLineElementIndex) {
                val currentElement = elements[i]
                val previousElement = elements.getOrNull(i - 1)
                val shouldStartNewLine = previousElement?.let { it.line < currentElement.line } ?: true
                if (shouldStartNewLine) {
                    val viewLeft = wordMargin + dividedSpace
                    val viewRight = wordMargin + dividedSpace + currentElement.wordView.width
                    currentElement.wordView.layout(viewLeft.toInt(), currentElement.wordView.top, viewRight.toInt(), currentElement.wordView.bottom)
                } else {
                    val viewLeft = previousElement!!.wordView.right + wordMargin + wordMargin
                    val viewRight = previousElement.wordView.right + wordMargin + wordMargin + currentElement.wordView.width

                    currentElement.wordView.layout(viewLeft, currentElement.wordView.top, viewRight, currentElement.wordView.bottom)
                }
            }

            if (nextLineElementIndex == elements.size) {
                break
            }
        }
    }

    data class Element(
        val wordView: MnemonicWordView,
        val line: Int
    )
}