package jp.co.soramitsu.common.view

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import androidx.lifecycle.LifecycleOwner
import com.github.razir.progressbutton.bindProgressButton
import com.github.razir.progressbutton.hideProgress
import com.github.razir.progressbutton.isProgressActive
import com.github.razir.progressbutton.showProgress
import jp.co.soramitsu.common.R

enum class ButtonState(val viewEnabled: Boolean) {
    NORMAL(true),
    DISABLED(false),
    PROGRESS(false)
}

class PrimaryButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : AppCompatTextView(context, attrs, defStyle) {

    private var cachedText: String? = null

    private var preparedForProgress = false

    fun prepareForProgress(lifecycleOwner: LifecycleOwner) {
        lifecycleOwner.bindProgressButton(this)

        preparedForProgress = true
    }

    fun setState(state: ButtonState) {
        isEnabled = state.viewEnabled

        if (state == ButtonState.PROGRESS) {
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
        if (isProgressActive()) {
            hideProgress(cachedText)
        }
    }

    private fun showProgress() {
        if (isProgressActive()) return

        cachedText = text.toString()

        showProgress {
            progressColorRes = R.color.gray2
        }
    }
}