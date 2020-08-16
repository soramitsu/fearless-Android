package jp.co.soramitsu.feature_account_impl.presentation.mnemonic.confirm.view

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
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

    private var wordClickListener: (MnemonicWordView, String) -> Unit = { _, _ -> }

    private val wordMargin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, WORD_MARGIN_DP, resources.displayMetrics).toInt()

    init {
        View.inflate(context, R.layout.view_mnemonic_container, this)
        setBackgroundResource(R.drawable.bg_mnemonic_container)
        orientation = VERTICAL
        applyAttributes(attrs)
    }

    private fun applyAttributes(attrs: AttributeSet?) {
        attrs?.let {
        }
    }

    fun populate(mnemonic: List<String>) {
        mnemonic.forEach {
            val mnemonicView = MnemonicWordView(context).apply {
                val params = FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                params.setMargins(wordMargin, wordMargin, wordMargin, wordMargin)
                layoutParams = params
                setWord(it)
                setOnClickListener { wordClickListener(this, getWord()) }
            }
            addWordView(mnemonicView)
        }
    }

    fun addWordView(word: MnemonicWordView) {
        val firstLineContainerWidth = firstLineContainer.width
        word.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
        val wordWidth = word.measuredWidth
        val childCount = firstLineContainer.childCount
        if (childCount > 0) {
            var lastChildRight = 0
            for (x in 0 until childCount) {
                val child = firstLineContainer.getChildAt(x)
                lastChildRight += child.measuredWidth
                lastChildRight += wordMargin * 2
            }
            val freeSpace = firstLineContainerWidth - lastChildRight - wordMargin * 2
            if (freeSpace > wordWidth) {
                Log.d("mylog", "first... wordWidth: $wordWidth, freeSpace: $freeSpace")
                firstLineContainer.addView(word)
            } else {
                val secondChildCount = secondLineContainer.childCount
                if (secondChildCount > 0) {
                    var lastSecondChildRight = 0
                    for (x in 0 until secondChildCount) {
                        val child = secondLineContainer.getChildAt(x)
                        lastSecondChildRight += child.measuredWidth
                        lastSecondChildRight += wordMargin * 2
                    }
                    val secondContainerWidth = firstLineContainer.width
                    val secondFreeSpace = secondContainerWidth - lastSecondChildRight - wordMargin * 2
                    if (secondFreeSpace > wordWidth) {
                        secondLineContainer.addView(word)
                    } else {
                        thirdLineContainer.addView(word)
                    }
                } else {
                    secondLineContainer.addView(word)
                }
            }
        } else {
            firstLineContainer.addView(word)
        }
    }

    fun setOnWordClickListener(clickListener: (MnemonicWordView, String) -> Unit) {
        wordClickListener = clickListener
    }
}