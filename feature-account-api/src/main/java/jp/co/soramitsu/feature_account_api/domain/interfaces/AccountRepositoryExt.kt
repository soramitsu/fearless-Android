package jp.co.soramitsu.feature_account_api.domain.interfaces

import jp.co.soramitsu.common.data.mappers.mapCryptoTypeToEncryption
import jp.co.soramitsu.common.utils.requireValue
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.fearless_utils.encrypt.MultiChainEncryption
import jp.co.soramitsu.fearless_utils.encrypt.Signer
import jp.co.soramitsu.feature_account_api.domain.model.Account
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val DEFAULT_NETWORK_TYPE = Node.NetworkType.KUSAMA

//TODO remove
suspend fun AccountRepository.currentNetworkType() = runCatching { getSelectedAccount().network.type }
    .recover { DEFAULT_NETWORK_TYPE }
    .requireValue()

suspend fun AccountRepository.signWithAccount(account: Account, message: ByteArray) = withContext(Dispatchers.Default) {
    val securitySource = getSecuritySource(account.address)

    val encryptionType = mapCryptoTypeToEncryption(account.cryptoType)

    Signer.sign(MultiChainEncryption.Substrate(encryptionType), message, securitySource.keypair).signature
}
