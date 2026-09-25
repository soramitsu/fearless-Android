package jp.co.soramitsu.account.impl.data.repository

import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.common.utils.ethereumAddressFromPublicKey
import jp.co.soramitsu.common.utils.tonAccountId
import jp.co.soramitsu.fearless_utils.encrypt.keypair.ethereum.EthereumKeypairFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.mnemonic.Mnemonic

class PortableWalletWatchIdentityProofTest {
    private val codec = PortableWalletSemanticMaterial
    private val role = PortableWalletSemanticMaterial.Role
    private val field = PortableWalletSemanticMaterial.FieldId

    @Test
    fun `proves substrate evm ton and chain watch identities from canonical plaintext`() {
        val wallet = completeWatchWallet()
        val encoded = codec.encode(PortableWalletSemanticMaterial.Snapshot(0, listOf(wallet)))
        try {
            assertEquals(4, PortableWalletWatchIdentityProof.verifyWallet(wallet))
            val counts = PortableWalletAndroidSourceCohortProof.verify(encoded, emptyList())
            assertEquals(1, counts.watchWallets)
            assertEquals(0, counts.signedWallets)
            val received = PortableWalletReceiveInstallPlan.decode(encoded)
            try {
                assertEquals(4, received.wallets.single().materials.size)
                assertEquals(
                    4,
                    received.blockers.count {
                        it.reason == PortableWalletReceiveInstallPlan.BlockerReason.WATCH_IDENTITY_UNPROVEN
                    },
                )
            } finally {
                received.clearSecrets()
            }
        } finally {
            wallet.clearSecrets()
            encoded.fill(0)
        }
    }

    @Test
    fun `rejects mismatched watch addresses for each public key family`() {
        listOf(
            field.ACCOUNT_ID_OR_ADDRESS to 0,
            field.ACCOUNT_ID_OR_ADDRESS to 1,
            field.ACCOUNT_ID_OR_ADDRESS to 2,
            field.ACCOUNT_ID_OR_ADDRESS to 3,
        ).forEach { (target, index) ->
            val wallet = completeWatchWallet()
            wallet.slots.filter { it.role == role.WATCH_IDENTITY }[index]
                .fields.single { it.id == target }.value[1] = 0x7f
            val encoded = codec.encode(PortableWalletSemanticMaterial.Snapshot(0, listOf(wallet)))
            try {
                assertThrows(IllegalArgumentException::class.java) {
                    PortableWalletAndroidSourceCohortProof.verify(encoded, emptyList())
                }
                assertThrows(IllegalArgumentException::class.java) {
                    PortableWalletReceiveInstallPlan.decode(encoded)
                }
            } finally {
                encoded.fill(0)
                wallet.clearSecrets()
            }
        }
    }

    @Test
    fun `accepts address-only EVM watch and rejects missing native TON key`() {
        val addressOnly = watchWallet(
            listOf(
                watch(
                    0,
                    bytes(field.ACCOUNT_ID_OR_ADDRESS, ByteArray(20) { 3 }),
                    one(field.WATCH_ECOSYSTEM, 2)
                )
            )
        )
        val addressOnlyBytes = codec.encode(PortableWalletSemanticMaterial.Snapshot(0, listOf(addressOnly)))
        try {
            assertEquals(1, PortableWalletWatchIdentityProof.verifyWallet(addressOnly))
            assertEquals(1, PortableWalletAndroidSourceCohortProof.verify(addressOnlyBytes, emptyList()).watchWallets)
            PortableWalletReceiveInstallPlan.decode(addressOnlyBytes).clearSecrets()
        } finally {
            addressOnlyBytes.fill(0)
            addressOnly.clearSecrets()
        }

        val nativeTon = completeWatchWallet()
        val ton = nativeTon.slots[2]
        val absentKey = watchWallet(listOf(watch(0, *ton.fields.filter { it.id != field.PUBLIC_KEY }.toTypedArray())))
        val absentKeyBytes = codec.encode(PortableWalletSemanticMaterial.Snapshot(0, listOf(absentKey)))
        try {
            assertThrows(IllegalArgumentException::class.java) {
                PortableWalletAndroidSourceCohortProof.verify(absentKeyBytes, emptyList())
            }
            assertThrows(IllegalArgumentException::class.java) {
                PortableWalletReceiveInstallPlan.decode(absentKeyBytes)
            }
        } finally {
            absentKeyBytes.fill(0)
            absentKey.clearSecrets()
            nativeTon.clearSecrets()
        }
    }

    @Test
    fun `rejects duplicate ecosystem within one watch wallet`() {
        val first = watch(
            0,
            bytes(field.ACCOUNT_ID_OR_ADDRESS, ByteArray(20) { 1 }),
            one(field.WATCH_ECOSYSTEM, 2)
        )
        val second = watch(
            1,
            bytes(field.ACCOUNT_ID_OR_ADDRESS, ByteArray(20) { 2 }),
            one(field.WATCH_ECOSYSTEM, 2)
        )
        val wallet = watchWallet(listOf(first, second))
        val encoded = codec.encode(PortableWalletSemanticMaterial.Snapshot(0, listOf(wallet)))
        try {
            assertThrows(IllegalArgumentException::class.java) {
                PortableWalletAndroidSourceCohortProof.verify(encoded, emptyList())
            }
            assertThrows(IllegalArgumentException::class.java) {
                PortableWalletReceiveInstallPlan.decode(encoded)
            }
        } finally {
            encoded.fill(0)
            wallet.clearSecrets()
        }
    }

    @Test
    fun `receiving rejects unqualified iOS TON JSON and named universal watch chains`() {
        val foreignTon = watchWallet(
            listOf(
                watch(
                    0,
                    bytes(field.PUBLIC_KEY, ByteArray(32) { 7 }),
                    bytes(field.ACCOUNT_ID_OR_ADDRESS, "{}".toByteArray()),
                    one(field.TON_CONTRACT_VERSION, 2),
                    one(field.TON_ADDRESS_ENCODING, 2),
                    one(field.WATCH_ECOSYSTEM, 3),
                )
            )
        )
        assertReceiveRejected(foreignTon)

        listOf(
            UniversalWalletRegistry.tonMainnetRegistryEntry.id,
            UniversalWalletRegistry.tonMainnetRegistryEntry.chainId,
        ).forEach { chainId ->
            assertReceiveRejected(
                watchWallet(
                    listOf(
                        watch(
                            0,
                            bytes(field.PUBLIC_KEY, ByteArray(32) { 8 }),
                            bytes(field.ACCOUNT_ID_OR_ADDRESS, ByteArray(32) { 8 }),
                            one(field.CRYPTO_TYPE, 1),
                            one(field.WATCH_ECOSYSTEM, 4),
                            bytes(field.WATCH_CHAIN_ID, chainId.toByteArray()),
                        )
                    )
                )
            )
        }
    }

    private fun assertReceiveRejected(wallet: PortableWalletSemanticMaterial.Wallet) {
        val encoded = codec.encode(PortableWalletSemanticMaterial.Snapshot(0, listOf(wallet)))
        try {
            assertThrows(IllegalArgumentException::class.java) {
                PortableWalletReceiveInstallPlan.decode(encoded)
            }
        } finally {
            encoded.fill(0)
            wallet.clearSecrets()
        }
    }

    private fun completeWatchWallet(): PortableWalletSemanticMaterial.Wallet {
        val substrate = ByteArray(32) { 8 }
        val evm = EthereumKeypairFactory.createWithPrivateKey(ByteArray(32).also { it[31] = 1 })
        val ton = PrivateKeyEd25519(Mnemonic.toSeed(List(11) { "abandon" } + "about"))
        val tonPublic = ton.publicKey().key.toByteArray()
        val tonHash = tonPublic.tonAccountId(false).removePrefix("0:")
        val tonAddress = byteArrayOf(0) + tonHash.chunked(2).map { it.toInt(16).toByte() }
        return watchWallet(
            listOf(
                watch(
                    0,
                    bytes(field.PUBLIC_KEY, substrate),
                    bytes(field.ACCOUNT_ID_OR_ADDRESS, substrate),
                    one(field.CRYPTO_TYPE, 1),
                    one(field.WATCH_ECOSYSTEM, 1)
                ),
                watch(
                    1,
                    bytes(field.PUBLIC_KEY, evm.publicKey),
                    bytes(field.ACCOUNT_ID_OR_ADDRESS, evm.publicKey.ethereumAddressFromPublicKey()),
                    one(field.WATCH_ECOSYSTEM, 2)
                ),
                watch(
                    2,
                    bytes(field.PUBLIC_KEY, tonPublic),
                    bytes(field.ACCOUNT_ID_OR_ADDRESS, tonAddress),
                    one(field.TON_CONTRACT_VERSION, 2),
                    one(field.TON_ADDRESS_ENCODING, 1),
                    one(field.WATCH_ECOSYSTEM, 3)
                ),
                watch(
                    3,
                    bytes(field.PUBLIC_KEY, substrate),
                    bytes(field.ACCOUNT_ID_OR_ADDRESS, substrate),
                    one(field.CRYPTO_TYPE, 1),
                    one(field.WATCH_ECOSYSTEM, 4),
                    bytes(field.WATCH_CHAIN_ID, "0x${"01".repeat(32)}".toByteArray())
                ),
            )
        )
    }

    private fun watchWallet(slots: List<PortableWalletSemanticMaterial.Slot>) =
        PortableWalletSemanticMaterial.Wallet(ByteArray(16) { (it + 1).toByte() }, 0, true, "Watch", emptyList(), slots)

    private fun watch(index: Int, vararg fields: PortableWalletSemanticMaterial.Field) =
        PortableWalletSemanticMaterial.Slot(role.WATCH_IDENTITY, index.toString(16).padStart(4, '0'), fields.sortedBy { it.id })

    private fun bytes(id: Int, value: ByteArray) = PortableWalletSemanticMaterial.Field(id, value.copyOf())

    private fun one(id: Int, value: Int) = bytes(id, byteArrayOf(value.toByte()))
}
