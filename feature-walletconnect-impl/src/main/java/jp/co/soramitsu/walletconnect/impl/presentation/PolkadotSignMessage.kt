package jp.co.soramitsu.walletconnect.impl.presentation

import jp.co.soramitsu.fearless_utils.encrypt.MultiChainEncryption
import jp.co.soramitsu.fearless_utils.encrypt.SignatureWrapper
import jp.co.soramitsu.fearless_utils.encrypt.Signer
import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair
import jp.co.soramitsu.fearless_utils.extensions.fromHex

private val POLKADOT_MESSAGE_PREFIX = "<Bytes>".encodeToByteArray()
private val POLKADOT_MESSAGE_POSTFIX = "</Bytes>".encodeToByteArray()
private val POLKADOT_HEX_MESSAGE = Regex("0x(?:[0-9a-fA-F]{2})*")

/**
 * Bytes of a `polkadot_signMessage` message, read as polkadot.js `u8aToU8a` reads them:
 * `0x`-prefixed even-length hex is decoded, anything else is UTF-8 text.
 */
internal fun polkadotMessageBytes(message: String): ByteArray = if (POLKADOT_HEX_MESSAGE.matches(message)) {
    message.fromHex()
} else {
    message.encodeToByteArray()
}

internal fun isWrappedPolkadotMessage(bytes: ByteArray): Boolean {
    val wrapSize = POLKADOT_MESSAGE_PREFIX.size + POLKADOT_MESSAGE_POSTFIX.size
    return bytes.size >= wrapSize &&
        bytes.copyOfRange(0, POLKADOT_MESSAGE_PREFIX.size).contentEquals(POLKADOT_MESSAGE_PREFIX) &&
        bytes.copyOfRange(bytes.size - POLKADOT_MESSAGE_POSTFIX.size, bytes.size).contentEquals(POLKADOT_MESSAGE_POSTFIX)
}

/**
 * Wraps message bytes in `<Bytes>`…`</Bytes>` as polkadot.js `u8aWrapBytes` does, so a message
 * signature can never be valid for an extrinsic signing payload, its blake2 hash or an RLP
 * Ethereum transaction. Already wrapped input is kept as is. Unlike polkadot.js, input that starts
 * with the EIP-191 prefix is wrapped too: this namespace never signs Ethereum messages.
 */
internal fun wrapPolkadotMessageBytes(bytes: ByteArray): ByteArray = if (isWrappedPolkadotMessage(bytes)) {
    bytes
} else {
    POLKADOT_MESSAGE_PREFIX + bytes + POLKADOT_MESSAGE_POSTFIX
}

/** Signs a `polkadot_signMessage` message with polkadot.js extension `signRaw` semantics. */
internal fun signPolkadotMessage(
    message: String,
    encryption: MultiChainEncryption,
    keypair: Keypair
): SignatureWrapper = Signer.sign(encryption, wrapPolkadotMessageBytes(polkadotMessageBytes(message)), keypair)
