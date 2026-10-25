package jp.co.soramitsu.account.impl.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the production SR25519 JNI bundled by feature-account-impl's androidTest APK. */
@RunWith(AndroidJUnit4::class)
@Suppress("MagicNumber") // Deterministic fixture seeds and fixed FPWMSM01 field widths.
class PortableWalletRootSigningProofDeviceTest {
    private val codec = PortableWalletSemanticMaterial
    private val field = PortableWalletSemanticMaterial.FieldId

    // iOS shared-features-spm Tests/SR25519Native/legacy-vectors.json "pair":
    // SNKeyFactory's scalar||nonce||public result for seed 00..1f.
    private val iosNativeSrPair = (
        "87d29d94134be13d30adc66e053ca9aab38d02c8db5bc23cb4249ef0e3dff10" +
            "da9d71862a3e5746b571be3d187b0041046f52ebd850c7cbd5fde8ee38473" +
            "b649e2111779981618705ecacea1af6ff9350bce2b2dccd03e0c3e01eb0c823d2666"
        ).chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun sr25519PortableRootProvesNativeSigningAndRejectsAnotherPrivateKey() {
        val root = SubstrateKeypairFactory.generate(
            EncryptionType.SR25519, ByteArray(32) { 31 }, emptyList(),
        ) as Sr25519Keypair
        val unrelated = SubstrateKeypairFactory.generate(
            EncryptionType.SR25519, ByteArray(32) { 32 }, emptyList(),
        ) as Sr25519Keypair
        try {
            val genuine = encodedRoot(root.publicKey, root.privateKey, root.nonce)
            try {
                val proof = PortableWalletRootSigningProof.verify(genuine)
                assertEquals(1, proof.substrateRoots)
                assertEquals(0, proof.unprovenSlots)
            } finally {
                genuine.fill(0)
            }

            val substituted = encodedRoot(root.publicKey, unrelated.privateKey, unrelated.nonce)
            try {
                assertThrows(IllegalArgumentException::class.java) {
                    PortableWalletRootSigningProof.verify(substituted)
                }
            } finally {
                substituted.fill(0)
            }
        } finally {
            root.privateKey.fill(0)
            root.nonce.fill(0)
            unrelated.privateKey.fill(0)
            unrelated.nonce.fill(0)
        }
    }

    @Test
    fun iosSr25519ScalarNonceSecretProvesNativeSigningAndRejectsAmbiguousShapes() {
        val root = SubstrateKeypairFactory.generate(
            EncryptionType.SR25519, ByteArray(32) { it.toByte() }, emptyList(),
        ) as Sr25519Keypair
        val another = SubstrateKeypairFactory.generate(
            EncryptionType.SR25519, ByteArray(32) { (it + 1).toByte() }, emptyList(),
        ) as Sr25519Keypair
        // The iOS native fixture establishes both byte order and public identity.
        // Android's independent JNI derivation must produce the same 96 bytes.
        val iosSecret = iosNativeSrPair.copyOfRange(0, 64)
        val iosPublic = iosNativeSrPair.copyOfRange(64, 96)
        try {
            assertArrayEquals(iosNativeSrPair, root.privateKey + root.nonce + root.publicKey)
            val genuine = encodedRoot(iosPublic, iosSecret, null)
            try {
                val proof = PortableWalletRootSigningProof.verify(genuine)
                assertEquals(1, proof.substrateRoots)
                assertEquals(0, proof.unprovenRecoveryFields)
            } finally {
                genuine.fill(0)
            }

            val substituted = encodedRoot(iosPublic, another.privateKey + another.nonce, null)
            try {
                assertThrows(IllegalArgumentException::class.java) {
                    PortableWalletRootSigningProof.verify(substituted)
                }
            } finally {
                substituted.fill(0)
            }

            val ambiguous = encodedRoot(iosPublic, iosSecret, root.nonce)
            try {
                assertThrows(IllegalArgumentException::class.java) {
                    PortableWalletRootSigningProof.verify(ambiguous)
                }
            } finally {
                ambiguous.fill(0)
            }

            val truncated = encodedRoot(iosPublic, iosSecret.copyOf(63), null)
            try {
                assertThrows(IllegalArgumentException::class.java) {
                    PortableWalletRootSigningProof.verify(truncated)
                }
            } finally {
                truncated.fill(0)
            }
        } finally {
            iosSecret.fill(0)
            iosPublic.fill(0)
            root.privateKey.fill(0)
            root.nonce.fill(0)
            another.privateKey.fill(0)
            another.nonce.fill(0)
        }
    }

    private fun encodedRoot(
        publicKey: ByteArray,
        privateKey: ByteArray,
        nonce: ByteArray?,
    ): ByteArray {
        val slot = PortableWalletSemanticMaterial.Slot(
            PortableWalletSemanticMaterial.Role.SUBSTRATE_ROOT,
            "",
            listOf(
                value(field.PUBLIC_KEY, publicKey),
                value(field.PRIVATE_KEY, privateKey),
                *listOfNotNull(nonce?.let { value(field.NONCE, it) }).toTypedArray(),
                value(field.ACCOUNT_ID_OR_ADDRESS, publicKey),
                value(field.CRYPTO_TYPE, byteArrayOf(1)),
                value(field.SOURCE_RECIPE, byteArrayOf(0)),
            ),
        )
        val source = PortableWalletSemanticMaterial.Snapshot(
            0,
            listOf(
                PortableWalletSemanticMaterial.Wallet(
                    ByteArray(16) { (it + 1).toByte() }, 0, true, "SR25519", emptyList(), listOf(slot),
                ),
            ),
        )
        return try {
            codec.encode(source)
        } finally {
            source.clearSecrets()
        }
    }

    private fun value(id: Int, bytes: ByteArray) = PortableWalletSemanticMaterial.Field(id, bytes.copyOf())
}
