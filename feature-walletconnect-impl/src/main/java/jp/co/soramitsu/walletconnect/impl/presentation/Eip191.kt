package jp.co.soramitsu.walletconnect.impl.presentation

import jp.co.soramitsu.fearless_utils.extensions.toHexString
import org.web3j.crypto.ECKeyPair
import org.web3j.crypto.Sign

/** EIP-191 `personal_sign`, as the wallet answered it with the SDK before. */
internal object Eip191 {
    /**
     * Signs the UTF-8 bytes of [message] with the "\u0019Ethereum Signed Message:\n<length>" prefix and
     * returns `0x` + r + s + v (v = 27 or 28), exactly as the removed SDK's EIP-191 signer did.
     */
    fun personalSign(message: String, privateKey: ByteArray): String {
        val signature = Sign.signPrefixedMessage(message.toByteArray(Charsets.UTF_8), ECKeyPair.create(privateKey))
        return (signature.r + signature.s + signature.v).toHexString(withPrefix = true)
    }
}
