package jp.co.soramitsu.feature_account_impl.presentation.importing.source.view

import android.content.Context
import android.util.AttributeSet
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import jp.co.soramitsu.common.utils.EventObserver
import jp.co.soramitsu.common.utils.bindTo
import jp.co.soramitsu.common.utils.nameInputFilters
import jp.co.soramitsu.common.view.InputField
import jp.co.soramitsu.feature_account_api.presentation.importing.ImportAccountType
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.model.ImportSource
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.model.JsonImportSource
import kotlinx.android.synthetic.main.import_source_json.view.importJsonContent
import kotlinx.android.synthetic.main.import_source_json.view.importJsonPasswordInput
import kotlinx.android.synthetic.main.import_source_json.view.importJsonUsernameInput

class JsonImportView @JvmOverloads constructor(
    context: Context,
    private val isChainAccount: Boolean,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ImportSourceView(R.layout.import_source_json, context, attrs, defStyleAttr) {

    override val nameInputView: InputField
        get() = importJsonUsernameInput

    init {
        init()
    }

    constructor(context: Context, importAccountType: ImportAccountType, isChainAccount: Boolean) : this(context, isChainAccount) {
        init(importAccountType)
    }

    private fun init(importAccountType: ImportAccountType = ImportAccountType.Substrate) {
        importJsonUsernameInput.editText!!.filters = nameInputFilters()
        setImportAccountType(importAccountType)
        importJsonUsernameInput.isVisible = !isChainAccount
    }

    private fun setImportAccountType(type: ImportAccountType) {
        when (type) {
            ImportAccountType.Substrate -> importJsonContent.setLabel(R.string.recovery_json_substrate)
            ImportAccountType.Ethereum -> importJsonContent.setLabel(R.string.recovery_json_eth)
        }
    }

    override fun observeSource(source: ImportSource, lifecycleOwner: LifecycleOwner) {
        require(source is JsonImportSource)

        source.jsonContentLiveData.observe(lifecycleOwner, Observer(importJsonContent::setMessage))

        source.showJsonInputOptionsEvent.observe(
            lifecycleOwner,
            EventObserver {
                showJsonInputOptionsSheet(source)
            }
        )

        importJsonPasswordInput.content.bindTo(source.passwordLiveData, lifecycleOwner)

        importJsonContent.setActionClickListener {
            source.chooseFileClicked()
        }

        importJsonContent.setOnClickListener {
            source.jsonClicked()
        }
    }

    private fun showJsonInputOptionsSheet(source: JsonImportSource) {
        JsonPasteOptionsSheet(context, source::pasteClicked, source::chooseFileClicked)
            .show()
    }
}
