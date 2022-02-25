package jp.co.soramitsu.feature_account_impl.presentation.importing.source.view

import android.content.Context
import android.util.AttributeSet
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import jp.co.soramitsu.common.utils.bindTo
import jp.co.soramitsu.common.utils.nameInputFilters
import jp.co.soramitsu.common.view.InputField
import jp.co.soramitsu.common.view.shape.getIdleDrawable
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.model.ImportSource
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.model.MnemonicImportSource
import kotlinx.android.synthetic.main.import_source_mnemonic.view.importMnemonicContent
import kotlinx.android.synthetic.main.import_source_mnemonic.view.importMnemonicContentContainer
import kotlinx.android.synthetic.main.import_source_mnemonic.view.importMnemonicUsernameInput
import kotlinx.android.synthetic.main.import_source_mnemonic.view.usernameHintTv

class MnemonicImportView @JvmOverloads constructor(
    context: Context,
    isChainAccount: Boolean,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ImportSourceView(R.layout.import_source_mnemonic, context, attrs, defStyleAttr) {

    override val nameInputView: InputField
        get() = importMnemonicUsernameInput

    init {
        importMnemonicContentContainer.background = context.getIdleDrawable()

        importMnemonicUsernameInput.content.filters = nameInputFilters()

        importMnemonicUsernameInput.isVisible = !isChainAccount
        usernameHintTv.isVisible = !isChainAccount
    }

    override fun observeSource(source: ImportSource, lifecycleOwner: LifecycleOwner) {
        require(source is MnemonicImportSource)

        importMnemonicContent.bindTo(source.mnemonicContentLiveData, lifecycleOwner)
    }
}
