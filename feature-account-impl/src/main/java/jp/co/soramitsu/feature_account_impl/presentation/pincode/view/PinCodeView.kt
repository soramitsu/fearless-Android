package jp.co.soramitsu.feature_account_impl.presentation.pincode.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.View.OnClickListener
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
import kotlinx.android.synthetic.main.pincode_view.view.fingerprintBtn

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
}