package jp.co.soramitsu.feature_account_api.domain.interfaces

import jp.co.soramitsu.common.data.Keypair
import jp.co.soramitsu.common.data.mappers.mapCryptoTypeToEncryption
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v2.MetaAccountSecrets
import jp.co.soramitsu.fearless_utils.encrypt.MultiChainEncryption
import jp.co.soramitsu.fearless_utils.encrypt.Signer
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_account_api.domain.model.MetaAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun AccountRepository.signWithAccount(account: Account, message: ByteArray) = withContext(Dispatchers.Default) {
    val securitySource = getSecuritySource(account.address)

    val encryptionType = mapCryptoTypeToEncryption(account.cryptoType)

    Signer.sign(MultiChainEncryption.Substrate(encryptionType), message, securitySource.keypair).signature
}

suspend fun AccountRepository.signWithMetaAccount(account: MetaAccount, message: ByteArray) = withContext(Dispatchers.Default) {
    val secrets = getMetaAccountSecrets(account.id) //?: return@withContext
    requireNotNull(secrets)
    val keypairSchema = //if (chain.isEthereumBased) {//todo add multiassets
//        secrets?.get(MetaAccountSecrets.EthereumKeypair)
//    } else {
        secrets[MetaAccountSecrets.SubstrateKeypair]
//    }

    val publicKey = keypairSchema[KeyPairSchema.PublicKey]
    val privateKey = keypairSchema[KeyPairSchema.PrivateKey]
    val nonce = keypairSchema[KeyPairSchema.Nonce]
    val keypair = Keypair(publicKey, privateKey, nonce)
    val encryptionType = mapCryptoTypeToEncryption(account.substrateCryptoType)

    Signer.sign(MultiChainEncryption.Substrate(encryptionType), message, keypair)
}

suspend fun AccountRepository.signWithCurrentMetaAccount(message: ByteArray) = withContext(Dispatchers.Default) {
    signWithMetaAccount(getSelectedMetaAccount(), message)
}
