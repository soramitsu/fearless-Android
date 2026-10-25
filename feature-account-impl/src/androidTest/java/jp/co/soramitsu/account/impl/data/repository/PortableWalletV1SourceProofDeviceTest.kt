package jp.co.soramitsu.account.impl.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises V1 SR25519 proof with the native provider bundled into the Android test APK. */
@RunWith(AndroidJUnit4::class)
@Suppress("MagicNumber") // Deterministic fixture seed and fixed semantic wire tags.
class PortableWalletV1SourceProofDeviceTest {
    private val codec = PortableWalletSemanticMaterial
    private val role = PortableWalletSemanticMaterial.Role
    private val field = PortableWalletSemanticMaterial.FieldId

    @Test
    fun sr25519LegacySeedAndPrivateKeyProveOriginalOwnership() {
        val seed = ByteArray(32) { (it + 1).toByte() }
        val pair = SubstrateKeypairFactory.generate(EncryptionType.SR25519, seed, emptyList()) as Sr25519Keypair
        val original = encodedLegacy(pair.publicKey, pair.privateKey, pair.nonce, seed)
        // schnorrkel draws a fresh random nonce for every soft junction, so a
        // nonce is never compared with a re-derived one (AL-5); any 32-byte
        // nonce signs for this key. The seed and private key still bind it.
        val otherNonce = pair.nonce.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
        val withOtherNonce = encodedLegacy(pair.publicKey, pair.privateKey, otherNonce, seed)
        val wrongSeed = seed.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
        val withWrongSeed = encodedLegacy(pair.publicKey, pair.privateKey, pair.nonce, wrongSeed)
        val other = SubstrateKeypairFactory.generate(EncryptionType.SR25519, wrongSeed, emptyList()) as Sr25519Keypair
        val withWrongPrivateKey = encodedLegacy(pair.publicKey, other.privateKey, pair.nonce, seed)
        try {
            assertEquals(1, PortableWalletV1SourceProof.verify(original).provedLegacySources)
            assertEquals(1, PortableWalletV1SourceProof.verify(withOtherNonce).provedLegacySources)
            assertThrows(IllegalArgumentException::class.java) {
                PortableWalletV1SourceProof.verify(withWrongSeed)
            }
            assertThrows(IllegalArgumentException::class.java) {
                PortableWalletV1SourceProof.verify(withWrongPrivateKey)
            }
        } finally {
            listOf(original, withOtherNonce, withWrongSeed, withWrongPrivateKey).forEach { it.fill(0) }
            listOf(seed, wrongSeed, otherNonce, pair.privateKey, pair.nonce, other.privateKey, other.nonce)
                .forEach { it.fill(0) }
        }
    }

    private fun encodedLegacy(
        publicKey: ByteArray,
        privateKey: ByteArray,
        nonce: ByteArray,
        seed: ByteArray
    ): ByteArray {
        val slot = PortableWalletSemanticMaterial.Slot(
            role.LEGACY_SUBSTRATE, "",
            listOf(
                item(field.PUBLIC_KEY, publicKey),
                item(field.PRIVATE_KEY, privateKey),
                item(field.NONCE, nonce),
                item(field.SEED, seed),
                item(field.ACCOUNT_ID_OR_ADDRESS, publicKey.substrateAccountId().toAddress(42).toByteArray()),
                item(field.CRYPTO_TYPE, byteArrayOf(1)),
                item(field.SOURCE_RECIPE, byteArrayOf(2))
            )
        )
        val snapshot = PortableWalletSemanticMaterial.Snapshot(
            0,
            listOf(
                PortableWalletSemanticMaterial.Wallet(
                    ByteArray(16) { 1 }, 0, true, "SR25519", emptyList(), listOf(slot)
                )
            )
        )
        return try {
            codec.encode(snapshot)
        } finally {
            snapshot.clearSecrets()
        }
    }

    private fun item(id: Int, value: ByteArray) = PortableWalletSemanticMaterial.Field(id, value.copyOf())
}
