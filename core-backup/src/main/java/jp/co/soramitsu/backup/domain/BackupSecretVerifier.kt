package jp.co.soramitsu.backup.domain

import jp.co.soramitsu.backup.domain.exceptions.BackupSecretNotVerifiedException
import jp.co.soramitsu.backup.domain.models.BackupAccountType
import jp.co.soramitsu.backup.domain.models.DecryptedBackupAccount
import jp.co.soramitsu.core.crypto.SubstratePasswordRule
import jp.co.soramitsu.core.crypto.substrateMiniSecret
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.junction.BIP32JunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.junction.Junction
import jp.co.soramitsu.fearless_utils.encrypt.junction.SubstrateJunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.keypair.ethereum.EthereumKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.seed.ethereum.EthereumSeedFactory
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.hash.Hasher.blake2b256
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Lets only recovery material that restores the wallet it is filed under reach Google Drive.
 *
 * A backup is filed under the wallet's Substrate address. Importers restore a PASSPHRASE backup from
 * the phrase and paths alone and a SEED backup from the seeds. If stored recovery material has
 * drifted from the wallet's keys, uploading it would leave the user believing they are backed up
 * with something that restores a different account (critique D-7), so that type is refused and
 * nothing is uploaded. Material of an undeclared type is dropped, because no importer reads it.
 *
 * A JSON backup is exported from the keypair of the wallet's Polkadot account, which is a separate
 * chain account for some wallets. Importers only fall back to it when a backup has neither phrase
 * nor seed, so a JSON of another account is dropped beside verified types and refused on its own.
 */
internal class BackupSecretVerifier(
    private val derivation: BackupKeyDerivation = FearlessUtilsBackupKeyDerivation
) {

    fun verifiedForUpload(account: DecryptedBackupAccount): DecryptedBackupAccount {
        val types = account.backupAccountType.toSet()
        require(types.isNotEmpty()) { "A backup needs at least one backup type" }
        // The address names the Drive file and becomes the restored wallet's backup address.
        val accountId = requireNotNull(runCatching { SS58Encoder.decode(account.address) }.getOrNull()) {
            "A backup must be filed under the wallet's SS58 address"
        }

        if (BackupAccountType.PASSPHRASE in types) verifyPassphrase(account, accountId)
        if (BackupAccountType.SEED in types) verifySeed(account, accountId)
        val keepsJson = BackupAccountType.JSON in types && jsonRestoresWallet(account, accountId)
        if (types == setOf(BackupAccountType.JSON) && !keepsJson) {
            throw BackupSecretNotVerifiedException(BackupAccountType.JSON)
        }

        return account.copy(
            mnemonicPhrase = account.mnemonicPhrase.takeIf { BackupAccountType.PASSPHRASE in types },
            backupAccountType = account.backupAccountType.filter { it != BackupAccountType.JSON || keepsJson },
            seed = account.seed.takeIf { BackupAccountType.SEED in types },
            json = account.json.takeIf { keepsJson }
        )
    }

    /**
     * For importers: the phrase and seed of a downloaded backup that restore the Substrate account it
     * is filed under, or null when the address cannot be checked. Before this version uploads were not
     * verified, so a drifted phrase can sit beside a seed or JSON that does restore the wallet.
     */
    fun restoringSecretTypes(account: DecryptedBackupAccount): Set<BackupAccountType>? {
        val accountId = runCatching { SS58Encoder.decode(account.address) }.getOrNull() ?: return null

        val path = account.substrateDerivationPath
        val passphraseRestores = account.mnemonicPhrase?.let { mnemonic ->
            restores { passphraseRule(mnemonic, path, account.cryptoType, accountId) != null }
        } == true
        val seedRestores = account.seed?.substrateSeed?.let { seed ->
            restores { derivation.substrateAccountIdFromSeed(seed.fromHex(), path, account.cryptoType).contentEquals(accountId) }
        } == true

        return buildSet {
            if (passphraseRestores) add(BackupAccountType.PASSPHRASE)
            if (seedRestores) add(BackupAccountType.SEED)
        }
    }

    /**
     * For importers of a PASSPHRASE backup: how the path's password must be read to restore the account
     * the backup is filed under (an earlier browser extension's rule, see SubstratePasswordRule), or null
     * when the phrase restores no account under any rule or the address cannot be checked.
     */
    fun restoringSubstratePasswordRule(account: DecryptedBackupAccount): SubstratePasswordRule? {
        val accountId = runCatching { SS58Encoder.decode(account.address) }.getOrNull() ?: return null
        val mnemonic = account.mnemonicPhrase ?: return null
        return runCatching {
            passphraseRule(mnemonic, account.substrateDerivationPath, account.cryptoType, accountId)
        }.getOrNull()
    }

    // The first rule, current one first, under which the phrase and path derive the account.
    private fun passphraseRule(
        mnemonic: String,
        path: String?,
        cryptoType: CryptoType,
        accountId: ByteArray
    ): SubstratePasswordRule? {
        val password = path?.takeIf(String::isNotEmpty)?.let(SubstrateJunctionDecoder::decode)?.password
        return SubstratePasswordRule.distinctFor(password).firstOrNull { rule ->
            derivation.substrateAccountIdFromMnemonic(mnemonic, path, cryptoType, rule).contentEquals(accountId)
        }
    }

    // Material that cannot be derived at all (bad path, not BIP39, wrong seed length) restores nothing.
    private inline fun restores(derivesAccount: () -> Boolean): Boolean {
        return runCatching(derivesAccount).getOrDefault(false)
    }

    private fun verifyPassphrase(account: DecryptedBackupAccount, accountId: ByteArray) =
        verifying(BackupAccountType.PASSPHRASE) {
            val mnemonic = requireNotNull(account.mnemonicPhrase)
            // An import may have opened the account an earlier browser extension made from the path's
            // password; importers read the rule back the same way (restoringSubstratePasswordRule).
            val rule = passphraseRule(mnemonic, account.substrateDerivationPath, account.cryptoType, accountId)
            rule != null && passphraseRestoresEthereumKey(account, mnemonic)
        }

    // The wallet's Ethereum key travels in the seed. Unless the SEED type carries it along, the
    // phrase and Ethereum path must derive it, or the restored wallet gets another Ethereum account.
    private fun passphraseRestoresEthereumKey(account: DecryptedBackupAccount, mnemonic: String): Boolean {
        val ethereumPrivateKey = account.seed?.ethSeed?.fromHex() ?: return true
        if (BackupAccountType.SEED in account.backupAccountType) return true

        return derivation.ethereumPrivateKeyFromMnemonic(
            mnemonic = mnemonic,
            derivationPath = account.ethDerivationPath.orEmpty()
        ).contentEquals(ethereumPrivateKey)
    }

    private fun verifySeed(account: DecryptedBackupAccount, accountId: ByteArray) = verifying(BackupAccountType.SEED) {
        val seed = requireNotNull(account.seed)
        seed.ethSeed?.let { require(it.fromHex().size == ETHEREUM_PRIVATE_KEY_SIZE) }
        val restoredAccountId = derivation.substrateAccountIdFromSeed(
            seed = requireNotNull(seed.substrateSeed).fromHex(),
            derivationPath = account.substrateDerivationPath,
            cryptoType = account.cryptoType
        )
        restoredAccountId.contentEquals(accountId)
    }

    // The keystore's plain "address" names the account its encrypted keypair holds.
    private fun jsonRestoresWallet(account: DecryptedBackupAccount, accountId: ByteArray): Boolean {
        val substrateJson = account.json?.substrateJson ?: return false
        return runCatching {
            val address = Json.parseToJsonElement(substrateJson).jsonObject.getValue("address").jsonPrimitive.content
            SS58Encoder.decode(address).contentEquals(accountId)
        }.getOrDefault(false)
    }

    private inline fun verifying(type: BackupAccountType, restoresWallet: () -> Boolean) {
        val verified = try {
            restoresWallet()
        } catch (e: Exception) {
            throw BackupSecretNotVerifiedException(type, e)
        }
        if (!verified) throw BackupSecretNotVerifiedException(type)
    }

    private companion object {
        const val ETHEREUM_PRIVATE_KEY_SIZE = 32
    }
}

/** The derivations the app's importers perform, so a check here predicts what an import restores. */
internal interface BackupKeyDerivation {

    fun substrateAccountIdFromMnemonic(
        mnemonic: String,
        derivationPath: String?,
        cryptoType: CryptoType,
        passwordRule: SubstratePasswordRule = SubstratePasswordRule.NFKD
    ): ByteArray

    fun substrateAccountIdFromSeed(
        seed: ByteArray,
        derivationPath: String?,
        cryptoType: CryptoType
    ): ByteArray

    fun ethereumPrivateKeyFromMnemonic(mnemonic: String, derivationPath: String): ByteArray
}

internal object FearlessUtilsBackupKeyDerivation : BackupKeyDerivation {

    private const val ACCOUNT_ID_SIZE = 32

    // As SubstrateOrEvmAccountRepository.create: the path's password, read by the rule, salts the seed.
    override fun substrateAccountIdFromMnemonic(
        mnemonic: String,
        derivationPath: String?,
        cryptoType: CryptoType,
        passwordRule: SubstratePasswordRule
    ): ByteArray {
        val decodedPath = derivationPath?.takeIf(String::isNotEmpty)?.let(SubstrateJunctionDecoder::decode)
        val seed = substrateMiniSecret(mnemonic, decodedPath?.password, passwordRule)
        return substrateAccountId(seed, decodedPath?.junctions.orEmpty(), cryptoType)
    }

    // As AccountRepositoryImpl.importFromSeed: only the path's junctions apply to a seed.
    override fun substrateAccountIdFromSeed(
        seed: ByteArray,
        derivationPath: String?,
        cryptoType: CryptoType
    ): ByteArray {
        val decodedPath = derivationPath?.takeIf(String::isNotEmpty)?.let(SubstrateJunctionDecoder::decode)
        return substrateAccountId(seed, decodedPath?.junctions.orEmpty(), cryptoType)
    }

    override fun ethereumPrivateKeyFromMnemonic(mnemonic: String, derivationPath: String): ByteArray {
        val decodedPath = BIP32JunctionDecoder.decode(derivationPath)
        val seed = EthereumSeedFactory.deriveSeed(mnemonic, decodedPath.password).seed
        return EthereumKeypairFactory.generate(seed, decodedPath.junctions).privateKey
    }

    private fun substrateAccountId(
        seed: ByteArray,
        junctions: List<Junction>,
        cryptoType: CryptoType
    ): ByteArray {
        val publicKey = SubstrateKeypairFactory.generate(cryptoType.toEncryptionType(), seed, junctions).publicKey
        // ECDSA public keys are 33 bytes; Substrate account ids are their blake2b-256 hash.
        return if (publicKey.size > ACCOUNT_ID_SIZE) publicKey.blake2b256() else publicKey
    }

    private fun CryptoType.toEncryptionType() = when (this) {
        CryptoType.SR25519 -> EncryptionType.SR25519
        CryptoType.ED25519 -> EncryptionType.ED25519
        CryptoType.ECDSA -> EncryptionType.ECDSA
    }
}
