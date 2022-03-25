package jp.co.soramitsu.common.view.bottomSheet

import android.content.Context
import android.os.Bundle
import androidx.annotation.CallSuper
import androidx.annotation.StringRes
import com.google.android.material.bottomsheet.BottomSheetDialog
import jp.co.soramitsu.common.R
import kotlinx.android.synthetic.main.bottom_sheet_alert.alertConfirmButton
import kotlinx.android.synthetic.main.bottom_sheet_alert.alertMessage
import kotlinx.android.synthetic.main.bottom_sheet_alert.alertTitle

class AlertBottomSheet(
    context: Context,
    private val title: String,
    private val message: String,
    private val buttonText: String,
    private val cancelable: Boolean,
    private val callback: () -> Unit
) : BottomSheetDialog(context) {

    @CallSuper
    override fun onCreate(savedInstanceState: Bundle?) {
        setContentView(R.layout.bottom_sheet_alert)
        super.onCreate(savedInstanceState)

        alertTitle.text = title
        alertMessage.text = message
        alertConfirmButton.text = buttonText

        alertConfirmButton.setOnClickListener {
            callback()
            dismiss()
        }

        setCancelable(cancelable)
    }

    class Builder(private val context: Context) {

        private var title: String = ""
        private var message: String = ""
        private var buttonText: String = ""
        private var cancelable: Boolean = true
        private var callback: () -> Unit = {}

        fun setTitle(@StringRes titleRes: Int): Builder {
            title = context.resources.getString(titleRes)
            return this
        }

        fun setTitle(title: String): Builder {
            this.title = title
            return this
        }

        fun setMessage(@StringRes messageRes: Int): Builder {
            message = context.resources.getString(messageRes)
            return this
        }

        fun setMessage(message: String): Builder {
            this.message = message
            return this
        }

        fun setButtonText(@StringRes buttonTextRes: Int): Builder {
            buttonText = context.resources.getString(buttonTextRes)
            return this
        }

        fun setButtonText(buttonText: String): Builder {
            this.buttonText = buttonText
            return this
        }

        fun callback(callback: () -> Unit): Builder {
            this.callback = callback
            return this
        }

        fun setCancelable(cancelable: Boolean): Builder {
            this.cancelable = cancelable
            return this
        }

        fun build() = AlertBottomSheet(context, title, message, buttonText, cancelable, callback)
    }
}
