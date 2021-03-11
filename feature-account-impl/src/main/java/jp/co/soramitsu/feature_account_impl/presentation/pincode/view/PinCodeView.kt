package jp.co.soramitsu.feature_account_impl.presentation.pincode.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.View.OnClickListener
import android.view.animation.AnimationUtils
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatButton
import jp.co.soramitsu.feature_account_impl.R
import kotlinx.android.synthetic.main.pincode_view.view.btn0
import kotlinx.android.synthetic.main.pincode_view.view.btn1
import kotlinx.android.synthetic.main.pincode_view.view.btn2
import kotlinx.android.synthetic.main.pincode_view.view.btn3
import kotlinx.android.synthetic.main.pincode_view.view.btn4
import kotlinx.android.synthetic.main.pincode_view.view.btn5
import kotlinx.android.synthetic.main.pincode_view.view.btn6
import kotlinx.android.synthetic.main.pincode_view.view.btn7
import kotlinx.android.synthetic.main.pincode_view.view.btn8
import kotlinx.android.synthetic.main.pincode_view.view.btn9
import kotlinx.android.synthetic.main.pincode_view.view.btnDelete
import kotlinx.android.synthetic.main.pincode_view.view.dotsProgressView
import kotlinx.android.synthetic.main.pincode_view.view.fingerprintBtn
import kotlinx.android.synthetic.main.pincode_view.view.pinCodeTitleTv

class PinCodeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : LinearLayout(context, attrs, defStyle) {

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
        View.inflate(context, R.layout.pincode_view, this)

        btn1.setOnClickListener(pinCodeNumberClickListener)
        btn2.setOnClickListener(pinCodeNumberClickListener)
        btn3.setOnClickListener(pinCodeNumberClickListener)
        btn4.setOnClickListener(pinCodeNumberClickListener)
        btn5.setOnClickListener(pinCodeNumberClickListener)
        btn6.setOnClickListener(pinCodeNumberClickListener)
        btn7.setOnClickListener(pinCodeNumberClickListener)
        btn8.setOnClickListener(pinCodeNumberClickListener)
        btn9.setOnClickListener(pinCodeNumberClickListener)
        btn0.setOnClickListener(pinCodeNumberClickListener)

        btnDelete.setOnClickListener(pinCodeDeleteClickListener)

        fingerprintBtn.setOnClickListener(pinCodeFingerprintClickListener)
    }

    fun changeFingerPrintButtonVisibility(isVisible: Boolean) {
        if (isVisible) {
            fingerprintBtn.visibility = View.VISIBLE
        } else {
            fingerprintBtn.visibility = View.INVISIBLE
        }
    }

    fun setTitle(title: String) {
        pinCodeTitleTv.text = title
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
        dotsProgressView.setProgress(currentProgress)
    }

    private fun shakeDotsAnimation() {
        val animation = AnimationUtils.loadAnimation(context, R.anim.shake)
        dotsProgressView.startAnimation(animation)
    }
}