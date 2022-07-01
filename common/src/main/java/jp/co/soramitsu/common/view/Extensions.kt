package jp.co.soramitsu.common.view

import android.annotation.SuppressLint
import android.os.CountDownTimer
import android.widget.CompoundButton
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.utils.bindTo
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import kotlinx.coroutines.flow.MutableStateFlow

private val TIMER_TAG = R.string.common_time_left

fun TextView.startTimer(millis: Long, timeLeftTimestamp: Long? = null, extraMessage: String? = null, hideZeroTimer: Boolean = false, onFinish: ((view: TextView) -> Unit)? = null) {
    val deltaTime = if (timeLeftTimestamp != null) System.currentTimeMillis() - timeLeftTimestamp else 0L

    val currentTimer = getTag(TIMER_TAG)

    if (currentTimer is CountDownTimer) {
        currentTimer.cancel()
    }

    val newTimer = object : CountDownTimer(millis - deltaTime, 1000) {
        @SuppressLint("SetTextI18n")
        override fun onTick(millisUntilFinished: Long) {
            val days = millisUntilFinished.toDuration(DurationUnit.MILLISECONDS).toInt(DurationUnit.DAYS)
            if(hideZeroTimer){
                hashCode()
            }
            val formattedTime = when {
                days > 0 -> resources.getQuantityString(R.plurals.staking_payouts_days_left, days, days)
                hideZeroTimer && millisUntilFinished == 0L -> ""
                else -> millisUntilFinished.formatTime()
            }

            this@startTimer.text = "${extraMessage.orEmpty()} $formattedTime"
        }

        override fun onFinish() {
            if (onFinish != null) {
                onFinish(this@startTimer)
            } else {
                this@startTimer.text = 0L.formatTime()
            }

            cancel()

            setTag(TIMER_TAG, null)
        }
    }
    newTimer.start()

    setTag(TIMER_TAG, newTimer)
}

fun TextView.stopTimer() {
    val currentTimer = getTag(TIMER_TAG)

    if (currentTimer is CountDownTimer) {
        currentTimer.cancel()
        setTag(TIMER_TAG, null)
    }
}

fun Long.formatTime(): String {
    val totalSeconds = this / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}

fun <K> CompoundButton.bindFromMap(key: K, map: Map<out K, MutableStateFlow<Boolean>>, lifecycleScope: LifecycleCoroutineScope) {
    val source = map[key] ?: error("Cannot find $key source")

    bindTo(source, lifecycleScope)
}

fun Lifecycle.onResumeObserver(): LiveData<Boolean> {
    val liveData = MutableLiveData(false)
    addObserver(
        LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                liveData.value = true
            }
        }
    )
    return liveData
}
