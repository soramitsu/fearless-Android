package jp.co.soramitsu.account.impl.domain.recovery

import java.util.Locale
import jp.co.soramitsu.common.data.Keypair
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecretValidator
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecrets
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecrets
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.secrets.v3.TonSecrets
import jp.co.soramitsu.common.data.secrets.v3.WalletRootSecretValidator
import jp.co.soramitsu.common.utils.DEFAULT_DERIVATION_PATH
import jp.co.soramitsu.common.utils.deriveSeed32
import jp.co.soramitsu.core.crypto.SubstratePasswordRule
import jp.co.soramitsu.core.crypto.mapCryptoTypeToEncryption
import jp.co.soramitsu.fearless_utils.encrypt.MultiChainEncryption
import jp.co.soramitsu.fearless_utils.encrypt.json.JsonSeedDecoder
import jp.co.soramitsu.fearless_utils.encrypt.junction.BIP32JunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.junction.SubstrateJunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair as SigningKeypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.ethereum.EthereumKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.seed.ethereum.EthereumSeedFactory
import jp.co.soramitsu.fearless_utils.encrypt.seed.substrate.SubstrateSeedFactory
import jp.co.soramitsu.fearless_utils.scale.toHexString
import org.bouncycastle.util.encoders.Hex
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.mnemonic.Mnemonic

/**
 * Derives a candidate with the same algorithms used by shipped importers, then
 * proves its signing key against the recorded identity. This class never writes
 * storage. Rebinding must re-read the identity and validate again under the
 * cross-store mutation lock, since a UI's target can become stale.
 */
class WalletIdentityVerifier(private val jsonDecoder: JsonSeedDecoder) {
    fun verifyMnemonic(identity: WalletKeyIdentity, text: String, path: String = ""): VerifiedWalletSecret {
        require(text.length in 1..4096 && path.length <= 512) { "Recovery input is too long or empty" }
        if (identity is WalletKeyIdentity.Ton) return verifyTon(identity, text)
        val ethereum = identity.isEthereum()
        val fallback = if (ethereum) BIP32JunctionDecoder.DEFAULT_DERIVATION_PATH else ""
        for (candidate in listOf(path.ifEmpty { fallback }, fallback).distinct()) {
            if (ethereum) {
                val decoded = BIP32JunctionDecoder.decode(candidate)
                val derived = EthereumSeedFactory.deriveSeed32(text, decoded.password)
                val keys = EthereumKeypairFactory.generate(derived.seed, decoded.junctions)
                // V3 stores the derived private key; chain V2 stores the BIP39 seed.
                val seed = if (identity is WalletKeyIdentity.Chain) derived.seed else keys.privateKey
                if (keys.publicKey.contentEquals(identity.publicKey())) {
                    return validated(identity, encode(identity, keys, derived.mnemonic.entropy, seed, candidate))
                }
                continue
            }
            val decoded = candidate.takeIf { it.isNotEmpty() }?.let(SubstrateJunctionDecoder::decode)
            // A wallet import may have opened the account an earlier browser extension made from the
            // path's password; per-network accounts are only ever made with the current rule.
            val rules = if (identity is WalletKeyIdentity.Substrate) {
                SubstratePasswordRule.distinctFor(decoded?.password)
            } else {
                listOf(SubstratePasswordRule.NFKD)
            }
            for (rule in rules) {
                val derived = SubstrateSeedFactory.deriveSeed32(text, decoded?.password, rule)
                val keys = SubstrateKeypairFactory.generate(identity.encryptionType(), derived.seed, decoded?.junctions.orEmpty())
                if (keys.publicKey.contentEquals(identity.publicKey())) {
                    return validated(identity, encode(identity, keys, derived.mnemonic.entropy, derived.seed, candidate))
                }
            }
        }
        throw WalletRecoveryIdentityMismatch()
    }

    fun verifySeed(identity: WalletKeyIdentity, hex: String, path: String = ""): VerifiedWalletSecret {
        require(identity !is WalletKeyIdentity.Ton) { "TON recovery requires its stored phrase" }
        val value = hex.removePrefix("0x")
        require(value.length == 64 && value.all { it.digitToIntOrNull(16) != null } && path.length <= 512) {
            "A recovery seed or private key must contain 32 bytes"
        }
        val seed = Hex.decode(value)
        val ethereum = identity.isEthereum()
        val keys = if (ethereum) {
            EthereumKeypairFactory.createWithPrivateKey(seed)
        } else {
            val decoded = path.takeIf { it.isNotEmpty() }?.let(SubstrateJunctionDecoder::decode)
            // Historical raw-seed imports apply junctions only; the optional
            // phrase password in a saved path does not transform an existing seed.
            SubstrateKeypairFactory.generate(identity.encryptionType(), seed, decoded?.junctions.orEmpty())
        }
        if (!keys.publicKey.contentEquals(identity.publicKey())) throw WalletRecoveryIdentityMismatch()
        return validated(identity, encode(identity, keys, null, seed, if (ethereum) null else path))
    }

    fun verifyJson(identity: WalletKeyIdentity, json: String, password: String): VerifiedWalletSecret {
        require(identity !is WalletKeyIdentity.Ton) { "TON recovery requires its stored phrase" }
        require(json.length in 1..262144) { "Recovery file is too large or empty" }
        val imported = jsonDecoder.decode(json, password)
        val keys = if (identity.isEthereum()) {
            require(imported.multiChainEncryption == MultiChainEncryption.Ethereum) { "An Ethereum backup is required" }
            EthereumKeypairFactory.createWithPrivateKey(imported.keypair.privateKey)
        } else {
            require(imported.multiChainEncryption is MultiChainEncryption.Substrate &&
                imported.multiChainEncryption.encryptionType == identity.encryptionType()) {
                "The backup uses another signing type"
            }
            imported.keypair
        }
        if (!keys.publicKey.contentEquals(identity.publicKey())) throw WalletRecoveryIdentityMismatch()
        return validated(identity, encode(identity, keys, null, if (identity.isEthereum()) null else imported.seed, null))
    }

    /** Re-proves signing, including a sign/verify self-test, without trusting the candidate's type. */
    fun validate(identity: WalletKeyIdentity, encoded: String): String = when (identity) {
        is WalletKeyIdentity.Substrate -> WalletRootSecretValidator.validateSubstrateAndSanitize(
            encoded, identity.publicKey, identity.cryptoType, identity.accountId
        )
        is WalletKeyIdentity.Ethereum -> WalletRootSecretValidator.validateEthereumAndSanitize(
            encoded, identity.publicKey, identity.address
        )
        is WalletKeyIdentity.Ton -> WalletRootSecretValidator.validateTonAndSanitize(encoded, identity.publicKey)
        is WalletKeyIdentity.Chain -> ChainAccountSecretValidator.validateAndSanitize(
            encoded, identity.accountId, identity.publicKey, identity.cryptoType
        )
    }

    private fun verifyTon(identity: WalletKeyIdentity.Ton, text: String): VerifiedWalletSecret {
        val canonical = text.trim().split(Regex("\\s+")).joinToString(" ")
        for (candidate in listOf(text, canonical, canonical.lowercase(Locale.ROOT)).distinct()) {
            // Preserve the exact candidate that matched, including historical spaces and case.
            val privateKey = PrivateKeyEd25519(Mnemonic.toSeed(candidate.split(" ")))
            val publicKey = privateKey.publicKey().key.toByteArray()
            if (publicKey.contentEquals(identity.publicKey)) {
                return validated(identity, TonSecrets(
                    seed = candidate.encodeToByteArray(),
                    tonKeypair = Keypair(publicKey, privateKey.key.toByteArray())
                ).toHexString())
            }
        }
        throw WalletRecoveryIdentityMismatch()
    }

    private fun validated(identity: WalletKeyIdentity, encoded: String) =
        VerifiedWalletSecret(identity.scope, validate(identity, encoded))

    private fun encode(
        identity: WalletKeyIdentity,
        keys: SigningKeypair,
        entropy: ByteArray?,
        seed: ByteArray?,
        path: String?
    ): String = when (identity) {
        is WalletKeyIdentity.Substrate -> SubstrateSecrets(keys, entropy, seed, path).toHexString()
        is WalletKeyIdentity.Ethereum -> EthereumSecrets(entropy, seed, keys, path).toHexString()
        is WalletKeyIdentity.Chain -> ChainAccountSecrets(keys, entropy, seed, path).toHexString()
        is WalletKeyIdentity.Ton -> error("TON uses its own phrase encoding")
    }

    private fun WalletKeyIdentity.publicKey(): ByteArray = when (this) {
        is WalletKeyIdentity.Substrate -> publicKey
        is WalletKeyIdentity.Ethereum -> publicKey
        is WalletKeyIdentity.Ton -> publicKey
        is WalletKeyIdentity.Chain -> publicKey
    }

    private fun WalletKeyIdentity.isEthereum(): Boolean = this is WalletKeyIdentity.Ethereum ||
        (this is WalletKeyIdentity.Chain && accountId.size == 20)

    private fun WalletKeyIdentity.encryptionType() = mapCryptoTypeToEncryption(when (this) {
        is WalletKeyIdentity.Substrate -> cryptoType
        is WalletKeyIdentity.Chain -> cryptoType
        else -> error("A Substrate signing type is required")
    })
}
