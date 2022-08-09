package jp.co.soramitsu.featureaccountimpl.presentation.mnemonic.confirm.view

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.databinding.ViewMnemonicWordBinding

class MnemonicWordView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    enum class ColorMode {
        LIGHT,
        DARK
    }

    private val binding: ViewMnemonicWordBinding

    init {
        inflate(context, R.layout.view_mnemonic_word, this)
        binding = ViewMnemonicWordBinding.bind(this)

        applyAttributes(attrs)
    }

    private fun applyAttributes(attrs: AttributeSet?) {
        attrs?.let {
            val typedArray = context.obtainStyledAttributes(attrs, R.styleable.MnemonicWordView)

            val word = typedArray.getString(R.styleable.MnemonicWordView_wordText)
            word?.let { setWord(it) }

            val mode = ColorMode.values()[typedArray.getInt(R.styleable.MnemonicWordView_colorMode, 0)]
            setColorMode(mode)

            typedArray.recycle()
        }
    }

    fun setWord(word: String) {
        binding.wordTv.text = word
    }

    fun getWord(): String {
        return binding.wordTv.text.toString()
    }

    fun setColorMode(colorMode: ColorMode) {
        val background = when (colorMode) {
            ColorMode.LIGHT -> R.drawable.bg_mnemonic_word_light
            ColorMode.DARK -> R.drawable.bg_mnemonic_word_dark
        }
        binding.wordTv.setBackgroundResource(background)
    }
}
