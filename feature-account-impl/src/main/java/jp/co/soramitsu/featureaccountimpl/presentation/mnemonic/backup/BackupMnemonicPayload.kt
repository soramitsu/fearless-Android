package jp.co.soramitsu.featureaccountimpl.presentation.mnemonic.backup

import android.os.Parcelable
import jp.co.soramitsu.featureaccountapi.presentation.account.create.ChainAccountCreatePayload
import kotlinx.android.parcel.Parcelize

@Parcelize
class BackupMnemonicPayload(
    val accountName: String,
    val chainAccountData: ChainAccountCreatePayload?
) : Parcelable
