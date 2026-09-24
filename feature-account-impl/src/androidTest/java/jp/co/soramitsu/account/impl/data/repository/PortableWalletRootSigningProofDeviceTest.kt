package jp.co.soramitsu.account.impl.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
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

    private fun encodedRoot(
        publicKey: ByteArray,
        privateKey: ByteArray,
        nonce: ByteArray,
    ): ByteArray {
        val slot = PortableWalletSemanticMaterial.Slot(
            PortableWalletSemanticMaterial.Role.SUBSTRATE_ROOT,
            "",
            listOf(
                value(field.PUBLIC_KEY, publicKey),
                value(field.PRIVATE_KEY, privateKey),
                value(field.NONCE, nonce),
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
