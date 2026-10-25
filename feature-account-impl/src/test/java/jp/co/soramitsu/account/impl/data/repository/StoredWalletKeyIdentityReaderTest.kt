package jp.co.soramitsu.account.impl.data.repository

import jp.co.soramitsu.account.impl.domain.recovery.WalletKeyIdentity
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretScope
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.coredb.dao.MetaAccountDao
import jp.co.soramitsu.coredb.model.ChainAccountLocal
import jp.co.soramitsu.coredb.model.MetaAccountLocal
import jp.co.soramitsu.coredb.model.RelationJoinedMetaAccountInfo
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class StoredWalletKeyIdentityReaderTest {
    private val dao = mock<MetaAccountDao>()
    private val reader = StoredWalletKeyIdentityReader(dao)
    private val wallet = MetaAccountLocal(
        substratePublicKey = ByteArray(32) { 1 }, substrateAccountId = ByteArray(32) { 1 },
        substrateCryptoType = CryptoType.ED25519, ethereumPublicKey = ByteArray(33) { 2 },
        ethereumAddress = ByteArray(20) { 3 }, tonPublicKey = ByteArray(32) { 4 },
        name = "Historical", isSelected = true, position = 0, isBackedUp = false,
        googleBackupAddress = null, initialized = true
    ).apply { id = 7 }

    @Test
    fun allRootTargetsComeFromTheStoredWalletAndMissingTargetsFail() = runTest {
        whenever(dao.getMetaAccount(7)).thenReturn(wallet)
        val substrate = reader.read(7, WalletSecretScope.Substrate) as WalletKeyIdentity.Substrate
        val ethereum = reader.read(7, WalletSecretScope.Ethereum) as WalletKeyIdentity.Ethereum
        val ton = reader.read(7, WalletSecretScope.Ton) as WalletKeyIdentity.Ton
        assertArrayEquals(wallet.substratePublicKey, substrate.publicKey)
        assertArrayEquals(wallet.substrateAccountId, substrate.accountId)
        assertEquals(wallet.substrateCryptoType, substrate.cryptoType)
        assertArrayEquals(wallet.ethereumPublicKey, ethereum.publicKey)
        assertArrayEquals(wallet.ethereumAddress, ethereum.address)
        assertArrayEquals(wallet.tonPublicKey, ton.publicKey)
        assertTrue(runCatching { reader.read(8, WalletSecretScope.Ton) }.isFailure)
    }

    @Test
    fun conflictingNetworkRowsCannotAuthorizeAReplacement() = runTest {
        whenever(dao.getMetaAccount(7)).thenReturn(wallet)
        val accountId = ByteArray(32) { 9 }
        val first = ChainAccountLocal(7, "network-a", ByteArray(32) { 9 }, accountId, CryptoType.ED25519, "a", true)
        whenever(dao.getJoinedMetaAccountInfo(7)).thenReturn(RelationJoinedMetaAccountInfo(wallet, listOf(first), emptyList()))
        val identity = reader.read(7, WalletSecretScope.chainAccount(accountId)) as WalletKeyIdentity.Chain
        assertArrayEquals(first.publicKey, identity.publicKey)
        val conflicting = ChainAccountLocal(7, "network-b", ByteArray(32) { 8 }, accountId, CryptoType.ED25519, "b", true)
        whenever(dao.getJoinedMetaAccountInfo(7)).thenReturn(RelationJoinedMetaAccountInfo(wallet, listOf(first, conflicting), emptyList()))
        assertTrue(runCatching { reader.read(7, WalletSecretScope.chainAccount(accountId)) }.isFailure)
        assertTrue(runCatching { reader.read(7, WalletSecretScope.chainAccount(ByteArray(33))) }.isFailure)
    }

    companion object {
        @JvmStatic
        @BeforeClass
        fun enableByteBuddyForJava21TestRuntime() {
            System.setProperty("net.bytebuddy.experimental", "true")
        }
    }
}
