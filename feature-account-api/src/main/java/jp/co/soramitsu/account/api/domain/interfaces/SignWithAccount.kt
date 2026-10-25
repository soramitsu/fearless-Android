package jp.co.soramitsu.account.api.domain.interfaces

import jp.co.soramitsu.account.api.domain.model.Account
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecretCorruptionException
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecretValidator
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecrets
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

    val matchingChainAccounts = metaAccount.chainAccounts.entries.filter { (_, chainAccount) ->
        chainAccount.accountId.contentEquals(account.accountId)
    }
    val chainAccountEntry = matchingChainAccounts.firstOrNull()
    if (
        chainAccountEntry != null &&
        matchingChainAccounts.drop(1).any { (_, chainAccount) ->
            chainAccount.cryptoType != chainAccountEntry.value.cryptoType ||
                !chainAccount.publicKey.contentEquals(chainAccountEntry.value.publicKey)
        }
    ) {
        throw WalletPublicIdentityIntegrityException(
            "A signing account has conflicting durable chain identities"
        )
    }

    val (keypair, expectedCryptoType) = if (chainAccountEntry != null) {
        val (chainId, chainAccount) = chainAccountEntry
        if (account.cryptoType != chainAccount.cryptoType) {
            throw WalletPublicIdentityIntegrityException(
                "A signing request conflicts with the durable chain-account identity"
            )
        }
        val chainSecrets = getChainAccountSecrets(metaAccount.id, chainId)
            ?: throw WalletRecoveryRequiredException()
        val chainKeypair = chainSecrets[ChainAccountSecrets.Keypair]
            .let(::mapKeypairStructToKeypair)
        try {
            ChainAccountSecretValidator.validateKeypair(
                keypair = chainKeypair,
                expectedAccountId = chainAccount.accountId,
                expectedPublicKey = chainAccount.publicKey,
                expectedCryptoType = chainAccount.cryptoType
            )
        } catch (failure: ChainAccountSecretCorruptionException) {
            throw WalletRecoveryRequiredException()
        }
        chainKeypair to chainAccount.cryptoType
    } else {
        val expectedPublicKey = metaAccount.substratePublicKey
            ?: throw WalletPublicIdentityIntegrityException(
                "A signing wallet is missing its durable public key"
            )
        val rootCryptoType = metaAccount.substrateCryptoType
            ?: throw WalletPublicIdentityIntegrityException(
                "A signing wallet is missing its durable crypto type"
            )
        val expectedAccountId = metaAccount.substrateAccountId
            ?: throw WalletPublicIdentityIntegrityException(
                "A signing wallet is missing its durable account id"
            )
        if (
            account.cryptoType != rootCryptoType ||
            !account.accountId.contentEquals(expectedAccountId)
        ) {
            throw WalletPublicIdentityIntegrityException(
                "A signing request conflicts with the durable wallet identity"
            )
        }
        val currentKeypair = getSubstrateSecrets(metaAccount.id)
            ?.get(SubstrateSecrets.SubstrateKeypair)
            ?.let(::mapKeypairStructToKeypair)
        // Some pre-v2 transitional installs may only have the retained V1 source.
        // The repository guards this fallback with the same recovery marker check.
        val rootKeypair = currentKeypair ?: getSecuritySource(account.address).keypair
        try {
            WalletRootSecretValidator.validateSubstrateKeypair(
                keypair = rootKeypair,
                expectedPublicKey = expectedPublicKey,
                expectedCryptoType = rootCryptoType,
                expectedAccountId = expectedAccountId
            )
        } catch (failure: WalletRootSecretCorruptionException) {
            throw WalletRecoveryRequiredException()
        }
        rootKeypair to rootCryptoType
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
