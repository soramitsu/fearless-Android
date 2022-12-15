package jp.co.soramitsu.common.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import java.net.URLEncoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow

fun Activity.showToast(msg: String, duration: Int = Toast.LENGTH_LONG) {
    Toast.makeText(this, msg, duration).show()
}

fun Fragment.showToast(msg: String, duration: Int = Toast.LENGTH_LONG) {
    context?.let {
        Toast.makeText(it, msg, duration).show()
    }
}

fun <T> MutableLiveData<T>.setValueIfNew(newValue: T) {
    if (this.value != newValue) value = newValue
}

fun View.makeVisible() {
    this.visibility = View.VISIBLE
}

fun View.makeInvisible() {
    this.visibility = View.INVISIBLE
}

fun View.makeGone() {
    this.visibility = View.GONE
}

fun Fragment.hideKeyboard() {
    requireActivity().currentFocus?.hideSoftKeyboard()
}

fun Fragment.showBrowser(link: String) = requireContext().showBrowser(link)

fun Context.showBrowser(link: String) {
    val intent = Intent(Intent.ACTION_VIEW).apply { data = Uri.parse(link) }
    startActivity(intent)
}

fun Context.createSendEmailIntent(targetEmail: String, title: String) {
    val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
        putExtra(Intent.EXTRA_EMAIL, targetEmail)
        type = "message/rfc822"
        data = Uri.parse("mailto:$targetEmail")
    }
    startActivity(Intent.createChooser(emailIntent, title))
}

fun @receiver:ColorInt Int.toHexColor(): String {
    val withoutAlpha = 0xFFFFFF and this

    return "#%06X".format(withoutAlpha)
}

fun String.urlEncoded() = URLEncoder.encode(this, Charsets.UTF_8.displayName())

fun CoroutineScope.childScope(supervised: Boolean = true): CoroutineScope {
    val parentJob = coroutineContext[Job]

    val job = if (supervised) SupervisorJob(parent = parentJob) else Job(parent = parentJob)

    return CoroutineScope(coroutineContext + job)
}

fun Int.asBoolean() = this != 0

private val CAMEL_CASE_REGEX = "(?<=[a-z])(?=[A-Z])".toRegex()
fun String.camelCaseToCapitalizedWords() = CAMEL_CASE_REGEX.split(this).joinToString(separator = " ") { it.capitalize() }

inline fun <T1, T2, T3, T4, T5, T6, R> combine(
    flow: Flow<T1>,
    flow2: Flow<T2>,
    flow3: Flow<T3>,
    flow4: Flow<T4>,
    flow5: Flow<T5>,
    flow6: Flow<T6>,
    crossinline transform: suspend (T1, T2, T3, T4, T5, T6) -> R
): Flow<R> {
    return kotlinx.coroutines.flow.combine(flow, flow2, flow3, flow4, flow5, flow6) { args: Array<*> ->
        @Suppress("UNCHECKED_CAST")
        transform(
            args[0] as T1,
            args[1] as T2,
            args[2] as T3,
            args[3] as T4,
            args[4] as T5,
            args[5] as T6
        )
    }
}

inline fun <T1, T2, T3, T4, T5, T6, T7, R> combine(
    flow: Flow<T1>,
    flow2: Flow<T2>,
    flow3: Flow<T3>,
    flow4: Flow<T4>,
    flow5: Flow<T5>,
    flow6: Flow<T6>,
    flow7: Flow<T7>,
    crossinline transform: suspend (T1, T2, T3, T4, T5, T6, T7) -> R
): Flow<R> {
    return kotlinx.coroutines.flow.combine(flow, flow2, flow3, flow4, flow5, flow6, flow7) { args: Array<*> ->
        @Suppress("UNCHECKED_CAST")
        transform(
            args[0] as T1,
            args[1] as T2,
            args[2] as T3,
            args[3] as T4,
            args[4] as T5,
            args[5] as T6,
            args[6] as T7
        )
    }
}

inline fun <T1, T2, T3, T4, T5, T6, T7, T8, R> combine(
    flow: Flow<T1>,
    flow2: Flow<T2>,
    flow3: Flow<T3>,
    flow4: Flow<T4>,
    flow5: Flow<T5>,
    flow6: Flow<T6>,
    flow7: Flow<T7>,
    flow8: Flow<T8>,
    crossinline transform: suspend (T1, T2, T3, T4, T5, T6, T7, T8) -> R
): Flow<R> {
    return kotlinx.coroutines.flow.combine(flow, flow2, flow3, flow4, flow5, flow6, flow7, flow8) { args: Array<*> ->
        @Suppress("UNCHECKED_CAST")
        transform(
            args[0] as T1,
            args[1] as T2,
            args[2] as T3,
            args[3] as T4,
            args[4] as T5,
            args[5] as T6,
            args[6] as T7,
            args[7] as T8
        )
    }
}

val String.Companion.ZERO: String
    get() = "0"

val Char.Companion.ZERO: Char
    get() = '0'
