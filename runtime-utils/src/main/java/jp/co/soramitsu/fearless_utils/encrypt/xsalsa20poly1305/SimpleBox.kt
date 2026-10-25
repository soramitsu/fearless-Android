package jp.co.soramitsu.fearless_utils.encrypt.xsalsa20poly1305

import java8.util.Optional
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
 * Convenience functions for encryption without requiring nonce management.
 *
 *
 * Compatible with RbNaCl's SimpleBox construction, but generates misuse-resistant nonces.
 */
class SimpleBox {
    private val box: SecretBox

    /**
     * Create a new [SimpleBox] instance with the given secret key.
     *
     * @param secretKey a 32-byte secret key
     */
    constructor(secretKey: ByteArray) {
        box = SecretBox(secretKey)
    }

    /**
     * Create a new [SecretBox] instance given a Curve25519 public key and a Curve25519 private
     * key.
     *
     * @param publicKey a Curve25519 public key
     * @param privateKey a Curve25519 private key
     */
    constructor(publicKey: ByteArray?, privateKey: ByteArray?) {
        box = SecretBox(publicKey, privateKey)
    }

    /**
     * Encrypt the plaintext with the given key.
     *
     * @param plaintext any arbitrary bytes
     * @return the ciphertext
     */
    fun seal(plaintext: ByteArray): ByteArray {
        val nonce: ByteArray = box.nonce(plaintext)
        val ciphertext: ByteArray = box.seal(nonce, plaintext)
        val combined = ByteArray(nonce.size + ciphertext.size)
        System.arraycopy(nonce, 0, combined, 0, nonce.size)
        System.arraycopy(ciphertext, 0, combined, nonce.size, ciphertext.size)
        return combined
    }

    /**
     * Decrypt the ciphertext with the given key.
     *
     * @param ciphertext an encrypted message
     * @return an [Optional] of the original plaintext, or if either the key, nonce, or
     * ciphertext was modified, an empty [Optional]
     */
    fun open(ciphertext: ByteArray): ByteArray {
        if (ciphertext.size < SecretBox.NONCE_SIZE) {
            return ByteArray(0)
        }
        val nonce =
            Arrays.copyOfRange(ciphertext, 0, SecretBox.NONCE_SIZE)
        val x =
            Arrays.copyOfRange(ciphertext, SecretBox.NONCE_SIZE, ciphertext.size)
        return box.open(nonce, x)
    }
}
