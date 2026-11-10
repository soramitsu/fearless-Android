package jp.co.soramitsu.common.data.storage.envelope

import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.macs.HMac
import org.bouncycastle.crypto.params.KeyParameter

/** HMAC over SHA-256 for the wallet keyring's device factor, on BouncyCastle like the rest of the keyring. */
object KeyringMac {
    fun sha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = HMac(SHA256Digest())
        mac.init(KeyParameter(key))
        mac.update(data, 0, data.size)
        return ByteArray(mac.macSize).also { mac.doFinal(it, 0) }
    }
}
