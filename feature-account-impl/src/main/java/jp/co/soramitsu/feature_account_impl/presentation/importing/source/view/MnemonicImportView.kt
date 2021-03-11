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
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.model.MnemonicImportSource
import kotlinx.android.synthetic.main.import_source_mnemonic.view.importMnemonicContent
import kotlinx.android.synthetic.main.import_source_mnemonic.view.importMnemonicContentContainer
import kotlinx.android.synthetic.main.import_source_mnemonic.view.importMnemonicNetworkInput
import kotlinx.android.synthetic.main.import_source_mnemonic.view.importMnemonicUsernameInput

class MnemonicImportView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ImportSourceView(R.layout.import_source_mnemonic, context, attrs, defStyleAttr) {

    override val networkInputView: LabeledTextView
        get() = importMnemonicNetworkInput

    override val nameInputView: InputField
        get() = importMnemonicUsernameInput

    init {
        importMnemonicContentContainer.background = context.getIdleDrawable()

        importMnemonicUsernameInput.content.filters = nameInputFilters()
    }

    override fun observeSource(source: ImportSource, lifecycleOwner: LifecycleOwner) {
        require(source is MnemonicImportSource)

        importMnemonicContent.bindTo(source.mnemonicContentLiveData, lifecycleOwner)
    }
}