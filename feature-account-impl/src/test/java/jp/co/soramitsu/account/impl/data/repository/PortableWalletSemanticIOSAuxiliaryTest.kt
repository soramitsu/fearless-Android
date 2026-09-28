package jp.co.soramitsu.account.impl.data.repository

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PortableWalletSemanticIOSAuxiliaryTest {
    private val codec = PortableWalletSemanticMaterial
    private val role = PortableWalletSemanticMaterial.Role
    private val field = PortableWalletSemanticMaterial.FieldId

    @Test
    fun `account scoped iOS TON keychain source remains bound to exact chain account`() {
        val source = snapshot(bindingAccount = 0x20)
        val encoded = codec.encode(source)
        val decoded = codec.decode(encoded)
        try {
            assertArrayEquals(encoded, codec.encode(decoded))
            val auxiliary = decoded.wallets.single().slots.last()
            assertArrayEquals(byteArrayOf(3), auxiliary.value(field.SOURCE_SLOT_ROLE))
            assertArrayEquals(byteArrayOf(5), auxiliary.value(field.BINDING_KIND))
            assertArrayEquals("ton-chain".toByteArray(), auxiliary.value(field.BINDING_CHAIN_ID))
            assertArrayEquals(byteArrayOf(0x20), auxiliary.value(field.BINDING_ACCOUNT_ID))
            assertArrayEquals(byteArrayOf(0x33), auxiliary.value(field.SOURCE_BYTES))
        } finally {
            source.clearSecrets()
            decoded.clearSecrets()
            encoded.fill(0)
        }
    }

    @Test
    fun `account scoped iOS TON source rejects another chain identity`() {
        val source = snapshot(bindingAccount = 0x21)
        try {
            assertThrows(IllegalArgumentException::class.java) { codec.encode(source) }
        } finally {
            source.clearSecrets()
        }
    }

    private fun snapshot(bindingAccount: Int): PortableWalletSemanticMaterial.Snapshot {
        val root = PortableWalletSemanticMaterial.Slot(
            role.SUBSTRATE_ROOT,
            "",
            listOf(
                value(field.PUBLIC_KEY, 0x40),
                value(field.PRIVATE_KEY, 0x41),
                value(field.ACCOUNT_ID_OR_ADDRESS, 0x42),
                value(field.CRYPTO_TYPE, 2),
                value(field.SOURCE_RECIPE, 0),
            ),
        )
        val account = PortableWalletSemanticMaterial.Slot(
            role.CHAIN_ACCOUNT,
            "ton-chain",
            listOf(
                value(field.PUBLIC_KEY, 0x10),
                value(field.PRIVATE_KEY, 0x11),
                value(field.ACCOUNT_ID_OR_ADDRESS, 0x20),
                value(field.CRYPTO_TYPE, 2),
                PortableWalletSemanticMaterial.Field(field.CHAIN_NAME, ByteArray(0)),
                value(field.INITIALIZED_OR_FAVORITE, 1),
                value(field.SOURCE_RECIPE, 0),
            ),
        )
        val auxiliary = PortableWalletSemanticMaterial.Slot(
            role.AUXILIARY_SOURCE,
            "0000",
            listOf(
                value(field.SOURCE_RECIPE, 0),
                value(field.SOURCE_PLATFORM, 2),
                value(field.SOURCE_SLOT_ROLE, 3),
                value(field.BINDING_KIND, 5),
                PortableWalletSemanticMaterial.Field(
                    field.BINDING_CHAIN_ID,
                    "ton-chain".toByteArray(),
                ),
                value(field.SOURCE_FORMAT, 1),
                value(field.SOURCE_BYTES, 0x33),
                value(field.BINDING_ACCOUNT_ID, bindingAccount),
            ),
        )
        return PortableWalletSemanticMaterial.Snapshot(
            0,
            listOf(
                PortableWalletSemanticMaterial.Wallet(
                    ByteArray(16) { (it + 1).toByte() },
                    0,
                    true,
                    "iOS",
                    emptyList(),
                    listOf(root, account, auxiliary),
                ),
            ),
        )
    }

    private fun value(id: Int, byte: Int) = PortableWalletSemanticMaterial.Field(id, byteArrayOf(byte.toByte()))

    private fun PortableWalletSemanticMaterial.Slot.value(id: Int): ByteArray = fields.single { it.id == id }.value
}
