package jp.co.soramitsu.account.impl.presentation.importing.source.view

import android.content.Context
import android.util.AttributeSet
import androidx.lifecycle.LifecycleOwner
import jp.co.soramitsu.account.api.presentation.importing.ImportAccountType
import jp.co.soramitsu.account.impl.presentation.importing.source.model.ImportError
import jp.co.soramitsu.account.impl.presentation.importing.source.model.ImportSource
import jp.co.soramitsu.account.impl.presentation.importing.source.model.JsonImportSource
import jp.co.soramitsu.common.utils.EventObserver
import jp.co.soramitsu.common.utils.bindTo
import jp.co.soramitsu.common.utils.nameInputFilters
import jp.co.soramitsu.common.view.InputField
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.databinding.ImportSourceJsonBinding

class JsonImportView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    private val showImportError: (importError: ImportError) -> Unit = {}
) : ImportSourceView(R.layout.import_source_json, context, attrs, defStyleAttr) {

    private val binding: ImportSourceJsonBinding = ImportSourceJsonBinding.bind(this)

    override val nameInputView: InputField
        get() = binding.importJsonUsernameInput

    init {
        init()
    }

    constructor(context: Context, importAccountType: ImportAccountType, onShowImportError: (importError: ImportError) -> Unit) : this(context, showImportError = onShowImportError) {
        init(importAccountType)
    }

    private fun init(importAccountType: ImportAccountType = ImportAccountType.Substrate) {
        binding.importJsonUsernameInput.apply {
            editText!!.filters = nameInputFilters()
        }
        setImportAccountType(importAccountType)
    }

    private fun setImportAccountType(type: ImportAccountType) {
        when (type) {
            ImportAccountType.Substrate -> binding.importJsonContent.setLabel(R.string.import_substrate_recovery)
            ImportAccountType.Ethereum -> binding.importJsonContent.setLabel(R.string.import_ethereum_recovery)
            ImportAccountType.Ton -> { /* not implemented */
            }
        }
    }

    override fun observeSource(source: ImportSource, blockchainType: ImportAccountType, lifecycleOwner: LifecycleOwner) {
        require(source is JsonImportSource)

        source.blockchainTypeFlow.value = blockchainType

        source.jsonContentLiveData.observe(lifecycleOwner) {
            binding.importJsonContent.setMessage(it)
        }

        source.showJsonInputOptionsEvent.observe(
            lifecycleOwner,
            EventObserver {
                showJsonInputOptionsSheet(source)
            }
        )

        source.showImportErrorEvent.observe(
            lifecycleOwner,
            EventObserver {
                showImportError.invoke(it)
            }
        )

        binding.importJsonPasswordInput.content.bindTo(source.passwordLiveData, lifecycleOwner)

        binding.importJsonContent.apply {
            setActionClickListener {
                source.chooseFileClicked()
            }
            setOnClickListener {
                source.jsonClicked()
            }
        }
    }

    private fun showJsonInputOptionsSheet(source: JsonImportSource) {
        JsonPasteOptionsSheet(context, source::pasteClicked, source::chooseFileClicked)
            .show()
    }
}
