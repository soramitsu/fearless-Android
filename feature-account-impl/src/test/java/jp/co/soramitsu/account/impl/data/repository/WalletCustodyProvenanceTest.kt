package jp.co.soramitsu.account.impl.data.repository

import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.coredb.model.ChainAccountLocal
import jp.co.soramitsu.coredb.model.MetaAccountLocal
import jp.co.soramitsu.coredb.model.WalletCustodyLocal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WalletCustodyProvenanceTest {
    @Test
    fun `missing marker remains unknown even for a complete public identity`() {
        assertEquals(
            WalletCustodyProvenance.Kind.UNKNOWN,
            WalletCustodyProvenance.classify(wallet(), emptyList(), null)
        )
    }

    @Test
    fun `watch marker binds wallet id roots and chain account identity`() {
        val meta = wallet()
        val chain = ChainAccountLocal(
            metaId = meta.id, chainId = "chain-a", publicKey = ByteArray(32) { 3 },
            accountId = ByteArray(32) { 3 }, cryptoType = CryptoType.ED25519,
            name = "Child", initialized = true
        )
        val marker = WalletCustodyProvenance.watchMarker(meta, listOf(chain))
        assertEquals(
            WalletCustodyProvenance.Kind.WATCH,
            WalletCustodyProvenance.classify(meta, listOf(chain), marker)
        )
        val independentlyAddedChain = ChainAccountLocal(
            metaId = meta.id, chainId = "chain-b", publicKey = ByteArray(32) { 4 },
            accountId = ByteArray(32) { 4 }, cryptoType = CryptoType.ED25519,
            name = "Later", initialized = true
        )
        assertThrows(IllegalArgumentException::class.java) {
            WalletCustodyProvenance.classify(meta, listOf(chain, independentlyAddedChain), marker)
        }
        meta.id = 9
        assertThrows(IllegalArgumentException::class.java) {
            WalletCustodyProvenance.classify(meta, listOf(chain), marker)
        }
        meta.id = 7
        chain.accountId[0] = 4
        assertThrows(IllegalArgumentException::class.java) {
            WalletCustodyProvenance.classify(meta, listOf(chain), marker)
        }
    }

    @Test
    fun `signed marker binds roots while later derived chains still require secret proof`() {
        val meta = wallet()
        val marker = WalletCustodyProvenance.signedMarker(meta)
        val laterChain = ChainAccountLocal(
            metaId = meta.id, chainId = "chain-b", publicKey = ByteArray(32) { 6 },
            accountId = ByteArray(32) { 6 }, cryptoType = CryptoType.SR25519,
            name = "Derived", initialized = false
        )
        assertEquals(
            WalletCustodyProvenance.Kind.SIGNED,
            WalletCustodyProvenance.classify(meta, listOf(laterChain), marker)
        )
        meta.substratePublicKey!![0] = 9
        assertThrows(IllegalArgumentException::class.java) {
            WalletCustodyProvenance.classify(meta, listOf(laterChain), marker)
        }
    }

    @Test
    fun `malformed or unknown markers fail closed`() {
        val meta = wallet()
        val marker = WalletCustodyProvenance.watchMarker(meta, emptyList())
        assertThrows(IllegalArgumentException::class.java) {
            WalletCustodyProvenance.classify(
                meta, emptyList(),
                WalletCustodyLocal(meta.id, "FUTURE", marker.publicIdentitySha256)
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            WalletCustodyProvenance.classify(
                meta, emptyList(),
                WalletCustodyLocal(meta.id, WalletCustodyLocal.WATCH, byteArrayOf(1))
            )
        }
    }

    @Test
    fun `watch enrollment rejects a mismatched public account and incomplete roots`() {
        val wrong = wallet()
        wrong.substrateAccountId!![0] = 2
        assertThrows(IllegalArgumentException::class.java) {
            WalletCustodyProvenance.watchMarker(wrong, emptyList())
        }
        val incomplete = MetaAccountLocal(
            substratePublicKey = null, substrateCryptoType = null, substrateAccountId = null,
            ethereumPublicKey = byteArrayOf(1), ethereumAddress = null, tonPublicKey = null,
            name = "Watch", isSelected = true, position = 0, isBackedUp = false,
            googleBackupAddress = null, initialized = false
        ).apply { id = 8 }
        assertThrows(IllegalArgumentException::class.java) {
            WalletCustodyProvenance.watchMarker(incomplete, emptyList())
        }
    }

    @Test
    fun `named Bitcoin and Taira public chain identities remain unsupported`() {
        val meta = wallet()
        listOf("bitcoin:mainnet" to 33, "iroha3-taira" to 32).forEach { (chainId, keySize) ->
            val chain = ChainAccountLocal(
                metaId = meta.id,
                chainId = chainId,
                publicKey = ByteArray(keySize) { 2 },
                accountId = ByteArray(keySize) { 2 },
                cryptoType = if (keySize == 33) CryptoType.ECDSA else CryptoType.ED25519,
                name = "Public chain",
                initialized = true
            )
            assertThrows(IllegalArgumentException::class.java) {
                WalletCustodyProvenance.watchMarker(meta, listOf(chain))
            }
        }
    }

    private fun wallet(): MetaAccountLocal = MetaAccountLocal(
        substratePublicKey = ByteArray(32) { 1 },
        substrateCryptoType = CryptoType.SR25519,
        substrateAccountId = ByteArray(32) { 1 },
        ethereumPublicKey = null,
        ethereumAddress = null,
        tonPublicKey = null,
        name = "Watch", isSelected = true, position = 0, isBackedUp = false,
        googleBackupAddress = null, initialized = false
    ).apply { id = 7 }
}
