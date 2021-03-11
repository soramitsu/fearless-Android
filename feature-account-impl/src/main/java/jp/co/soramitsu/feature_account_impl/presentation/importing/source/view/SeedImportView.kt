package jp.co.soramitsu.feature_account_impl.presentation.importing.source.view

import android.content.Context
import android.util.AttributeSet
import androidx.lifecycle.LifecycleOwner
import jp.co.soramitsu.common.utils.bindTo
import jp.co.soramitsu.common.utils.nameInputFilters
import jp.co.soramitsu.common.view.InputField
import jp.co.soramitsu.common.view.LabeledTextView
import jp.co.soramitsu.common.view.shape.getIdleDrawable
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.model.ImportSource
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.model.RawSeedImportSource
import kotlinx.android.synthetic.main.import_source_seed.view.importSeedContent
import kotlinx.android.synthetic.main.import_source_seed.view.importSeedContentContainer
import kotlinx.android.synthetic.main.import_source_seed.view.importSeedNetworkInput
import kotlinx.android.synthetic.main.import_source_seed.view.importSeedUsernameInput

class SeedImportView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ImportSourceView(R.layout.import_source_seed, context, attrs, defStyleAttr) {

    override val networkInputView: LabeledTextView
        get() = importSeedNetworkInput

    override val nameInputView: InputField
        get() = importSeedUsernameInput

    init {
        importSeedContentContainer.background = context.getIdleDrawable()

        importSeedUsernameInput.content.filters = nameInputFilters()
    }

    override fun observeSource(source: ImportSource, lifecycleOwner: LifecycleOwner) {
        require(source is RawSeedImportSource)

        importSeedContent.bindTo(source.rawSeedLiveData, lifecycleOwner)
    }
}