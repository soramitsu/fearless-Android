package jp.co.soramitsu.account.impl.domain

import jp.co.soramitsu.common.utils.deriveSeed32
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.core.crypto.SubstratePasswordRule
import jp.co.soramitsu.core.crypto.mapCryptoTypeToEncryption
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.fearless_utils.encrypt.junction.SubstrateJunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.seed.substrate.SubstrateSeedFactory
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress

/** The Substrate account BIP39 words and a path open when the path's password is read by [rule]. */
data class SubstratePasswordAccount(val rule: SubstratePasswordRule, val address: String)

private const val GENERIC_SUBSTRATE_PREFIX: Short = 42

/**
 * One account per rule that reads the path's password differently (SubstratePasswordRule), the
 * current rule first. A single entry means every Fearless app opens the same account.
 */
fun substratePasswordAccounts(
    mnemonic: String,
    substrateDerivationPath: String,
    cryptoType: CryptoType
): List<SubstratePasswordAccount> {
    val decodedPath = substrateDerivationPath.takeIf(String::isNotEmpty)?.let(SubstrateJunctionDecoder::decode)
    return SubstratePasswordRule.distinctFor(decodedPath?.password).map { rule ->
        val seed = SubstrateSeedFactory.deriveSeed32(mnemonic, decodedPath?.password, rule).seed
        val keypair = SubstrateKeypairFactory.generate(
            encryptionType = mapCryptoTypeToEncryption(cryptoType),
            seed = seed,
            junctions = decodedPath?.junctions.orEmpty()
        )
        SubstratePasswordAccount(rule, keypair.publicKey.substrateAccountId().toAddress(GENERIC_SUBSTRATE_PREFIX))
    }
}
