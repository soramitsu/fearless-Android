package jp.co.soramitsu.fearless_utils.encrypt.xsalsa20poly1305

import jp.co.soramitsu.fearless_utils.encrypt.xsalsa20poly1305.Keys.sharedSecret
import org.bouncycastle.crypto.digests.Blake2bDigest
import org.bouncycastle.crypto.engines.XSalsa20Engine
import org.bouncycastle.crypto.macs.Poly1305
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Arrays

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

/**
 * Encryption and decryption using XSalsa20Poly1305.
 *
 *
 * Compatible with NaCl's `box` and `secretbox` constructions.
 */
class SecretBox(secretKey: ByteArray) {
    private val key: ByteArray

    /**
     * Create a new [SecretBox] instance given a Curve25519 public key and a Curve25519 private
     * key.
     *
     * @param publicKey a Curve25519 public key
     * @param privateKey a Curve25519 private key
     * @see Keys.generatePrivateKey
     * @see Keys.generatePublicKey
     */
    constructor(
        publicKey: ByteArray?,
        privateKey: ByteArray?
    ) : this(
        sharedSecret(
            publicKey,
            privateKey
        )
    ) {
    }

    /**
     * Encrypt a plaintext using the given key and nonce.
     *
     * @param nonce a 24-byte nonce (cf. [.nonce], [.nonce])
     * @param plaintext an arbitrary message
     * @return the ciphertext
     */
    fun seal(nonce: ByteArray?, plaintext: ByteArray): ByteArray {
        val xsalsa20 =
            XSalsa20Engine()
        val poly1305 =
            Poly1305()
        // initialize XSalsa20
        xsalsa20.init(
            true,
            ParametersWithIV(
                KeyParameter(key),
                nonce
            )
        )
        // generate Poly1305 subkey
        val sk =
            ByteArray(Keys.KEY_LEN)
        xsalsa20.processBytes(
            sk,
            0,
            Keys.KEY_LEN,
            sk,
            0
        )
        // encrypt plaintext
        val out = ByteArray(plaintext.size + poly1305.macSize)
        xsalsa20.processBytes(plaintext, 0, plaintext.size, out, poly1305.macSize)
        // hash ciphertext and prepend mac to ciphertext
        poly1305.init(KeyParameter(sk))
        poly1305.update(out, poly1305.macSize, plaintext.size)
        poly1305.doFinal(out, 0)
        return out
    }

    /**
     * Decrypt a ciphertext using the given key and nonce.
     *
     * @param nonce a 24-byte nonce
     * @param ciphertext the encrypted message
     * @return an [Optional] of the original plaintext, or if either the key, nonce, or
     * ciphertext was modified, an empty [Optional]
     * @see .nonce
     * @see .nonce
     */
    fun open(
        nonce: ByteArray?,
        ciphertext: ByteArray
    ): ByteArray {
        val xsalsa20 =
            XSalsa20Engine()
        val poly1305 =
            Poly1305()
        // initialize XSalsa20
        xsalsa20.init(
            false,
            ParametersWithIV(
                KeyParameter(key),
                nonce
            )
        )
        // generate mac subkey
        val sk =
            ByteArray(Keys.KEY_LEN)
        xsalsa20.processBytes(sk, 0, sk.size, sk, 0)
        // hash ciphertext
        poly1305.init(KeyParameter(sk))
        val len = Math.max(ciphertext.size - poly1305.macSize, 0)
        poly1305.update(ciphertext, poly1305.macSize, len)
        val calculatedMAC = ByteArray(poly1305.macSize)
        poly1305.doFinal(calculatedMAC, 0)
        // extract mac
        val presentedMAC = ByteArray(poly1305.macSize)
        System.arraycopy(
            ciphertext,
            0,
            presentedMAC,
            0,
            Math.min(ciphertext.size, poly1305.macSize)
        )
        // compare macs
        if (!MessageDigest.isEqual(calculatedMAC, presentedMAC)) {
            return ByteArray(0)
        }
        // decrypt ciphertext
        val plaintext = ByteArray(len)
        xsalsa20.processBytes(ciphertext, poly1305.macSize, plaintext.size, plaintext, 0)
        return plaintext
    }

    /**
     * Generates a random nonce.
     *
     *
     * **N.B.:** Use of this method is probably fine, but because an entropy-exhausted or
     * compromised [SecureRandom] provider might generate duplicate nonces (which would allow an
     * attacker to potentially decrypt and even forge messages), [.nonce] is recommended
     * instead.
     *
     * @return a 24-byte nonce
     */
    fun nonce(): ByteArray {
        val nonce = ByteArray(NONCE_SIZE)
        val random = SecureRandom()
        random.nextBytes(nonce)
        return nonce
    }

    /**
     * Generates a random nonce which is guaranteed to be unique even if the process's PRNG is
     * exhausted or compromised.
     *
     *
     * Internally, this creates a Blake2b instance with the given key, a random 16-byte salt, and a
     * random 16-byte personalization tag. It then hashes the message and returns the resulting
     * 24-byte digest as the nonce.
     *
     *
     * In the event of a broken or entropy-exhausted [SecureRandom] provider, the nonce is
     * essentially equivalent to a synthetic IV and should be unique for any given key/message pair.
     * The result will be deterministic, which will allow attackers to detect duplicate messages.
     *
     *
     * In the event of a compromised [SecureRandom] provider, the attacker would need a
     * complete second-preimage attack against Blake2b in order to produce colliding nonces.
     *
     * @param message the message to be encrypted
     * @return a 24-byte nonce
     */
    fun nonce(message: ByteArray): ByteArray {
        val n1 = ByteArray(16)
        val n2 = ByteArray(16)
        val random = SecureRandom()
        random.nextBytes(n1)
        random.nextBytes(n2)
        val blake2b =
            Blake2bDigest(
                key,
                NONCE_SIZE,
                n1,
                n2
            )
        blake2b.update(message, message.size, 0)
        val nonce = ByteArray(NONCE_SIZE)
        blake2b.doFinal(nonce, 0)
        return nonce
    }

    companion object {
        const val NONCE_SIZE = 24
    }

    /**
     * Create a new [SecretBox] instance with the given secret key.
     *
     * @param secretKey a 32-byte secret key
     * @see Keys.generateSecretKey
     */
    init {
        require(secretKey.size == Keys.KEY_LEN) { "secretKey must be 32 bytes long" }
        key = Arrays.copyOf(secretKey, secretKey.size)
    }
}
