package jp.co.soramitsu.account.impl.presentation.pincode.view

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.StateListAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import androidx.compose.ui.graphics.toArgb
import androidx.core.animation.doOnEnd
import jp.co.soramitsu.common.compose.theme.fw.FwColors
import jp.co.soramitsu.common.compose.theme.fw.FwEffects
import jp.co.soramitsu.common.compose.theme.fw.FwElevation
import jp.co.soramitsu.common.compose.theme.fw.FwLight
import jp.co.soramitsu.common.compose.theme.fw.FwMotionDurations
import jp.co.soramitsu.common.compose.theme.fw.FwMotionKind
import jp.co.soramitsu.common.compose.theme.fw.FwNeuDrawable
import jp.co.soramitsu.common.compose.theme.fw.FwSunGlowDrawable
import jp.co.soramitsu.common.compose.theme.fw.motionPolicy
import android.util.AttributeSet
import android.view.View
import android.view.View.OnClickListener
import android.view.animation.AnimationUtils
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatButton
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.databinding.PincodeViewBinding

class PinCodeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : LinearLayout(context, attrs, defStyle) {

    private val binding: PincodeViewBinding

    var pinCodeEnteredListener: (String) -> Unit = {}
    var fingerprintClickListener: () -> Unit = {}

    private val pinCodeNumberClickListener = OnClickListener {
        pinNumberAdded((it as AppCompatButton).text.toString())
    }

    private val pinCodeDeleteClickListener = OnClickListener {
        deleteClicked()
    }

    private val pinCodeFingerprintClickListener = OnClickListener {
        fingerprintClickListener()
    }

    private var inputCode: String = ""

    init {
        inflate(context, R.layout.pincode_view, this)
        binding = PincodeViewBinding.bind(this)

        binding.btn1.setOnClickListener(pinCodeNumberClickListener)
        binding.btn2.setOnClickListener(pinCodeNumberClickListener)
        binding.btn3.setOnClickListener(pinCodeNumberClickListener)
        binding.btn4.setOnClickListener(pinCodeNumberClickListener)
        binding.btn5.setOnClickListener(pinCodeNumberClickListener)
        binding.btn6.setOnClickListener(pinCodeNumberClickListener)
        binding.btn7.setOnClickListener(pinCodeNumberClickListener)
        binding.btn8.setOnClickListener(pinCodeNumberClickListener)
        binding.btn9.setOnClickListener(pinCodeNumberClickListener)
        binding.btn0.setOnClickListener(pinCodeNumberClickListener)

        binding.btnDelete.setOnClickListener(pinCodeDeleteClickListener)

        binding.fingerprintBtn.setOnClickListener(pinCodeFingerprintClickListener)
    }

    fun changeFingerPrintButtonVisibility(isVisible: Boolean) {
        if (isVisible) {
            binding.fingerprintBtn.visibility = View.VISIBLE
        } else {
            binding.fingerprintBtn.visibility = View.INVISIBLE
        }
    }

    fun setTitle(title: String) {
        binding.pinCodeTitleTv.text = title
    }

    fun resetInput() {
        inputCode = ""
        updateProgress()
    }

    private var sunlit: FwEffects? = null

    /**
     * Sun-lit PIN pad (FW Pin surface): the approved badge as the sun above the title, neumorphic
     * keys that sink while pressed, dots that fill from the key-light side, and a two-shake wrong-PIN
     * signal. Ids, listeners and content descriptions are unchanged.
     */
    fun applySunlitStyle(fx: FwEffects, colors: FwColors) {
        sunlit = fx
        val density = resources.displayMetrics.density
        clipChildren = false
        clipToPadding = false
        if (resources.configuration.screenHeightDp >= MIN_HEIGHT_FOR_LOGO_DP) {
            val logo = ImageView(context).apply {
                setImageResource(jp.co.soramitsu.common.R.drawable.fw_brand_badge)
                background = FwSunGlowDrawable(colors, fx.glow)
                val pad = (SUN_GLOW_PADDING_DP * density).toInt()
                setPadding(pad, pad, pad, pad)
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            val size = ((LOGO_DP + SUN_GLOW_PADDING_DP * 2) * density).toInt()
            addView(logo, 0, LayoutParams(size, size).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = (8 * density).toInt()
            })
        }
        binding.pinCodeTitleTv.setTextColor(colors.textPrimary.toArgb())
        binding.pinCodeTitleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        binding.pinCodeTitleTv.typeface = Typeface.create(binding.pinCodeTitleTv.typeface, Typeface.BOLD)
        binding.dotsProgressView.applySunlitStyle(fx, colors)
        val keys = listOf(binding.btn1, binding.btn2, binding.btn3, binding.btn4, binding.btn5, binding.btn6,
            binding.btn7, binding.btn8, binding.btn9, binding.btn0)
        val roomy = resources.configuration.screenHeightDp >= MIN_HEIGHT_FOR_LOGO_DP
        for (key in keys + listOf(binding.fingerprintBtn, binding.btnDelete)) {
            if (roomy) key.layoutParams = key.layoutParams.apply {
                width = (ROOMY_KEY_DP * density).toInt()
                height = (ROOMY_KEY_DP * density).toInt()
            }
        }
        for (key in keys) {
            key.background = FwNeuDrawable(colors, fx, density, FwElevation.Raised2, shadowRoom = KEY_SHADOW_ROOM_DP * density)
            key.setTextColor(colors.textPrimary.toArgb())
            key.stateListAnimator = pressAnimator(fx)
            (key.parent as? ViewGroup)?.apply {
                clipChildren = false
                clipToPadding = false
                (parent as? ViewGroup)?.apply {
                    clipChildren = false
                    clipToPadding = false
                }
            }
        }
        for (control in listOf(binding.fingerprintBtn, binding.btnDelete)) {
            control.background = null
            control.imageTintList = ColorStateList.valueOf(colors.textSecondary.toArgb())
            control.stateListAnimator = pressAnimator(fx)
        }
    }

    // 90 ms in, spring back in 160 ms; nothing moves under reduced motion or Calm/Reduced effects.
    private fun pressAnimator(fx: FwEffects): StateListAnimator? {
        if (!fx.motionPolicy().moves(FwMotionKind.Press)) return null
        fun scale(to: Float, millis: Int) = AnimatorSet().apply {
            playTogether(ObjectAnimator.ofFloat(null, View.SCALE_X, to), ObjectAnimator.ofFloat(null, View.SCALE_Y, to))
            duration = millis.toLong()
        }
        return StateListAnimator().apply {
            addState(intArrayOf(android.R.attr.state_pressed), scale(FwLight.PRESS_SCALE, FwMotionDurations.PRESS_IN))
            addState(intArrayOf(), scale(1f, FwMotionDurations.PRESS_OUT).apply { interpolator = OvershootInterpolator(2f) })
        }
    }

    fun pinCodeMatchingError() {
        val fx = sunlit
        if (fx == null) {
            resetInput()
            shakeDotsAnimation()
            return
        }
        // Wrong PIN: the entered dots turn danger and shake twice (6 dp, 300 ms), then clear.
        binding.dotsProgressView.showError(true)
        val clear = Runnable {
            binding.dotsProgressView.showError(false)
            resetInput()
        }
        if (fx.motionPolicy().moves(FwMotionKind.Shake)) {
            val amplitude = FwMotionDurations.SHAKE_DP * resources.displayMetrics.density
            ObjectAnimator.ofFloat(binding.dotsProgressView, View.TRANSLATION_X, 0f, -amplitude, amplitude, -amplitude, amplitude, 0f).apply {
                duration = FwMotionDurations.SHAKE.toLong()
                doOnEnd { clear.run() }
                start()
            }
        } else {
            binding.dotsProgressView.postDelayed(clear, FwMotionDurations.SHAKE.toLong())
        }
    }

    private fun pinNumberAdded(number: String) {
        if (inputCode.length >= DotsProgressView.MAX_PROGRESS) {
            return
        } else {
            inputCode += number
            updateProgress()
        }
        if (inputCode.length == DotsProgressView.MAX_PROGRESS) {
            pinCodeEnteredListener(inputCode)
        }
    }

    private fun deleteClicked() {
        if (inputCode.isEmpty()) {
            return
        }
        inputCode = inputCode.substring(0, inputCode.length - 1)
        updateProgress()
    }

    private fun updateProgress() {
        val currentProgress = inputCode.length
        binding.dotsProgressView.setProgress(currentProgress)
    }

    private fun shakeDotsAnimation() {
        val animation = AnimationUtils.loadAnimation(context, R.anim.shake)
        binding.dotsProgressView.startAnimation(animation)
    }

    private companion object {
        const val MIN_HEIGHT_FOR_LOGO_DP = 680
        const val LOGO_DP = 84
        const val SUN_GLOW_PADDING_DP = 26
        const val KEY_SHADOW_ROOM_DP = 5f
        const val ROOMY_KEY_DP = 74
    }
}
