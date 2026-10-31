package jp.co.soramitsu.wallet.impl.presentation.balance.list

import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import jp.co.soramitsu.account.api.domain.UniversalWalletPasswordNotice.Arrival
import jp.co.soramitsu.account.api.domain.UniversalWalletPasswordNotice.Outcome
import jp.co.soramitsu.account.api.domain.UniversalWalletPasswordNotice.Row
import jp.co.soramitsu.common.view.PrimaryButton
import jp.co.soramitsu.feature_wallet_impl.R

/** One wallet's old addresses for the notice, or what Switch did with them. */
data class UniversalPasswordNoticeState(val metaId: Long, val rows: List<Row>)

/**
 * The one short notice about a password wallet's addresses that its recovery words alone open:
 * each old address beside its password-protected one (selectable to copy), any funds that
 * reached an address the wallet left, then Switch or Later.
 */
class UniversalPasswordNoticeSheet(
    context: Context,
    private var rows: List<Row>,
    private val onSwitch: () -> Unit,
    private val onClosed: () -> Unit
) : BottomSheetDialog(context) {

    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        setContentView(R.layout.bottom_sheet_universal_password_notice)
        super.onCreate(savedInstanceState)
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.skipCollapsed = true
        findViewById<Button>(R.id.universalPasswordLater)?.setOnClickListener { dismiss() }
        findViewById<PrimaryButton>(R.id.universalPasswordSwitch)?.setOnClickListener {
            if (busy) return@setOnClickListener
            if (rows.all(::isSettled)) {
                dismiss()
            } else {
                busy = true
                render()
                onSwitch()
            }
        }
        setOnDismissListener { onClosed() }
        render()
    }

    /** Shows what Switch did for each old address; switched ones need nothing more. */
    fun showResult(result: List<Row>) {
        busy = false
        rows = result
        render()
    }

    private fun render() {
        val container = findViewById<LinearLayout>(R.id.universalPasswordRows) ?: return
        container.removeAllViews()
        rows.forEach { row ->
            container.addView(text(row.slot.networkName, R.color.white, bold = true, topMarginDp = 16))
            container.addView(text(context.getString(R.string.universal_password_notice_old, row.oldAddress), R.color.white_64))
            container.addView(text(context.getString(R.string.universal_password_notice_new, row.newAddress), R.color.white))
            row.arrival?.let { arrival ->
                container.addView(text(context.getString(arrival.kind.title()), R.color.colorAccent))
                arrival.balance?.let { container.addView(text(context.getString(R.string.universal_password_notice_balance, it), R.color.white)) }
                arrival.kind.advice()?.let { container.addView(text(context.getString(it), R.color.white_64)) }
            }
            row.outcome?.let { container.addView(text(context.getString(it.label()), R.color.colorAccent)) }
        }
        val allSwitched = rows.isNotEmpty() && rows.all(::isSettled)
        findViewById<PrimaryButton>(R.id.universalPasswordSwitch)?.text = context.getString(
            when {
                busy -> R.string.universal_password_notice_checking
                allSwitched -> R.string.common_ok
                else -> R.string.universal_password_notice_switch
            }
        )
        findViewById<Button>(R.id.universalPasswordLater)?.isEnabled = !busy
    }

    private fun text(value: String, colorRes: Int, bold: Boolean = false, topMarginDp: Int = 4) = TextView(context).apply {
        TextViewCompat.setTextAppearance(this, R.style.TextAppearance_Soramitsu_Body1)
        setTextColor(ContextCompat.getColor(context, colorRes))
        if (bold) setTypeface(typeface, Typeface.BOLD)
        // Addresses can be selected and copied to send the old funds to the new address.
        setTextIsSelectable(!bold)
        text = value
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = (topMarginDp * context.resources.displayMetrics.density).toInt()
        }
    }

    // Switched, or only reporting funds at an old address the slot already left.
    private fun isSettled(row: Row) = row.outcome == Outcome.SWITCHED || !row.switchable

    @StringRes
    private fun Arrival.Kind.title(): Int = when (this) {
        Arrival.Kind.OLD_ADDRESS, Arrival.Kind.OLD_ADDRESS_USED_AGAIN -> R.string.universal_password_notice_arrived_old
        Arrival.Kind.NEW_ADDRESS -> R.string.universal_password_notice_arrived_new
    }

    @StringRes
    private fun Arrival.Kind.advice(): Int? = when (this) {
        Arrival.Kind.OLD_ADDRESS -> R.string.universal_password_notice_move_old
        Arrival.Kind.OLD_ADDRESS_USED_AGAIN -> R.string.universal_password_notice_used_again
        Arrival.Kind.NEW_ADDRESS -> null
    }

    @StringRes
    private fun Outcome.label(): Int = when (this) {
        Outcome.SWITCHED -> R.string.universal_password_notice_switched
        Outcome.STILL_FUNDED -> R.string.universal_password_notice_still_funded
        Outcome.UNKNOWN -> R.string.universal_password_notice_unknown
        Outcome.IN_USE -> R.string.universal_password_notice_in_use
    }
}
