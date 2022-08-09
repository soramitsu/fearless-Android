package jp.co.soramitsu.featureaccountimpl.presentation.importing.source.view

import android.content.Context
import android.util.AttributeSet
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import jp.co.soramitsu.common.utils.EventObserver
import jp.co.soramitsu.common.utils.bindTo
import jp.co.soramitsu.common.utils.nameInputFilters
import jp.co.soramitsu.common.view.InputField
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.databinding.ImportSourceJsonBinding
import jp.co.soramitsu.featureaccountapi.presentation.importing.ImportAccountType
import jp.co.soramitsu.featureaccountimpl.presentation.importing.source.model.ImportSource
import jp.co.soramitsu.featureaccountimpl.presentation.importing.source.model.JsonImportSource

class JsonImportView @JvmOverloads constructor(
    context: Context,
    private val isChainAccount: Boolean = false,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ImportSourceView(R.layout.import_source_json, context, attrs, defStyleAttr) {

    private val binding: ImportSourceJsonBinding = ImportSourceJsonBinding.bind(this)

    override val nameInputView: InputField
        get() = binding.importJsonUsernameInput

    init {
        init()
    }

    constructor(context: Context, importAccountType: ImportAccountType, isChainAccount: Boolean) : this(context, isChainAccount) {
        init(importAccountType)
    }

    private fun init(importAccountType: ImportAccountType = ImportAccountType.Substrate) {
        binding.importJsonUsernameInput.apply {
            editText!!.filters = nameInputFilters()
            isVisible = !isChainAccount
        }
        setImportAccountType(importAccountType)
    }

    private fun setImportAccountType(type: ImportAccountType) {
        when (type) {
            ImportAccountType.Substrate -> binding.importJsonContent.setLabel(R.string.import_substrate_recovery)
            ImportAccountType.Ethereum -> binding.importJsonContent.setLabel(R.string.import_ethereum_recovery)
        }
    }

    override fun observeSource(source: ImportSource, lifecycleOwner: LifecycleOwner) {
        require(source is JsonImportSource)

        source.jsonContentLiveData.observe(
            lifecycleOwner,
            Observer(binding.importJsonContent::setMessage)
        )

        source.showJsonInputOptionsEvent.observe(
            lifecycleOwner,
            EventObserver {
                showJsonInputOptionsSheet(source)
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
