package jp.co.soramitsu.account.impl.presentation.mnemonic.backup

import android.os.Parcelable
import jp.co.soramitsu.common.model.WalletEcosystem
import kotlinx.parcelize.Parcelize

@Parcelize
class BackupMnemonicPayload(
    val isFromGoogleBackup: Boolean,
    val accountName: String,
    val walletId: Long?,
    val accountTypes: List<WalletEcosystem>
) : Parcelable
