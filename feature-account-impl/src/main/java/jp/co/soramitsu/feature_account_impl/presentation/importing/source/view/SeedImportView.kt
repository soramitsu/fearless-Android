package jp.co.soramitsu.feature_account_impl.presentation.importing.source.view

import android.content.Context
import android.util.AttributeSet
import androidx.lifecycle.LifecycleOwner
import jp.co.soramitsu.common.utils.bindTo
import jp.co.soramitsu.common.utils.getDrawableCompat
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.model.ImportSource
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.model.RawSeedImportSource
import kotlinx.android.synthetic.main.import_source_seed.view.importSeedContent

class SeedImportView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ImportSourceView(R.layout.import_source_seed, context, attrs, defStyleAttr) {

    init {
        background = context.getDrawableCompat(R.drawable.bg_big_input_shape_selector)
    }

    override fun observeSource(source: ImportSource, lifecycleOwner: LifecycleOwner) {
        require(source is RawSeedImportSource)

        importSeedContent.bindTo(source.rawSeedLiveData, lifecycleOwner)
    }
}