package jp.co.soramitsu.feature_account_impl.presentation.importing.source.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.annotation.LayoutRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.LifecycleOwner
import jp.co.soramitsu.feature_account_impl.presentation.importing.ImportAccountViewModel
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.model.ImportSource

abstract class ImportSourceView @JvmOverloads constructor(
    @LayoutRes layoutId: Int,
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    init {
        View.inflate(context, layoutId, this)
    }

    abstract fun observeSource(source: ImportSource, lifecycleOwner: LifecycleOwner)

    abstract fun observeCommon(viewModel: ImportAccountViewModel, lifecycleOwner: LifecycleOwner)
}