package jp.co.soramitsu.account.impl.presentation.mnemonic.confirm.view

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.doOnAttach
import jp.co.soramitsu.common.compose.theme.fw.FwElevation
import jp.co.soramitsu.common.compose.theme.fw.FwRestyle
import jp.co.soramitsu.common.compose.theme.fw.FwShape
import jp.co.soramitsu.common.compose.theme.fw.FwSurface
import jp.co.soramitsu.common.compose.theme.fw.FwViews
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

            val mode = ColorMode.entries[typedArray.getInt(R.styleable.MnemonicWordView_colorMode, 0)]
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
        if (FwRestyle.isEnabled(FwSurface.Mnemonic)) {
            // A word to pick is a raised chip; a word already placed rests in the well.
            clipChildren = false
            doOnAttach { (parent as? ViewGroup)?.clipChildren = false }
            binding.wordTv.isDuplicateParentStateEnabled = true
            binding.wordTv.background = FwViews.surface(
                context,
                if (colorMode == ColorMode.LIGHT) FwElevation.Raised1 else FwElevation.Inset,
                FwShape.WELL.value
            )
            return
        }
        val background = when (colorMode) {
            ColorMode.LIGHT -> R.drawable.bg_mnemonic_word_light
            ColorMode.DARK -> R.drawable.bg_mnemonic_word_dark
        }
        binding.wordTv.setBackgroundResource(background)
    }
}
