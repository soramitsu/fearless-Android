package jp.co.soramitsu.fearless_utils.encrypt.xsalsa20poly1305

import org.bouncycastle.math.ec.rfc7748.X25519
import java.security.SecureRandom
import kotlin.experimental.and
import kotlin.experimental.or

/*
 * Copyright © 2017 Coda Hale (coda.hale@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/** Utility methods for generating XSalsa20Poly1305 keys.  */
object Keys {
    const val KEY_LEN = 32
    private val HSALSA20_SEED = ByteArray(16)

    /**
     * Generates a 32-byte secret key.
     *
     * @return a 32-byte secret key
     */
    fun generateSecretKey(): ByteArray {
        val k = ByteArray(KEY_LEN)
        val random = SecureRandom()
        random.nextBytes(k)
        return k
    }

    /**
     * generates a Curve25519 private key.
     *
     * @return a Curve25519 private key
     */
    fun generatePrivateKey(): ByteArray {
        val k = generateSecretKey()
        k[0] = k[0] and 248.toByte()
        k[31] = k[31] and 127.toByte()
        k[31] = k[31] or 64.toByte()
        return k
    }

    /**
     * Generates a Curve25519 public key given a Curve25519 private key.
     *
     * @param privateKey a Curve25519 private key
     * @return the public key matching `privateKey`
     */
    fun generatePublicKey(privateKey: ByteArray?): ByteArray {
        val publicKey = ByteArray(KEY_LEN)
        X25519.scalarMultBase(privateKey, 0, publicKey, 0)
        return publicKey
    }

    /**
     * Calculate the X25519/HSalsa20 shared secret for the given public key and private key.
     *
     * @param publicKey the recipient's public key
     * @param privateKey the sender's private key
     * @return a 32-byte secret key only re-calculable by the sender and recipient
     */
    fun sharedSecret(
        publicKey: ByteArray?,
        privateKey: ByteArray?
    ): ByteArray {
        val s = ByteArray(KEY_LEN)
        X25519.scalarMult(privateKey, 0, publicKey, 0, s, 0)
        val k = ByteArray(KEY_LEN)
        HSalsa20.hsalsa20(k, HSALSA20_SEED, s)
        return k
    }
}
