package jp.co.soramitsu.feature_account_impl.presentation.mnemonic.confirm.view

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import jp.co.soramitsu.feature_account_impl.R
import kotlinx.android.synthetic.main.view_mnemonic_container.view.firstLineContainer
import kotlinx.android.synthetic.main.view_mnemonic_container.view.secondLineContainer
import kotlinx.android.synthetic.main.view_mnemonic_container.view.thirdLineContainer

class MnemonicContainerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val WORD_MARGIN_DP = 4f
    }

    private val wordMargin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, WORD_MARGIN_DP, resources.displayMetrics).toInt()

    init {
        View.inflate(context, R.layout.view_mnemonic_container, this)
        setBackgroundResource(R.drawable.bg_mnemonic_container)
        orientation = VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        applyAttributes(attrs)
    }

    private fun applyAttributes(attrs: AttributeSet?) {
        attrs?.let {
        }
    }

    fun populate(mnemonic: List<String>, wordClickListener: (MnemonicWordView, String) -> Unit) {
        mnemonic.forEach {
            val mnemonicWordView = MnemonicWordView(context).apply {
                layoutParams = FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                    setMargins(wordMargin, wordMargin, wordMargin, wordMargin)
                }
                setWord(it)
            }
            mnemonicWordView.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
            addWordView(mnemonicWordView, wordClickListener)
        }
    }

    fun addWordView(word: MnemonicWordView, wordClickListener: (MnemonicWordView, String) -> Unit) {
        if (firstLineContainer.childCount == 0) {
            addViewToContainer(firstLineContainer, word, wordClickListener)
        } else {
            if (secondLineContainer.childCount == 0) {
                val spaceAvailable = checkAvailableSpaceForNewWord(word, firstLineContainer)
                if (spaceAvailable) {
                    addViewToContainer(firstLineContainer, word, wordClickListener)
                } else {
                    addViewToContainer(secondLineContainer, word, wordClickListener)
                }
            } else {
                if (thirdLineContainer.childCount == 0) {
                    val spaceAvailable = checkAvailableSpaceForNewWord(word, secondLineContainer)
                    if (spaceAvailable) {
                        addViewToContainer(secondLineContainer, word, wordClickListener)
                    } else {
                        addViewToContainer(thirdLineContainer, word, wordClickListener)
                    }
                } else {
                    addViewToContainer(thirdLineContainer, word, wordClickListener)
                }
            }
        }
    }

    private fun addViewToContainer(container: LinearLayout, word: MnemonicWordView, wordClickListener: (MnemonicWordView, String) -> Unit) {
        container.addView(word)
        word.setOnClickListener {
            container.removeView(it)
            wordClickListener(word, word.getWord())
            word.setOnClickListener { }
        }
    }

    private fun checkAvailableSpaceForNewWord(word: MnemonicWordView, container: LinearLayout): Boolean {
        return if (container.childCount == 0) {
            true
        } else {
            var lastChildRight = 0
            for (i in 0 until container.childCount) {
                val child = container.getChildAt(i)
                lastChildRight += child.measuredWidth
                lastChildRight += wordMargin * 2
            }
            val freeSpace = container.width - lastChildRight - wordMargin - wordMargin
            val wordSpace = word.measuredWidth
            freeSpace > wordSpace
        }
    }
}