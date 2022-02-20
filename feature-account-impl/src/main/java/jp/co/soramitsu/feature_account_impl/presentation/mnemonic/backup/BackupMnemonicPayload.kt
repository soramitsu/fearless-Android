package jp.co.soramitsu.feature_account_impl.presentation.mnemonic.backup

import android.os.Parcelable
import jp.co.soramitsu.feature_account_api.presentation.account.create.ChainAccountCreatePayload
import kotlinx.android.parcel.Parcelize

@Parcelize
class BackupMnemonicPayload(
    val accountName: String,
    val chainAccountData: ChainAccountCreatePayload?
) : Parcelable
