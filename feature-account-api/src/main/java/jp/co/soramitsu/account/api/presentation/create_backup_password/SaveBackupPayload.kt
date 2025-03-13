package jp.co.soramitsu.account.api.presentation.create_backup_password

import android.os.Parcelable
import jp.co.soramitsu.account.api.domain.model.AddAccountPayload
import kotlinx.parcelize.Parcelize

@Parcelize
class SaveBackupPayload(
    val walletId: Long?,
    val addAccountPayload: AddAccountPayload?
) : Parcelable
