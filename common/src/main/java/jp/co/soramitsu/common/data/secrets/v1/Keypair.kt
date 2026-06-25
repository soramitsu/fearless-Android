package jp.co.soramitsu.common.data.secrets.v1

import jp.co.soramitsu.fearless_utils.encrypt.keypair.BaseKeypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.Sr25519Keypair

/**
 * Creates [Sr25519Keypair] if [nonce] is not null
 * Creates [BaseKeypair] otherwise
 */
fun Keypair(
    publicKey: ByteArray,
    privateKey: ByteArray,
    nonce: ByteArray? = null
) = if (nonce != null) {
    Sr25519Keypair(
        publicKey = publicKey,
        privateKey = privateKey,
        nonce = nonce
    )
} else {
    BaseKeypair(
        privateKey = privateKey,
        publicKey = publicKey
    )
}
