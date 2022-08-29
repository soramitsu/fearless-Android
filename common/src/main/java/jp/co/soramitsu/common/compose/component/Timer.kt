package jp.co.soramitsu.common.compose.component

import android.annotation.SuppressLint
import android.content.res.Resources
import android.os.CountDownTimer
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.view.formatTime
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@Composable
fun Timer(
    millis: Long,
    timeLeftTimestamp: Long? = null,
    extraMessage: String? = null,
    hideZeroTimer: Boolean = false,
    onFinish: (() -> Unit)? = null,
    modifier: Modifier,
    textAlign: TextAlign
) {
    val timer = remember { mutableStateOf<CountDownTimer?>(null) }
    val text = remember { mutableStateOf("") }

    if (millis <= 0L) {
        text.value = stringResource(id = R.string.parachain_staking_request_finished)
        return
    }
    val deltaTime = if (timeLeftTimestamp != null) System.currentTimeMillis() - timeLeftTimestamp else 0L

    val currentTimer = timer.value

    if (currentTimer is CountDownTimer) {
        currentTimer.cancel()
    }

    val newTimer = Timer(
        millis,
        deltaTime,
        hideZeroTimer,
        extraMessage,
        LocalContext.current.resources,
        textUpdate = {
            text.value = it
        },
        onFinish = {
            timer.value = null
        }
    )

    newTimer.start()

    timer.value = newTimer
    Text(text = text.value, modifier = modifier, textAlign = textAlign)
}

private class Timer(
    millis: Long,
    deltaTime: Long,
    private val hideZeroTimer: Boolean,
    private val extraMessage: String?,
    private val resources: Resources,
    private val textUpdate: (String) -> Unit,
    private val onFinish: () -> Unit
) :
    CountDownTimer(millis - deltaTime, 1000) {
    @SuppressLint("SetTextI18n")
    override fun onTick(millisUntilFinished: Long) {
        val days = millisUntilFinished.toDuration(DurationUnit.MILLISECONDS).toInt(DurationUnit.DAYS)

        val formattedTime = when {
            days > 0 -> resources.getQuantityString(jp.co.soramitsu.common.R.plurals.staking_payouts_days_left, days, days)
            hideZeroTimer && millisUntilFinished == 0L -> ""
            else -> millisUntilFinished.formatTime()
        }

        textUpdate("${extraMessage.orEmpty()} $formattedTime")
    }

    override fun onFinish() {
        textUpdate(0L.formatTime())
        cancel()
        onFinish()
    }
}
