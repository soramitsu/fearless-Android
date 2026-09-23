package jp.co.soramitsu.account.impl.data.repository

import java.nio.ByteBuffer
import java.util.Base64
import jp.co.soramitsu.common.data.Keypair
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecrets
import jp.co.soramitsu.fearless_utils.scale.toByteArray
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PortableWalletMaterialDraftTest {
    private val format = PortableWalletMaterialDraft

    @Test
    fun `strict roundtrip preserves standalone EVM private key and metadata`() {
        val original = snapshot()
        val encoded = format.encode(original)
        val decoded = format.decode(encoded)
        try {
            assertArrayEquals(original.wallets.single().ethereumSecret, decoded.wallets.single().ethereumSecret)
            assertEquals(original.wallets.single().identity, decoded.wallets.single().identity)
            assertArrayEquals(encoded, format.encode(decoded))
            assertEquals("PortableWalletMaterialDraft.Wallet(redacted)", decoded.wallets.single().toString())
        } finally {
            original.clearSecrets()
            decoded.clearSecrets()
            encoded.fill(0)
        }
    }

    @Test
    fun `decoder rejects truncated extended malformed and unknown version records`() {
        val original = snapshot()
        val encoded = format.encode(original)
        try {
            val invalid = listOf(
                encoded.copyOf(encoded.size - 1),
                encoded + byteArrayOf(0),
                encoded.copyOf().also { it[11] = 2 },
                encoded.copyOf().also { ByteBuffer.wrap(it).putInt(24, Int.MAX_VALUE) },
                encoded.copyOf().also { it[28] = 0xff.toByte() }
            )
            invalid.forEach { candidate ->
                assertThrows(Exception::class.java) { format.decode(candidate).clearSecrets() }
                candidate.fill(0)
            }
        } finally {
            original.clearSecrets()
            encoded.fill(0)
        }
    }

    @Test
    fun `encoder rejects a secret whose public key differs from durable identity`() {
        val original = snapshot()
        val wrong = PortableWalletMaterialDraft.Snapshot(
            listOf(
                PortableWalletMaterialDraft.Wallet(
                    original.wallets.single().identity.copy(ethereumPublicKey = publicId(byteArrayOf(9, 9))),
                    null,
                    original.wallets.single().ethereumSecret!!.copyOf(),
                    null,
                    emptyList()
                )
            )
        )
        try {
            assertThrows(IllegalArgumentException::class.java) { format.encode(wrong) }
        } finally {
            original.clearSecrets()
            wrong.clearSecrets()
        }
    }

    @Test
    fun `encoder rejects duplicate wallet IDs and missing selected wallet`() {
        val original = snapshot()
        val same = original.wallets.single()
        try {
            assertThrows(IllegalArgumentException::class.java) {
                format.encode(PortableWalletMaterialDraft.Snapshot(listOf(same, same)))
            }
            assertThrows(IllegalArgumentException::class.java) {
                format.encode(PortableWalletMaterialDraft.Snapshot(listOf(PortableWalletMaterialDraft.Wallet(
                    same.identity.copy(isSelected = false), null,
                    same.ethereumSecret, null, emptyList()
                ))))
            }
        } finally {
            original.clearSecrets()
        }
    }

    private fun snapshot(): PortableWalletMaterialDraft.Snapshot {
        val publicKey = byteArrayOf(3, 1)
        val ethereum = EthereumSecrets(ethereumKeypair = Keypair(publicKey, ByteArray(32) { 99 }))
        val identity = PortableWalletMaterialDraft.WalletIdentity(
            id = 1,
            name = "W",
            isSelected = true,
            position = 0,
            initialized = true,
            substratePublicKey = null,
            substrateCryptoType = null,
            substrateAccountId = null,
            ethereumPublicKey = publicId(publicKey),
            ethereumAddress = publicId(byteArrayOf(4, 1)),
            tonPublicKey = null,
            chainAccounts = emptyList(),
            favoriteChains = emptyList()
        )
        return PortableWalletMaterialDraft.Snapshot(listOf(
            PortableWalletMaterialDraft.Wallet(identity, null, ethereum.toByteArray(), null, emptyList())
        ))
    }

    private fun publicId(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
}
