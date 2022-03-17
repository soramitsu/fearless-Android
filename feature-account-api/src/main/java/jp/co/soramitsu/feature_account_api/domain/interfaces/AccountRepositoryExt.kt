package jp.co.soramitsu.feature_account_api.domain.interfaces

import jp.co.soramitsu.common.data.mappers.mapCryptoTypeToEncryption
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v2.MetaAccountSecrets
import jp.co.soramitsu.fearless_utils.encrypt.MultiChainEncryption
import jp.co.soramitsu.fearless_utils.encrypt.Signer
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_account_api.domain.model.MetaAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun AccountRepository.signWithAccount(account: Account, message: ByteArray) = withContext(Dispatchers.Default) {
    val securitySource = getSecuritySource(account.address)

    val encryptionType = mapCryptoTypeToEncryption(account.cryptoType)

    Signer.sign(MultiChainEncryption.Substrate(encryptionType), message, securitySource.keypair).signature
}

suspend fun AccountRepository.signWithCurrentAccount(message: ByteArray) = withContext(Dispatchers.Default) {

    signWithAccount(getSelectedAccount(), message)
}

suspend fun AccountRepository.signWithMetaAccount(account: MetaAccount, message: ByteArray) = withContext(Dispatchers.Default) {
    val secrets = getMetaAccountSecrets(account.id)
    val keypairSchema = if (chain.isEthereumBased) {
        secrets?.get(MetaAccountSecrets.EthereumKeypair)
    } else {
        secrets?.get(MetaAccountSecrets.SubstrateKeypair)
    }

    val publicKey = keypairSchema?.get(KeyPairSchema.PublicKey)
    val privateKey = keypairSchema?.get(KeyPairSchema.PrivateKey)
    val encryptionType = mapCryptoTypeToEncryption(account.substrateCryptoType)

    Signer.sign(MultiChainEncryption.Substrate(encryptionType), message, securitySource.keypair).signature
}


suspend fun AccountRepository.signWithCurrentMetaAccount(message: ByteArray) = withContext(Dispatchers.Default) {
    signWithMetaAccount(getSelectedMetaAccount(), message)
}
