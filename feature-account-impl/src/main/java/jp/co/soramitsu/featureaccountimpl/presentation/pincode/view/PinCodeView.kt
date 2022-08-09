package jp.co.soramitsu.featureaccountimpl.presentation.pincode.view

import android.content.Context
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

    fun pinCodeMatchingError() {
        resetInput()
        shakeDotsAnimation()
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
}
