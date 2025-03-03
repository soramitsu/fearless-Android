package jp.co.soramitsu.account.api.domain.model

import android.os.Parcelable
import jp.co.soramitsu.core.models.CryptoType
import kotlinx.parcelize.Parcelize

@Parcelize
sealed interface AddAccountPayload : Parcelable {
    val accountName: String
    val isBackedUp: Boolean

    @Parcelize
    data class SubstrateOrEvm(
        override val accountName: String,
        val mnemonic: String,
        val encryptionType: CryptoType,
        val substrateDerivationPath: String,
        val ethereumDerivationPath: String,
        val googleBackupAddress: String?,
        override val isBackedUp: Boolean
    ) : AddAccountPayload, Parcelable

    @Parcelize
    data class Ton(
        override val accountName: String,
        val mnemonic: String,
        override val isBackedUp: Boolean
    ): AddAccountPayload, Parcelable

    @Parcelize
    data class AdditionalEvm(
        val walletId: Long,
        override val accountName: String,
        val mnemonic: String,
        val ethereumDerivationPath: String,
        override val isBackedUp: Boolean
    ) : AddAccountPayload, Parcelable
}