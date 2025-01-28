package jp.co.soramitsu.account.impl.presentation.importing.source.view

import android.content.Context
import android.util.AttributeSet
import androidx.lifecycle.LifecycleOwner
import jp.co.soramitsu.common.model.ImportAccountType
import jp.co.soramitsu.account.impl.presentation.importing.source.model.ImportSource
import jp.co.soramitsu.account.impl.presentation.importing.source.model.MnemonicImportSource
import jp.co.soramitsu.common.utils.bindTo
import jp.co.soramitsu.common.utils.nameInputFilters
import jp.co.soramitsu.common.view.InputField
import jp.co.soramitsu.common.view.shape.getIdleDrawable
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.databinding.ImportSourceMnemonicBinding

class MnemonicImportView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ImportSourceView(R.layout.import_source_mnemonic, context, attrs, defStyleAttr) {

    private val binding: ImportSourceMnemonicBinding = ImportSourceMnemonicBinding.bind(this)

    override val nameInputView: InputField
        get() = binding.importMnemonicUsernameInput

    init {
        binding.importMnemonicContentContainer.background = context.getIdleDrawable()

        binding.importMnemonicUsernameInput.content.filters = nameInputFilters()
    }

    override fun observeSource(source: ImportSource, blockchainType: ImportAccountType, lifecycleOwner: LifecycleOwner) {
        require(source is MnemonicImportSource)

        binding.importMnemonicContent.bindTo(source.mnemonicContentLiveData, lifecycleOwner)
    }
}
