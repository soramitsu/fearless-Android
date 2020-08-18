package jp.co.soramitsu.feature_account_impl.presentation.mnemonic.confirm.view

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.FrameLayout
import jp.co.soramitsu.feature_account_impl.R

class MnemonicCollectorView @JvmOverloads constructor(
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

    fun populateWord(mnemonicWordView1: MnemonicWordView) {
        val mnemonicWordView = MnemonicWordView(context).apply {
            setWord(mnemonicWordView1.getWord())
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
        magic()
    }

    private fun magic() {
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

            Log.d("mylog", "line: $line, previousLineElementIndex: $previousLineElementIndex, nextLineElementIndex: $nextLineElementIndex")

            for (i in previousLineElementIndex until nextLineElementIndex) {
                val view = childViews[i]
                val viewWidth = view.width
                val previousView = childViews.getOrNull(i - 1)
                if (previousView == null) {
                    val viewLeft = wordMargin + dividedSpace
                    val viewRight = wordMargin + dividedSpace + view.width
                    Log.d("mylog", "first item in line")
                    view.layout(viewLeft.toInt(), view.top, viewRight.toInt(), view.bottom)
                } else {
                    val previousViewLine = elements[i - 1].line
                    val currentViewLine = elements[i].line
                    if (previousViewLine == currentViewLine) {
                        Log.d("mylog", "same line")
                        val viewLeft = previousView.right + wordMargin + wordMargin
                        val viewRight = previousView.right + wordMargin + wordMargin + view.width

                        view.layout(viewLeft, view.top, viewRight, view.bottom)
                    } else {
                        Log.d("mylog", "same line")
                        val viewLeft = wordMargin + dividedSpace
                        val viewRight = wordMargin + dividedSpace + view.width

                        view.layout(viewLeft.toInt(), view.top, viewRight.toInt(), view.bottom)
                        Log.d("mylog", "new line!")
                    }
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