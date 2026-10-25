package jp.co.soramitsu.common.view.bottomSheet

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.CallSuper
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetBehavior
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.view.PrimaryButton

class AlertBottomSheet(
    context: Context,
    private val title: String,
    private val message: String,
    private val buttonText: String,
    private val cancelable: Boolean,
    private val callback: () -> Unit,
    private val dismissCallback: () -> Unit,
    private val secondaryButtonText: String? = null,
    private val secondaryCallback: () -> Unit = {},
    @DrawableRes private val iconRes: Int? = null
) : BottomSheetDialog(context) {

    @CallSuper
    override fun onStart() {
        super.onStart()
        // Long localized messages must scroll within the available screen instead of
        // leaving the actions below a collapsed, non-scrollable sheet.
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.skipCollapsed = true
    }

    @CallSuper
    override fun onCreate(savedInstanceState: Bundle?) {
        setContentView(R.layout.bottom_sheet_alert)
        super.onCreate(savedInstanceState)

        findViewById<TextView>(R.id.alertTitle)?.text = title
        findViewById<TextView>(R.id.alertMessage)?.text = message
        iconRes?.let { findViewById<ImageView>(R.id.alertIcon)?.setImageResource(it) }
        findViewById<PrimaryButton>(R.id.alertConfirmButton)?.text = buttonText

        findViewById<PrimaryButton>(R.id.alertConfirmButton)?.setOnClickListener {
            callback()
            dismiss()
        }
        // The secondary action keeps the sheet open, so returning from it
        // (for example from another screen) finds the same choices.
        findViewById<Button>(R.id.alertSecondaryButton)?.let { button ->
            button.isVisible = secondaryButtonText != null
            button.text = secondaryButtonText
            button.setOnClickListener { secondaryCallback() }
        }

        setOnDismissListener {
            dismissCallback()
        }
        setCancelable(cancelable)
    }

    class Builder(private val context: Context) {

        private var title: String = ""
        private var message: String = ""
        private var buttonText: String = ""
        private var cancelable: Boolean = true
        private var callback: () -> Unit = {}
        private var dismissCallback: () -> Unit = {}
        private var secondaryButtonText: String? = null
        private var secondaryCallback: () -> Unit = {}
        private var iconRes: Int? = null

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

        fun onDismiss(callback: () -> Unit): Builder {
            dismissCallback = callback
            return this
        }

        fun setSecondaryButton(@StringRes textRes: Int, callback: () -> Unit): Builder {
            secondaryButtonText = context.resources.getString(textRes)
            secondaryCallback = callback
            return this
        }

        fun setIcon(@DrawableRes iconRes: Int): Builder {
            this.iconRes = iconRes
            return this
        }

        fun build() = AlertBottomSheet(
            context,
            title,
            message,
            buttonText,
            cancelable,
            callback,
            dismissCallback,
            secondaryButtonText,
            secondaryCallback,
            iconRes
        )
    }
}
