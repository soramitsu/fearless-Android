package jp.co.soramitsu.common.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData

fun Activity.showToast(msg: String, duration: Int = Toast.LENGTH_LONG) {
    Toast.makeText(this, msg, duration).show()
}

fun Activity.showToast(@StringRes msg: Int, duration: Int = Toast.LENGTH_LONG) {
    Toast.makeText(this, msg, duration).show()
}

@SuppressLint("NewApi")
fun Activity.setBarColorBackground(colorId: Int) {
    window.statusBarColor = ContextCompat.getColor(this, colorId)
}

fun <T> MutableLiveData<T>.setValueIfNew(newValue: T) {
    if (this.value != newValue) value = newValue
}

fun <T> MutableLiveData<T>.postValueIfNew(newValue: T) {
    if (this.value != newValue) postValue(newValue)
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

fun Fragment.showBrowser(link: String) = requireContext().showBrowser(link)

fun Context.showBrowser(link: String) {
    val intent = Intent(Intent.ACTION_VIEW).apply { data = Uri.parse(link) }
    startActivity(intent)
}

fun Fragment.shareText(text: String) = requireContext().shareText(text)

fun Context.createSendEmailIntent(targetEmail: String, title: String) {
    val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
        putExtra(Intent.EXTRA_EMAIL, targetEmail)
        type = "message/rfc822"
        data = Uri.parse("mailto:$targetEmail")
    }
    startActivity(Intent.createChooser(emailIntent, title))
}