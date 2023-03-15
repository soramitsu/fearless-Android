package jp.co.soramitsu.account.api.domain.interfaces

import jp.co.soramitsu.fearless_utils.encrypt.MultiChainEncryption
import jp.co.soramitsu.fearless_utils.encrypt.Signer
import jp.co.soramitsu.account.api.domain.model.Account
import jp.co.soramitsu.core.crypto.mapCryptoTypeToEncryption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun AccountRepository.signWithAccount(account: Account, message: ByteArray) = withContext(Dispatchers.Default) {
    val securitySource = getSecuritySource(account.address)

    val encryptionType = mapCryptoTypeToEncryption(account.cryptoType)

    Signer.sign(MultiChainEncryption.Substrate(encryptionType), message, securitySource.keypair).signature
}
