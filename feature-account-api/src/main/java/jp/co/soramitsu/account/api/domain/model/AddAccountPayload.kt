package jp.co.soramitsu.account.api.domain.model

import jp.co.soramitsu.core.models.CryptoType

sealed interface AddAccountPayload {
    val accountName: String
    val isBackedUp: Boolean

    data class SubstrateOrEvm(
        override val accountName: String,
        val mnemonic: String,
        val encryptionType: CryptoType,
        val substrateDerivationPath: String,
        val ethereumDerivationPath: String,
        val googleBackupAddress: String?,
        override val isBackedUp: Boolean
    ) : AddAccountPayload

    data class Ton(
        override val accountName: String,
        val mnemonic: String,
        override val isBackedUp: Boolean
    ): AddAccountPayload
}