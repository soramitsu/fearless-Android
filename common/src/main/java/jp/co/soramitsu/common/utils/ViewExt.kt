package jp.co.soramitsu.common.utils

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.LayoutRes
import androidx.core.content.ContextCompat

inline fun EditText.onTextChanged(crossinline listener: (String) -> Unit) {
    addTextChangedListener(object : TextWatcher {
        override fun afterTextChanged(s: Editable?) {
        }

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
        }

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            listener.invoke(s.toString())
        }
    })
}

fun ViewGroup.inflateChild(@LayoutRes id: Int): View {
    return LayoutInflater.from(context).run {
        inflate(id, this@inflateChild, false)
    }
}

fun TextView.setTextColorRes(@ColorRes colorRes: Int) = setTextColor(ContextCompat.getColor(context, colorRes))