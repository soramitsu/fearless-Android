package jp.co.soramitsu.feature_account_impl.presentation.account.exportaccounts

import android.os.Parcelable
import jp.co.soramitsu.feature_account_impl.domain.account.details.AccountInChain
import kotlinx.android.parcel.Parcelize

@Parcelize
class AccountsForExportPayload(
    val metaId: Long,
    val from: AccountInChain.From
) : Parcelable
