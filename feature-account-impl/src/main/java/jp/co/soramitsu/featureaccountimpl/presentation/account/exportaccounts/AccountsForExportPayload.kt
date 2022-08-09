package jp.co.soramitsu.featureaccountimpl.presentation.account.exportaccounts

import android.os.Parcelable
import jp.co.soramitsu.featureaccountimpl.domain.account.details.AccountInChain
import kotlinx.parcelize.Parcelize

@Parcelize
class AccountsForExportPayload(
    val metaId: Long,
    val from: AccountInChain.From
) : Parcelable
