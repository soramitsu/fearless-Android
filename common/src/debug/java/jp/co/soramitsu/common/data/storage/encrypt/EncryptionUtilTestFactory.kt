package jp.co.soramitsu.common.data.storage.encrypt

import android.content.Context
import java.util.concurrent.atomic.AtomicInteger

/**
 * Debug-only factory for exercising production decryption classification.
 *
 * Release artifacts do not contain this factory. Matching decryptions after
 * [successfulMatchingDecryptions] fail persistently so an Android database
 * opener cannot hide the injected failure with an immediate internal retry.
 */
object EncryptionUtilTestFactory {

    fun withPayloadDecryptFailure(
        context: Context,
        transformation: String,
        successfulMatchingDecryptions: Int = 0,
        failure: Exception
    ): EncryptionUtil {
        require(transformation.isNotBlank()) {
            "A payload cipher transformation is required"
        }
        require(successfulMatchingDecryptions >= 0) {
            "The successful decryption count cannot be negative"
        }

        val matchingDecryptions = AtomicInteger()
        val decryptor = WalletPayloadDecryptor {
                actualTransformation,
                key,
                parameters,
                ciphertext,
                secureRandom ->
            if (
                actualTransformation == transformation &&
                matchingDecryptions.getAndIncrement() >=
                successfulMatchingDecryptions
            ) {
                throw failure
            }

            JcaWalletPayloadDecryptor.decrypt(
                transformation = actualTransformation,
                key = key,
                parameters = parameters,
                ciphertext = ciphertext,
                secureRandom = secureRandom
            )
        }

        return EncryptionUtil(
            context = context,
            payloadDecryptor = decryptor
        )
    }
}
