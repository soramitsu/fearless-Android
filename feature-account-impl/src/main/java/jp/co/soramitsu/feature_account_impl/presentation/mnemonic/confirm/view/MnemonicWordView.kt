package jp.co.soramitsu.feature_account_impl.presentation.mnemonic.confirm.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import jp.co.soramitsu.feature_account_impl.R
import kotlinx.android.synthetic.main.view_mnemonic_word.view.wordTv

class MnemonicWordView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    init {
        View.inflate(context, R.layout.view_mnemonic_word, this)
        applyAttributes(attrs)
    }

    private fun applyAttributes(attrs: AttributeSet?) {
        attrs?.let {
            val typedArray = context.obtainStyledAttributes(attrs, R.styleable.MnemonicWordView)

            val word = typedArray.getString(R.styleable.MnemonicWordView_wordText)
            word?.let { setWord(it) }

            typedArray.recycle()
        }
    }

    fun setWord(word: String) {
        wordTv.text = word
    }

    fun getWord(): String {
        return wordTv.text.toString()
    }
}