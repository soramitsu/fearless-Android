package jp.co.soramitsu.account.impl.presentation.importing.source.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.annotation.LayoutRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.LifecycleOwner
import jp.co.soramitsu.account.api.presentation.importing.ImportAccountType
import jp.co.soramitsu.account.impl.presentation.importing.ImportAccountViewModel
import jp.co.soramitsu.account.impl.presentation.importing.source.model.ImportSource
import jp.co.soramitsu.common.utils.bindTo
import jp.co.soramitsu.common.view.InputField

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

    abstract fun observeSource(source: ImportSource, blockchainType: ImportAccountType, lifecycleOwner: LifecycleOwner)

    fun observeCommon(viewModel: ImportAccountViewModel, lifecycleOwner: LifecycleOwner) {
        nameInputView.content.bindTo(viewModel.nameLiveData, lifecycleOwner)
    }
}
