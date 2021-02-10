package jp.co.soramitsu.feature_account_impl.presentation.importing.source.view

import android.content.Context
import android.util.AttributeSet
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import jp.co.soramitsu.common.utils.bindTo
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.common.utils.nameInputFilters
import jp.co.soramitsu.common.view.shape.getIdleDrawable
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.presentation.common.mixin.api.chooseNetworkClicked
import jp.co.soramitsu.feature_account_impl.presentation.importing.ImportAccountViewModel
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

    init {
        importSeedContentContainer.background = context.getIdleDrawable()

        importSeedUsernameInput.content.filters = nameInputFilters()
    }

    override fun observeCommon(viewModel: ImportAccountViewModel, lifecycleOwner: LifecycleOwner) {
        importSeedUsernameInput.content.bindTo(viewModel.nameLiveData, lifecycleOwner)
        viewModel.selectedNetworkLiveData.observe(lifecycleOwner, Observer {
            importSeedNetworkInput.setTextIcon(it.networkTypeUI.icon)
            importSeedNetworkInput.setMessage(it.name)
        })
        importSeedNetworkInput.setWholeClickListener {
            viewModel.chooseNetworkClicked()
        }
        if (viewModel.isNetworkTypeChangeAvailable) {
            importSeedNetworkInput.isEnabled = true
            importSeedNetworkInput.makeVisible()
        } else {
            importSeedNetworkInput.isEnabled = false
            importSeedNetworkInput.makeVisible()
        }
        viewModel.networkChooserEnabledLiveData.observe(lifecycleOwner, Observer { enabled ->
            if (viewModel.isNetworkTypeChangeAvailable) {
                importSeedNetworkInput.isEnabled = true
                importSeedNetworkInput.makeVisible()
            } else {
                importSeedNetworkInput.isEnabled = false
                importSeedNetworkInput.makeVisible()
            }
        })
    }

    override fun observeSource(source: ImportSource, lifecycleOwner: LifecycleOwner) {
        require(source is RawSeedImportSource)

        importSeedContent.bindTo(source.rawSeedLiveData, lifecycleOwner)
    }
}