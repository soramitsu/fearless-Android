package jp.co.soramitsu.feature_account_impl.presentation.mnemonic.confirm.view

import android.animation.LayoutTransition
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

        private const val ANIMATION_DURATION = 200L
    }

    private val wordMargin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, WORD_MARGIN_DP, resources.displayMetrics).toInt()

    private val elements = mutableListOf<Element>()

    private val removedViews = mutableListOf<MnemonicWordView>()

    private var minimumMeasuredHeight = 0

    init {
        View.inflate(context, R.layout.view_mnemonic_container, this)
        setBackgroundResource(R.drawable.bg_mnemonic_container)
        applyAttributes(attrs)

        layoutTransition.apply {
            setDuration(ANIMATION_DURATION)
            disableTransitionType(LayoutTransition.CHANGING)
            disableTransitionType(LayoutTransition.CHANGE_APPEARING)
            disableTransitionType(LayoutTransition.CHANGE_DISAPPEARING)
        }
    }

    private fun applyAttributes(attrs: AttributeSet?) {
        attrs?.let {
        }
    }

    fun disableWordDisappearAnimation() {
        layoutTransition.disableTransitionType(LayoutTransition.DISAPPEARING)
    }

    fun populateWithMnemonic(mnemonic: List<MnemonicWordView>) {
        elements.clear()
        mnemonic.forEach { populateWord(it) }
        minimumMeasuredHeight = elements.lastOrNull()?.let {
            val wordView = it.wordView
            (wordView.layoutParams as LayoutParams).topMargin + wordView.measuredHeight + wordMargin
        } ?: 0
    }

    fun removeWordView(mnemonicWordView: MnemonicWordView) {
        removedViews.add(mnemonicWordView)
        removeView(mnemonicWordView)
    }

    fun removeLastWord() {
        if (elements.isEmpty()) {
            return
        }
        val lastElement = elements.last()
        elements.remove(lastElement)
        removeView(lastElement.wordView)
    }

    fun restoreLastWord() {
        if (removedViews.isEmpty()) {
            return
        }
        val lastRemovedView = removedViews.last()
        removedViews.remove(lastRemovedView)
        addView(lastRemovedView)
    }

    fun resetView() {
        elements.clear()
        removeAllViews()
    }

    fun restoreAllWords() {
        if (removedViews.isEmpty()) {
            return
        }
        removedViews.forEach { addView(it) }
        removedViews.clear()
    }

    fun populateWord(mnemonicWordView: MnemonicWordView) {
        val layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            setMargins(wordMargin, wordMargin, wordMargin, wordMargin)
        }
        mnemonicWordView.layoutParams = layoutParams
        if (elements.isEmpty()) {
            elements.add(Element(mnemonicWordView, 1))
        } else {
            val lastElement = elements.last()
            val lastView = lastElement.wordView

            val lastViewTop = (lastView.layoutParams as LayoutParams).topMargin
            val lastChildRight = (lastView.layoutParams as LayoutParams).leftMargin + lastView.measuredWidth + wordMargin

            val freeSpace = width - lastChildRight
            val wordSpace = mnemonicWordView.measuredWidth + wordMargin * 2

            if (freeSpace > wordSpace) {
                val leftMargin = lastChildRight + wordMargin
                (mnemonicWordView.layoutParams as LayoutParams).setMargins(leftMargin, lastViewTop, wordMargin, wordMargin)
                elements.add(Element(mnemonicWordView, lastElement.line))
            } else {
                val lastViewBottom = (lastView.layoutParams as LayoutParams).topMargin + lastView.measuredHeight + wordMargin
                (mnemonicWordView.layoutParams as LayoutParams).setMargins(wordMargin, lastViewBottom + wordMargin, wordMargin, wordMargin)
                elements.add(Element(mnemonicWordView, lastElement.line + 1))
            }
        }
        addView(mnemonicWordView)
    }

    fun getMinimumMeasuredHeight(): Int {
        return minimumMeasuredHeight
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (elements.isEmpty()) return

        val maxLines = elements.maxBy { it.line }!!.line

        for (line in 1..maxLines) {
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
                val childView = elements[i].wordView
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