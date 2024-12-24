package jp.co.soramitsu.account.impl.presentation.mnemonic.backup

import android.os.Parcelable
import jp.co.soramitsu.account.api.presentation.importing.ImportAccountType
import kotlinx.parcelize.Parcelize

@Parcelize
class BackupMnemonicPayload(
    val isFromGoogleBackup: Boolean,
    val accountName: String,
    val walletId: Long?,
    val accountTypes: List<ImportAccountType>
) : Parcelable
