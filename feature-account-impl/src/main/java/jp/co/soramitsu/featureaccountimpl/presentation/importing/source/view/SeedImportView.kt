package jp.co.soramitsu.featureaccountimpl.presentation.importing.source.view

import android.content.Context
import android.util.AttributeSet
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import jp.co.soramitsu.common.utils.bindTo
import jp.co.soramitsu.common.utils.nameInputFilters
import jp.co.soramitsu.common.view.InputField
import jp.co.soramitsu.common.view.shape.getIdleDrawable
import jp.co.soramitsu.featureaccountapi.presentation.importing.ImportAccountType
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.databinding.ImportSourceSeedBinding
import jp.co.soramitsu.featureaccountimpl.presentation.importing.source.model.ImportSource
import jp.co.soramitsu.featureaccountimpl.presentation.importing.source.model.RawSeedImportSource

class SeedImportView @JvmOverloads constructor(
    context: Context,
    private val isChainAccount: Boolean,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ImportSourceView(R.layout.import_source_seed, context, attrs, defStyleAttr) {

    private val binding: ImportSourceSeedBinding = ImportSourceSeedBinding.bind(this)

    override val nameInputView: InputField
        get() = binding.importSeedUsernameInput

    init {
        init()
    }

    constructor(context: Context, type: ImportAccountType, isChainAccount: Boolean) : this(context, isChainAccount) {
        init(type)
    }

    fun init(type: ImportAccountType = ImportAccountType.Substrate) {
        binding.importSeedContentContainer.background = context.getIdleDrawable()

        binding.importSeedUsernameInput.content.filters = nameInputFilters()

        setImportAccountType(type)

        binding.importSeedUsernameInput.isVisible = !isChainAccount
        binding.usernameHintTv.isVisible = !isChainAccount
    }

    private fun setImportAccountType(type: ImportAccountType) {
        when (type) {
            ImportAccountType.Substrate -> binding.importSeedTitle.setText(R.string.account_import_substrate_raw_seed_placeholder)
            ImportAccountType.Ethereum -> binding.importSeedTitle.setText(R.string.account_import_ethereum_raw_seed_placeholder)
        }
    }

    override fun observeSource(source: ImportSource, lifecycleOwner: LifecycleOwner) {
        require(source is RawSeedImportSource)

        binding.importSeedContent.bindTo(source.rawSeedLiveData, lifecycleOwner)
    }
}
