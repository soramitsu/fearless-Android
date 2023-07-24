package jp.co.soramitsu.account.api.presentation.create_backup_password

import android.os.Parcelable
import jp.co.soramitsu.core.models.CryptoType
import kotlinx.parcelize.Parcelize

@Parcelize
class CreateBackupPasswordPayload(
    val mnemonic: String?,
    val accountName: String,
    val cryptoType: CryptoType,
    val substrateDerivationPath: String,
    val ethereumDerivationPath: String,
//    val substrateSeed: String?,
//    val ethSeed: String?,
//    val substrateJson: String?,
//    val ethJson: String?,
    val createAccount: Boolean
) : Parcelable
