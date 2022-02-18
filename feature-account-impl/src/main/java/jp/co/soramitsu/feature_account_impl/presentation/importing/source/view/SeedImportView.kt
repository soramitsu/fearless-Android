package jp.co.soramitsu.feature_account_impl.presentation.importing.source.view

import android.content.Context
import android.util.AttributeSet
import androidx.lifecycle.LifecycleOwner
import jp.co.soramitsu.common.utils.bindTo
import jp.co.soramitsu.common.utils.nameInputFilters
import jp.co.soramitsu.common.view.InputField
import jp.co.soramitsu.common.view.shape.getIdleDrawable
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.presentation.importing.ImportAccountType
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.model.ImportSource
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.model.RawSeedImportSource
import kotlinx.android.synthetic.main.import_source_seed.view.importSeedContent
import kotlinx.android.synthetic.main.import_source_seed.view.importSeedContentContainer
import kotlinx.android.synthetic.main.import_source_seed.view.importSeedTitle
import kotlinx.android.synthetic.main.import_source_seed.view.importSeedUsernameInput

class SeedImportView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ImportSourceView(R.layout.import_source_seed, context, attrs, defStyleAttr) {

    override val nameInputView: InputField
        get() = importSeedUsernameInput

    init {
        init()
    }

    constructor(context: Context, type: ImportAccountType) : this(context) {
        init(type)
    }

    fun init(type: ImportAccountType = ImportAccountType.Substrate) {
        importSeedContentContainer.background = context.getIdleDrawable()

        importSeedUsernameInput.content.filters = nameInputFilters()

        setImportAccountType(type)
    }

    private fun setImportAccountType(type: ImportAccountType) {
        when (type) {
            ImportAccountType.Substrate -> importSeedTitle.setText(R.string.recovery_raw_seed_substrate)
            ImportAccountType.Ethereum -> importSeedTitle.setText(R.string.recovery_raw_seed_eth)
        }
    }

    override fun observeSource(source: ImportSource, lifecycleOwner: LifecycleOwner) {
        require(source is RawSeedImportSource)

        importSeedContent.bindTo(source.rawSeedLiveData, lifecycleOwner)
    }
}
