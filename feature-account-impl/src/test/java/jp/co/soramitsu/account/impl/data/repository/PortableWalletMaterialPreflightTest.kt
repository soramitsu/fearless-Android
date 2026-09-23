package jp.co.soramitsu.account.impl.data.repository

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecrets
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecrets
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.secrets.v3.TonSecrets
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.coredb.dao.MetaAccountDao
import jp.co.soramitsu.coredb.model.ChainAccountLocal
import jp.co.soramitsu.coredb.model.MetaAccountLocal
import jp.co.soramitsu.coredb.model.RelationJoinedMetaAccountInfo
import jp.co.soramitsu.coredb.model.chain.FavoriteChainLocal
import jp.co.soramitsu.fearless_utils.scale.EncodableStruct
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class PortableWalletMaterialPreflightTest {
    private val metaAccountDao = mock<MetaAccountDao>()
    private val accountRepository = mock<AccountRepository>()
    private val preflight = PortableWalletMaterialPreflight(metaAccountDao, accountRepository)

    @Before
    fun setUp() {
        runBlocking { whenever(accountRepository.isWalletRecoveryRequired(any())).thenReturn(false) }
    }

    @Test
    fun `reads every root and per-chain key across distinct wallets`(): Unit = runBlocking {
        val first = wallet(
            id = 1, substrate = true, ethereum = true,
            chains = listOf(chain(1, "chain-a"), chain(1, "chain-b"))
        )
        val second = wallet(id = 2, ton = true)
        whenever(metaAccountDao.getJoinedMetaAccountsInfo()).thenReturn(listOf(second, first))
        whenever(accountRepository.getSubstrateSecrets(1)).thenReturn(mock<EncodableStruct<SubstrateSecrets>>())
        whenever(accountRepository.getEthereumSecrets(1)).thenReturn(mock<EncodableStruct<EthereumSecrets>>())
        whenever(accountRepository.getTonSecrets(2)).thenReturn(mock<EncodableStruct<TonSecrets>>())
        whenever(accountRepository.getChainAccountSecrets(1, "chain-a"))
            .thenReturn(mock<EncodableStruct<ChainAccountSecrets>>())
        whenever(accountRepository.getChainAccountSecrets(1, "chain-b"))
            .thenReturn(mock<EncodableStruct<ChainAccountSecrets>>())

        assertEquals(PortableWalletMaterialPreflight.Coverage(2, 1, 1, 1, 2), preflight.verifyCoverage())

        verify(accountRepository).getSubstrateSecrets(1)
        verify(accountRepository).getEthereumSecrets(1)
        verify(accountRepository).getTonSecrets(2)
        verify(accountRepository).getChainAccountSecrets(1, "chain-a")
        verify(accountRepository).getChainAccountSecrets(1, "chain-b")
    }

    @Test
    fun `accepts a standalone EVM key without inventing a Substrate root`(): Unit = runBlocking {
        whenever(metaAccountDao.getJoinedMetaAccountsInfo()).thenReturn(listOf(wallet(7, ethereum = true)))
        whenever(accountRepository.getEthereumSecrets(7)).thenReturn(mock<EncodableStruct<EthereumSecrets>>())

        assertEquals(PortableWalletMaterialPreflight.Coverage(1, 0, 1, 0, 0), preflight.verifyCoverage())

        verify(accountRepository).getEthereumSecrets(7)
        verify(accountRepository, org.mockito.kotlin.never()).getSubstrateSecrets(7)
    }

    @Test
    fun `rejects a legacy-only wallet until its material has an explicit portable representation`(): Unit = runBlocking {
        whenever(metaAccountDao.getJoinedMetaAccountsInfo()).thenReturn(listOf(wallet(3, substrate = true)))

        assertThrows(IllegalStateException::class.java) {
            runBlocking { preflight.verifyCoverage() }
        }

        verify(accountRepository).getSubstrateSecrets(3)
    }

    @Test
    fun `rejects a missing independent EVM key even when Substrate is readable`(): Unit = runBlocking {
        whenever(metaAccountDao.getJoinedMetaAccountsInfo())
            .thenReturn(listOf(wallet(4, substrate = true, ethereum = true)))
        whenever(accountRepository.getSubstrateSecrets(4)).thenReturn(mock<EncodableStruct<SubstrateSecrets>>())

        assertThrows(IllegalStateException::class.java) {
            runBlocking { preflight.verifyCoverage() }
        }

        verify(accountRepository).getEthereumSecrets(4)
    }

    @Test
    fun `rejects a missing chain-specific key instead of silently dropping its address`(): Unit = runBlocking {
        whenever(metaAccountDao.getJoinedMetaAccountsInfo())
            .thenReturn(listOf(wallet(5, chains = listOf(chain(5, "chain-a")))))

        assertThrows(IllegalStateException::class.java) {
            runBlocking { preflight.verifyCoverage() }
        }

        verify(accountRepository).getChainAccountSecrets(5, "chain-a")
    }

    @Test
    fun `rejects wallet metadata changed while secrets were read`(): Unit = runBlocking {
        val before = wallet(8, ethereum = true, name = "Before")
        val after = wallet(8, ethereum = true, name = "After")
        whenever(metaAccountDao.getJoinedMetaAccountsInfo())
            .thenReturn(listOf(before), listOf(after))
        whenever(accountRepository.getEthereumSecrets(8)).thenReturn(mock<EncodableStruct<EthereumSecrets>>())

        assertThrows(IllegalStateException::class.java) {
            runBlocking { preflight.verifyCoverage() }
        }
    }

    @Test
    fun `rejects favorite-chain changes during material validation`(): Unit = runBlocking {
        val before = wallet(8, ethereum = true, favorites = listOf(FavoriteChainLocal(8, "chain-a", true)))
        val after = wallet(8, ethereum = true, favorites = listOf(FavoriteChainLocal(8, "chain-a", false)))
        whenever(metaAccountDao.getJoinedMetaAccountsInfo())
            .thenReturn(listOf(before), listOf(after))
        whenever(accountRepository.getEthereumSecrets(8)).thenReturn(mock<EncodableStruct<EthereumSecrets>>())

        assertThrows(IllegalStateException::class.java) {
            runBlocking { preflight.verifyCoverage() }
        }
    }

    @Test
    fun `rejects recovery-required wallets before opening any material`(): Unit = runBlocking {
        whenever(metaAccountDao.getJoinedMetaAccountsInfo()).thenReturn(listOf(wallet(9, ton = true)))
        whenever(accountRepository.isWalletRecoveryRequired(9)).thenReturn(true)

        assertThrows(IllegalStateException::class.java) {
            runBlocking { preflight.verifyCoverage() }
        }

        verify(accountRepository).isWalletRecoveryRequired(9)
        verify(accountRepository, org.mockito.kotlin.never()).getTonSecrets(9)
    }

    @Test
    fun `rejects incomplete public identities without reading keys`(): Unit = runBlocking {
        val incomplete = MetaAccountLocal(
            substratePublicKey = byteArrayOf(1), substrateCryptoType = null,
            substrateAccountId = byteArrayOf(2), ethereumPublicKey = null,
            ethereumAddress = null, tonPublicKey = null, name = "Incomplete",
            isSelected = true, position = 0, isBackedUp = false,
            googleBackupAddress = null, initialized = true
        ).apply { id = 10 }
        whenever(metaAccountDao.getJoinedMetaAccountsInfo())
            .thenReturn(listOf(RelationJoinedMetaAccountInfo(incomplete, emptyList(), emptyList())))

        assertThrows(IllegalStateException::class.java) {
            runBlocking { preflight.verifyCoverage() }
        }

        verifyNoInteractions(accountRepository)
    }

    private fun wallet(
        id: Long,
        substrate: Boolean = false,
        ethereum: Boolean = false,
        ton: Boolean = false,
        chains: List<ChainAccountLocal> = emptyList(),
        favorites: List<FavoriteChainLocal> = emptyList(),
        name: String = "Wallet"
    ): RelationJoinedMetaAccountInfo {
        val meta = MetaAccountLocal(
            substratePublicKey = if (substrate) byteArrayOf(1, id.toByte()) else null,
            substrateCryptoType = if (substrate) CryptoType.SR25519 else null,
            substrateAccountId = if (substrate) byteArrayOf(2, id.toByte()) else null,
            ethereumPublicKey = if (ethereum) byteArrayOf(3, id.toByte()) else null,
            ethereumAddress = if (ethereum) byteArrayOf(4, id.toByte()) else null,
            tonPublicKey = if (ton) byteArrayOf(5, id.toByte()) else null,
            name = name, isSelected = id == 1L, position = id.toInt(),
            isBackedUp = false, googleBackupAddress = null, initialized = true
        ).apply { this.id = id }
        return RelationJoinedMetaAccountInfo(meta, chains, favorites)
    }

    private fun chain(metaId: Long, chainId: String) = ChainAccountLocal(
        metaId, chainId, byteArrayOf(6), byteArrayOf(7), CryptoType.ED25519,
        "Chain account", true
    )
}
