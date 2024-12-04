package jp.co.soramitsu.account.impl.presentation.mnemonic.backup

import android.os.Parcelable
import jp.co.soramitsu.account.api.domain.model.AccountType
import kotlinx.parcelize.Parcelize

@Parcelize
class BackupMnemonicPayload(
    val isFromGoogleBackup: Boolean,
    val accountName: String,
    val accountType: AccountType
) : Parcelable
