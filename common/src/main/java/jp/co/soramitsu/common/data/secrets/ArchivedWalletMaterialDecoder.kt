package jp.co.soramitsu.common.data.secrets

import jp.co.soramitsu.common.data.Keypair
import jp.co.soramitsu.common.data.secrets.legacy.LegacyWalletV04Secrets
import jp.co.soramitsu.common.data.secrets.v1.SourceInternal
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecrets
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v2.MetaAccountSecrets
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecrets
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.secrets.v3.TonSecrets
import jp.co.soramitsu.common.data.secrets.v3.WalletRootSecretValidator
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretScope
import jp.co.soramitsu.common.utils.ethereumAddressFromPublicKey
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair as SigningKeypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.fearless_utils.scale.EncodableStruct
import jp.co.soramitsu.fearless_utils.scale.Schema
import jp.co.soramitsu.fearless_utils.scale.byteArray
import jp.co.soramitsu.fearless_utils.scale.schema
import jp.co.soramitsu.fearless_utils.scale.string
import jp.co.soramitsu.fearless_utils.scale.toHexString
import kotlinx.coroutines.CancellationException
import org.bouncycastle.util.encoders.Hex

/**
 * Proves retained signing keys without trusting missing/deleted Room identities.
 * Each key in a legacy multi-key record is independent. Failure never consumes
 * the archive, and callers must retain an exact-data export for the whole record.
 */
class ArchivedWalletMaterialDecoder {
    class Candidate(
        val scope: WalletSecretScope,
        val publicKey: ByteArray,
        val accountId: ByteArray,
        val cryptoType: CryptoType?,
        val encoded: String
    ) {
        override fun toString(): String = "ArchivedWalletCandidate(scope=$scope)"
    }

    class Decoded(val candidates: List<Candidate>, val unprovedParts: Int)

    @Suppress("DEPRECATION")
    fun decode(sourceKey: String, plaintext: String): Decoded {
        val candidates = mutableListOf<Candidate>()
        var unproved = 0
        fun prove(block: () -> Candidate) {
            try { candidates += block() }
            catch (failure: CancellationException) { throw failure }
            catch (_: Exception) { unproved++ }
        }
        try {
            val encoded = WalletSecretScalePreflight.normalizePersistedHex(plaintext)
            val source = sourceKey.removePrefix("deleted:")
            when {
                source.matches(Regex("[0-9]+:SUBSTRATE_SECRETS")) -> {
                    WalletSecretScalePreflight.requireSubstrateV3(encoded)
                    val secret = SubstrateSecrets.read(encoded)
                    prove { substrate(secret[SubstrateSecrets.SubstrateKeypair].pair(), encoded) }
                }
                source.matches(Regex("[0-9]+:ETHEREUM_SECRETS")) -> {
                    WalletSecretScalePreflight.requireEthereumV3(encoded)
                    val secret = EthereumSecrets.read(encoded)
                    prove { ethereum(secret[EthereumSecrets.EthereumKeypair].pair(), encoded) }
                }
                source.matches(Regex("[0-9]+:TON_SECRETS")) -> {
                    WalletSecretScalePreflight.requireTonV3(encoded)
                    val secret = TonSecrets.read(encoded)
                    prove { ton(secret[TonSecrets.PublicKey], encoded) }
                }
                source.matches(Regex("[0-9]+:[0-9a-f]+:ACCESS_SECRETS")) -> {
                    WalletSecretScalePreflight.requireChainAccountV2(encoded)
                    val secret = ChainAccountSecrets.read(encoded)
                    val pair = secret[ChainAccountSecrets.Keypair].pair()
                    val namedId = Hex.decode(source.split(':')[1])
                    prove {
                        val candidate = if (namedId.size == 20) {
                            ethereum(pair, EthereumSecrets(
                                ethereumKeypair = pair,
                                entropy = secret[ChainAccountSecrets.Entropy],
                                seed = secret[ChainAccountSecrets.Seed],
                                ethereumDerivationPath = secret[ChainAccountSecrets.DerivationPath]
                            ).toHexString())
                        } else {
                            substrate(pair, SubstrateSecrets(
                                substrateKeyPair = pair,
                                entropy = secret[ChainAccountSecrets.Entropy],
                                seed = secret[ChainAccountSecrets.Seed],
                                substrateDerivationPath = secret[ChainAccountSecrets.DerivationPath]
                            ).toHexString())
                        }
                        require(candidate.accountId.contentEquals(namedId))
                        candidate
                    }
                }
                source.matches(Regex("[0-9]+:ACCESS_SECRETS")) -> {
                    when (WalletSecretScalePreflight.requireMetaAccountV2OrLegacyV69(encoded)) {
                        WalletMetaAccountScaleLayout.CURRENT_V2 -> {
                            val secret = MetaAccountSecrets.read(encoded)
                            val pair = secret[MetaAccountSecrets.SubstrateKeypair].pair()
                            prove { substrate(pair, SubstrateSecrets(pair,
                                secret[MetaAccountSecrets.Entropy], secret[MetaAccountSecrets.Seed],
                                secret[MetaAccountSecrets.SubstrateDerivationPath]).toHexString()) }
                            secret[MetaAccountSecrets.EthereumKeypair]?.pair()?.let { ethereum ->
                                prove { ethereum(ethereum, EthereumSecrets(
                                    ethereumKeypair = ethereum, entropy = secret[MetaAccountSecrets.Entropy],
                                    ethereumDerivationPath = secret[MetaAccountSecrets.EthereumDerivationPath]
                                ).toHexString()) }
                            }
                            secret[MetaAccountSecrets.TonKeypair]?.pair()?.let { ton ->
                                // This historical container stored a keypair, not a TON phrase.
                                // An empty phrase is an honest unavailable backup, never an invented one.
                                prove { ton(ton.publicKey, TonSecrets(byteArrayOf(), ton).toHexString()) }
                            }
                        }
                        WalletMetaAccountScaleLayout.LEGACY_V69 -> {
                            val secret = BeforeTon.read(encoded)
                            val pair = secret[BeforeTon.SubstrateKeypair].pair()
                            prove { substrate(pair, SubstrateSecrets(pair, secret[BeforeTon.Entropy],
                                secret[BeforeTon.Seed], secret[BeforeTon.SubstrateDerivationPath]).toHexString()) }
                            secret[BeforeTon.EthereumKeypair]?.pair()?.let { ethereum ->
                                prove { ethereum(ethereum, EthereumSecrets(
                                    ethereumKeypair = ethereum, entropy = secret[BeforeTon.Entropy],
                                    ethereumDerivationPath = secret[BeforeTon.EthereumDerivationPath]
                                ).toHexString()) }
                            }
                        }
                    }
                }
                source.startsWith("security_source_") || source.startsWith("legacy_v1_") -> {
                    WalletSecretScalePreflight.requireSourceV1(encoded)
                    val secret = SourceInternal.read(encoded)
                    val pair = Keypair(secret[SourceInternal.PublicKey], secret[SourceInternal.PrivateKey], secret[SourceInternal.Nonce])
                    // V1's exact phrase/type stay in the original archive. Its signing key can
                    // be restored even when phrase conversion is unavailable or inconsistent.
                    prove { substrate(pair, SubstrateSecrets(pair, seed = secret[SourceInternal.Seed],
                        substrateDerivationPath = secret[SourceInternal.DerivationPath]).toHexString()) }
                }
                LegacyWalletV04Secrets.isSigningDataKey(source) || source.startsWith("legacy_v04_") -> {
                    val secret = LegacyWalletV04Secrets.decodeSigningData(encoded)
                    val pair = Keypair(secret.publicKey, secret.privateKey, secret.nonce)
                    prove { substrate(pair, SubstrateSecrets(pair).toHexString()) }
                }
                else -> unproved++
            }
        } catch (failure: CancellationException) { throw failure }
        catch (_: Exception) { unproved++ }
        return Decoded(candidates, unproved)
    }

    private fun substrate(pair: SigningKeypair, encoded: String): Candidate {
        val crypto = when {
            pair.publicKey.size == 33 -> CryptoType.ECDSA
            pair is Sr25519Keypair -> CryptoType.SR25519
            else -> CryptoType.ED25519
        }
        val accountId = pair.publicKey.substrateAccountId()
        val valid = WalletRootSecretValidator.validateSubstrateAndSanitize(encoded, pair.publicKey, crypto, accountId)
        return Candidate(WalletSecretScope.Substrate, pair.publicKey, accountId, crypto, valid)
    }

    private fun ethereum(pair: SigningKeypair, encoded: String): Candidate {
        val address = pair.publicKey.ethereumAddressFromPublicKey()
        val valid = WalletRootSecretValidator.validateEthereumAndSanitize(encoded, pair.publicKey, address)
        return Candidate(WalletSecretScope.Ethereum, pair.publicKey, address, CryptoType.ECDSA, valid)
    }

    private fun ton(publicKey: ByteArray, encoded: String): Candidate = Candidate(
        WalletSecretScope.Ton, publicKey, publicKey, null,
        WalletRootSecretValidator.validateTonAndSanitize(encoded, publicKey)
    )

    private fun EncodableStruct<KeyPairSchema>.pair(): SigningKeypair = Keypair(this[KeyPairSchema.PublicKey],
        this[KeyPairSchema.PrivateKey], this[KeyPairSchema.Nonce])

    private object BeforeTon : Schema<BeforeTon>() {
        val Entropy by byteArray().optional()
        val Seed by byteArray().optional()
        val SubstrateKeypair by schema(KeyPairSchema)
        val SubstrateDerivationPath by string().optional()
        val EthereumKeypair by schema(KeyPairSchema).optional()
        val EthereumDerivationPath by string().optional()
    }
}
