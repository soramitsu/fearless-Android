package jp.co.soramitsu.featureaccountimpl.presentation.importing.source.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.annotation.LayoutRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.LifecycleOwner
import jp.co.soramitsu.common.utils.bindTo
import jp.co.soramitsu.common.view.InputField
import jp.co.soramitsu.featureaccountimpl.presentation.importing.ImportAccountViewModel
import jp.co.soramitsu.featureaccountimpl.presentation.importing.source.model.ImportSource

abstract class ImportSourceView @JvmOverloads constructor(
    @LayoutRes layoutId: Int,
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    protected abstract val nameInputView: InputField

    init {
        View.inflate(context, layoutId, this)
    }

    abstract fun observeSource(source: ImportSource, lifecycleOwner: LifecycleOwner)

    fun observeCommon(viewModel: ImportAccountViewModel, lifecycleOwner: LifecycleOwner) {
        nameInputView.content.bindTo(viewModel.nameLiveData, lifecycleOwner)
    }
}
