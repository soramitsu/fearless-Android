package jp.co.soramitsu.feature_account_api.domain.interfaces

import jp.co.soramitsu.common.data.mappers.mapCryptoTypeToEncryption
import jp.co.soramitsu.common.data.mappers.mapSigningDataToKeypair
import jp.co.soramitsu.fearless_utils.encrypt.Signer
import jp.co.soramitsu.feature_account_api.domain.model.Account
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun AccountRepository.currentNetworkType() = getSelectedNode().networkType

suspend fun AccountRepository.signWithAccount(account: Account, message: ByteArray) = withContext(Dispatchers.Default) {
    val securitySource = getSecuritySource(account.address)

    val encryptionType = mapCryptoTypeToEncryption(account.cryptoType)
    val keypair = mapSigningDataToKeypair(securitySource.signingData)

    Signer.sign(encryptionType, message, keypair).signature
}
