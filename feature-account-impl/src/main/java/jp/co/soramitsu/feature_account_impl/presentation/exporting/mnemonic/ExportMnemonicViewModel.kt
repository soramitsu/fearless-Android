package jp.co.soramitsu.feature_account_impl.presentation.exporting.mnemonic

import android.content.Context
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.exporting.ExportSource
import jp.co.soramitsu.feature_account_impl.presentation.exporting.ExportViewModel

class ExportMnemonicViewModel(
    private val router: AccountRouter,
    private val context: Context,
    private val resourceManager: ResourceManager,
    accountAddress: String
) : ExportViewModel(ExportSource.Mnemonic)