package jp.co.soramitsu.common.view

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import androidx.lifecycle.LifecycleOwner
import com.github.razir.progressbutton.bindProgressButton
import com.github.razir.progressbutton.hideProgress
import com.github.razir.progressbutton.showProgress
import jp.co.soramitsu.common.R

class PrimaryButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : AppCompatTextView(context, attrs, defStyle) {

    private var cachedText: String? = null

    enum class State(val viewEnabled: Boolean) {
        NORMAL(true),
        DISABLED(false),
        PROGRESS(false)
    }

    private var preparedForProgress = false

    fun prepareForProgress(lifecycleOwner: LifecycleOwner) {
        lifecycleOwner.bindProgressButton(this)

        preparedForProgress = true
    }

    fun setState(state: State) {
        isEnabled = state.viewEnabled

        if (state == State.PROGRESS) {
            checkPreparedForProgress()

            showProgress()
        } else {
            hideProgress()
        }
    }

    private fun checkPreparedForProgress() {
        if (!preparedForProgress) {
            throw IllegalArgumentException("You must call prepareForProgress() first!")
        }
    }

    private fun hideProgress() {
        cachedText?.let {
            hideProgress(it)
        }
    }

    private fun showProgress() {
        cachedText = text.toString()

        showProgress {
            progressColorRes = R.color.gray2
        }
    }
}