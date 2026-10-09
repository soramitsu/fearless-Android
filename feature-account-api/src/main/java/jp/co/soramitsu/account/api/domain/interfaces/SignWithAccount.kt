package jp.co.soramitsu.account.api.domain.interfaces

import jp.co.soramitsu.account.api.domain.model.Account
import jp.co.soramitsu.common.data.secrets.v2.mapKeypairStructToKeypair
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.secrets.v3.WalletRootSecretCorruptionException
import jp.co.soramitsu.common.data.secrets.v3.WalletRootSecretValidator
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityIntegrityException
import jp.co.soramitsu.common.data.storage.encrypt.WalletRecoveryRequiredException
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageUnavailableException
import jp.co.soramitsu.core.crypto.mapCryptoTypeToEncryption
import jp.co.soramitsu.fearless_utils.encrypt.MultiChainEncryption
import jp.co.soramitsu.fearless_utils.encrypt.Signer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun AccountRepository.signWithAccount(account: Account, message: ByteArray) = withContext(Dispatchers.Default) {
    val metaAccount = findMetaAccount(account.accountId)
        ?: error("No wallet found for the signing account")
    val currentKeypair = getSubstrateSecrets(metaAccount.id)
        ?.get(SubstrateSecrets.SubstrateKeypair)
        ?.let(::mapKeypairStructToKeypair)
    // Some pre-v2 transitional installs may only have the retained V1 source.
    // The repository guards this fallback with the same recovery marker check.
    val keypair = currentKeypair ?: getSecuritySource(account.address).keypair
    val expectedPublicKey = metaAccount.substratePublicKey
        ?: throw WalletPublicIdentityIntegrityException(
            "A signing wallet is missing its durable public key"
        )
    val expectedCryptoType = metaAccount.substrateCryptoType
        ?: throw WalletPublicIdentityIntegrityException(
            "A signing wallet is missing its durable crypto type"
        )
    val expectedAccountId = metaAccount.substrateAccountId
        ?: throw WalletPublicIdentityIntegrityException(
            "A signing wallet is missing its durable account id"
        )
    if (
        account.cryptoType != expectedCryptoType ||
        !account.accountId.contentEquals(expectedAccountId)
    ) {
        throw WalletPublicIdentityIntegrityException(
            "A signing request conflicts with the durable wallet identity"
        )
    }
    try {
        WalletRootSecretValidator.validateSubstrateKeypair(
            keypair = keypair,
            expectedPublicKey = expectedPublicKey,
            expectedCryptoType = expectedCryptoType,
            expectedAccountId = expectedAccountId
        )
    } catch (failure: WalletRootSecretCorruptionException) {
        throw WalletRecoveryRequiredException()
    }

    val encryptionType = mapCryptoTypeToEncryption(expectedCryptoType)

    try {
        Signer.sign(
            MultiChainEncryption.Substrate(encryptionType),
            message,
            keypair
        ).signature
    } catch (failure: WalletSecureStorageUnavailableException) {
        throw failure
    } catch (failure: Exception) {
        throw WalletSecureStorageUnavailableException(
            "Wallet signing cryptography is unavailable",
            failure
        )
    }
}
