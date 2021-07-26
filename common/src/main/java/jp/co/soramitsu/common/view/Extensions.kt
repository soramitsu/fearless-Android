package jp.co.soramitsu.common.view

import android.os.CountDownTimer
import android.widget.TextView
import jp.co.soramitsu.common.R
import kotlin.time.ExperimentalTime
import kotlin.time.milliseconds

@ExperimentalTime
fun TextView.startTimer(millis: Long, onFinish: ((view: TextView) -> Unit)? = null): CountDownTimer {
    return object : CountDownTimer(millis, 1000) {
        override fun onTick(millisUntilFinished: Long) {
            val days = millisUntilFinished.milliseconds.inDays.toInt()

            this@startTimer.text = if (days > 0)
                resources.getQuantityString(R.plurals.staking_payouts_days_left, days, days)
            else
                millisUntilFinished.formatTime()
        }

        override fun onFinish() {
            if (onFinish != null) {
                onFinish(this@startTimer)
            } else {
                this@startTimer.text = 0L.formatTime()
            }

            cancel()
        }
    }
}

fun Long.formatTime(): String {
    val totalSeconds = this / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}
