package jp.co.soramitsu.feature_account_impl.presentation.importing.source.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.annotation.LayoutRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import jp.co.soramitsu.common.utils.bindTo
import jp.co.soramitsu.common.view.InputField
import jp.co.soramitsu.common.view.LabeledTextView
import jp.co.soramitsu.feature_account_impl.presentation.common.mixin.api.chooseNetworkClicked
import jp.co.soramitsu.feature_account_impl.presentation.importing.ImportAccountViewModel
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.model.ImportSource

abstract class ImportSourceView @JvmOverloads constructor(
    @LayoutRes layoutId: Int,
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    protected abstract val networkInputView: LabeledTextView

    protected abstract val nameInputView: InputField

    init {
        View.inflate(context, layoutId, this)
    }

    abstract fun observeSource(source: ImportSource, lifecycleOwner: LifecycleOwner)

    fun observeCommon(viewModel: ImportAccountViewModel, lifecycleOwner: LifecycleOwner) {
        nameInputView.content.bindTo(viewModel.nameLiveData, lifecycleOwner)

        changeNetworkInputState(viewModel.isNetworkTypeChangeAvailable, networkInputView)

        networkInputView.setWholeClickListener {
            viewModel.chooseNetworkClicked()
        }

        viewModel.selectedNetworkLiveData.observe(lifecycleOwner, Observer {
            networkInputView.setTextIcon(it.networkTypeUI.icon)
            networkInputView.setMessage(it.name)
        })

        viewModel.networkChooserEnabledLiveData.observe(lifecycleOwner, Observer { enabled ->
            changeNetworkInputState(enabled, networkInputView)
        })
    }

    private fun changeNetworkInputState(enable: Boolean, networkInputView: LabeledTextView) {
        networkInputView.isEnabled = enable
    }
}