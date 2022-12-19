package jp.co.soramitsu.account.impl.presentation.account.exportaccounts

import android.os.Parcelable
import jp.co.soramitsu.account.impl.domain.account.details.AccountInChain
import kotlinx.parcelize.Parcelize

@Parcelize
class AccountsForExportPayload(
    val metaId: Long,
    val from: AccountInChain.From
) : Parcelable
