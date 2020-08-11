package jp.co.soramitsu.feature_account_impl.presentation.pincode.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.View.OnClickListener
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatButton
import jp.co.soramitsu.sora_ui.R
import kotlinx.android.synthetic.main.uikit_view_pin_code.view.btn0
import kotlinx.android.synthetic.main.uikit_view_pin_code.view.btn1
import kotlinx.android.synthetic.main.uikit_view_pin_code.view.btn2
import kotlinx.android.synthetic.main.uikit_view_pin_code.view.btn3
import kotlinx.android.synthetic.main.uikit_view_pin_code.view.btn4
import kotlinx.android.synthetic.main.uikit_view_pin_code.view.btn5
import kotlinx.android.synthetic.main.uikit_view_pin_code.view.btn6
import kotlinx.android.synthetic.main.uikit_view_pin_code.view.btn7
import kotlinx.android.synthetic.main.uikit_view_pin_code.view.btn8
import kotlinx.android.synthetic.main.uikit_view_pin_code.view.btn9
import kotlinx.android.synthetic.main.uikit_view_pin_code.view.btnDelete
import kotlinx.android.synthetic.main.uikit_view_pin_code.view.fingerprintBtn

class PinCodeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : LinearLayout(context, attrs, defStyle) {

    var pinCodeListener: (String) -> Unit = {}
    var deleteClickListener: () -> Unit = {}
    var fingerprintClickListener: () -> Unit = {}

    init {
        orientation = VERTICAL
    }

    private val pinCodeNumberClickListener = OnClickListener {
        pinCodeListener((it as AppCompatButton).text.toString())
    }

    private val pinCodeDeleteClickListener = OnClickListener {
        deleteClickListener()
    }

    private val pinCodeFingerprintClickListener = OnClickListener {
        fingerprintClickListener()
    }

    init {
        View.inflate(context, R.layout.uikit_view_pin_code, this)

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

    fun changeDeleteButtonVisibility(isVisible: Boolean) {
        if (isVisible) {
            btnDelete.animate()
                .withStartAction { btnDelete.visibility = View.VISIBLE }
                .alpha(1.0f)
                .start()
        } else {
            btnDelete.animate()
                .withEndAction { btnDelete.visibility = View.INVISIBLE }
                .alpha(0.0f)
                .start()
        }
    }

    fun changeFingerPrintButtonVisibility(isVisible: Boolean) {
        if (isVisible) {
            fingerprintBtn.visibility = View.VISIBLE
        } else {
            fingerprintBtn.visibility = View.INVISIBLE
        }
    }
}