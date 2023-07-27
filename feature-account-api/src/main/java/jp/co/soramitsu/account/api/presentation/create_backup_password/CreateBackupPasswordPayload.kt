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
    val createAccount: Boolean
) : Parcelable
