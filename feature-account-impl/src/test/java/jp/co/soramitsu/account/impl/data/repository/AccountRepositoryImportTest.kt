package jp.co.soramitsu.account.impl.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import jp.co.soramitsu.account.api.domain.interfaces.AccountAlreadyExistsException
import jp.co.soramitsu.account.impl.data.repository.datasource.AccountDataSource
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecrets
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.utils.ethereumAddressFromPublicKey
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.coredb.dao.MetaAccountDao
import jp.co.soramitsu.coredb.model.MetaAccountLocal
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.MultiChainEncryption
import jp.co.soramitsu.fearless_utils.encrypt.json.JsonSeedDecoder
import jp.co.soramitsu.fearless_utils.encrypt.keypair.ethereum.EthereumKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.model.ImportAccountData
import jp.co.soramitsu.fearless_utils.encrypt.model.NetworkTypeIdentifier
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.bouncycastle.util.encoders.Hex
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountRepositoryImportTest {
    private val source = mockk<AccountDataSource>()
    private val dao = mockk<MetaAccountDao>()
    private val decoder = mockk<JsonSeedDecoder>()
    private val mutations = mockk<WalletSecretMutationCoordinator>()
    private val row = slot<MetaAccountLocal>()
    private val substrate = slot<String>()
    private val ethereum = slot<String>()
    private val seed = ByteArray(32) { 7 }
    private val ethSeed = ByteArray(32) { 9 }

    private fun repository() = AccountRepositoryImpl(
        source, dao, mockk(), decoder, mockk(), mockk(), mockk(),
        mockk(), mockk(), mockk(), mockk(), mutations, mockk(), UnconfinedTestDispatcher()
    )

    private fun existing() = MetaAccountLocal(
        ByteArray(32) { 1 }, CryptoType.ED25519, ByteArray(32) { 1 }, null, null,
        ByteArray(32) { 2 }, "Existing", false, 4, false, "old-backup", true
    ).apply { id = 17 }

    private fun jsonData(ethereum: Boolean = false): ImportAccountData {
        val keys = if (ethereum) EthereumKeypairFactory.createWithPrivateKey(ethSeed)
        else SubstrateKeypairFactory.generate(EncryptionType.ED25519, seed, emptyList())
        return ImportAccountData(
            keys,
            if (ethereum) MultiChainEncryption.Ethereum else MultiChainEncryption.Substrate(EncryptionType.ED25519),
            "JSON name", NetworkTypeIdentifier.Undefined, if (ethereum) ethSeed else seed
        )
    }

    @Test
    fun seedImportPersistsBothKeysAndTheRequestedWalletIdentity() = runTest {
        coEvery { mutations.create(capture(row), capture(substrate), capture(ethereum), null) } returns 19
        repository().importFromSeed(null, Hex.toHexString(seed), "Imported", "", CryptoType.ED25519,
            "0x" + Hex.toHexString(ethSeed), "backup")

        assertEquals("Imported", row.captured.name)
        assertEquals("backup", row.captured.googleBackupAddress)
        assertTrue(row.captured.isSelected)
        assertTrue(row.captured.isBackedUp)
        assertFalse(row.captured.initialized)
        assertEquals(0L, row.captured.id)
        assertEquals(CryptoType.ED25519, row.captured.substrateCryptoType)
        val sub = SubstrateSecrets.read(substrate.captured)
        val eth = EthereumSecrets.read(ethereum.captured)
        assertArrayEquals(seed, sub[SubstrateSecrets.Seed])
        assertEquals("", sub[SubstrateSecrets.SubstrateDerivationPath])
        assertArrayEquals(ethSeed, eth[EthereumSecrets.Seed])
        assertArrayEquals(row.captured.substratePublicKey, sub[SubstrateSecrets.SubstrateKeypair][KeyPairSchema.PublicKey])
        assertArrayEquals(row.captured.ethereumAddress, requireNotNull(row.captured.ethereumPublicKey).ethereumAddressFromPublicKey())
        coVerify(exactly = 0) { dao.selectMetaAccount(any()) }
    }

    @Test
    fun substrateOnlySeedDoesNotManufactureEthereumMaterial() = runTest {
        coEvery { mutations.create(capture(row), capture(substrate), null, null) } returns 19
        repository().importFromSeed(null, "0x" + Hex.toHexString(seed), "Only DOT", "//7",
            CryptoType.ED25519, null, null)
        assertNull(row.captured.ethereumPublicKey)
        assertNull(row.captured.ethereumAddress)
        assertEquals("//7", SubstrateSecrets.read(substrate.captured)[SubstrateSecrets.SubstrateDerivationPath])
        assertArrayEquals(seed, SubstrateSecrets.read(substrate.captured)[SubstrateSecrets.Seed])
    }

    @Test
    fun additionalSeedPreservesExistingWalletFieldsAndReplacesOnlyEthereum() = runTest {
        val before = existing()
        coEvery { dao.getMetaAccount(17) } returns before
        coEvery { mutations.addEvm(before, capture(row), capture(ethereum)) } returns 17
        repository().importFromSeed(17, null, "ignored", "ignored", CryptoType.SR25519,
            Hex.toHexString(ethSeed), "ignored")
        assertEquals(17L, row.captured.id)
        assertEquals(before.name, row.captured.name)
        assertEquals(before.position, row.captured.position)
        assertEquals(before.googleBackupAddress, row.captured.googleBackupAddress)
        assertEquals(before.isSelected, row.captured.isSelected)
        assertEquals(before.initialized, row.captured.initialized)
        assertArrayEquals(before.substratePublicKey, row.captured.substratePublicKey)
        assertArrayEquals(before.tonPublicKey, row.captured.tonPublicKey)
        assertArrayEquals(ethSeed, EthereumSecrets.read(ethereum.captured)[EthereumSecrets.Seed])
        assertTrue(row.captured.isBackedUp)
    }

    @Test
    fun additionalImportWithoutEthereumSeedLeavesWalletUntouched() = runTest {
        repository().importFromSeed(17, null, "ignored", "", CryptoType.ED25519, null, null)
        coVerify(exactly = 0) { dao.getMetaAccount(any()) }
        coVerify(exactly = 0) { mutations.addEvm(any(), any(), any()) }
    }

    @Test
    fun additionalImportRejectsAnUnknownWalletBeforeMutation() = runTest {
        coEvery { dao.getMetaAccount(17) } returns null
        val failure = runCatching {
            repository().importFromSeed(17, null, "ignored", "", CryptoType.ED25519, Hex.toHexString(ethSeed), null)
        }.exceptionOrNull()
        assertEquals("Account not found", failure?.message)
        coVerify(exactly = 0) { mutations.addEvm(any(), any(), any()) }
    }

    @Test
    fun jsonImportKeepsDecodedKeysAndUsesTheSuppliedName() = runTest {
        val decoded = jsonData()
        every { decoder.decode("substrate-json", "password") } returns decoded
        every { decoder.decode("ethereum-json", "password") } returns jsonData(true)
        coEvery { mutations.create(capture(row), capture(substrate), capture(ethereum), null) } returns 19
        repository().importFromJson("substrate-json", "password", "Chosen name", "ethereum-json", "backup")
        assertEquals("Chosen name", row.captured.name)
        assertEquals("backup", row.captured.googleBackupAddress)
        assertArrayEquals(decoded.keypair.publicKey, row.captured.substratePublicKey)
        assertArrayEquals(seed, SubstrateSecrets.read(substrate.captured)[SubstrateSecrets.Seed])
        assertArrayEquals(ethSeed, EthereumSecrets.read(ethereum.captured)[EthereumSecrets.EthereumKeypair][KeyPairSchema.PrivateKey])
        assertNull(EthereumSecrets.read(ethereum.captured)[EthereumSecrets.Seed])
        assertEquals(CryptoType.ED25519, row.captured.substrateCryptoType)
    }

    @Test
    fun substrateJsonAloneLeavesOtherEcosystemsAbsent() = runTest {
        every { decoder.decode("json", "password") } returns jsonData()
        coEvery { mutations.create(capture(row), capture(substrate), null, null) } returns 19
        repository().importFromJson("json", "password", "Only DOT", null, null)
        assertNull(row.captured.ethereumAddress)
        assertNull(row.captured.tonPublicKey)
        assertEquals("Only DOT", row.captured.name)
    }

    @Test
    fun additionalJsonPreservesIdentityAndStoresTheDecodedEthereumKey() = runTest {
        val before = existing()
        every { decoder.decode("json", "password") } returns jsonData(true)
        coEvery { dao.getMetaAccount(17) } returns before
        coEvery { mutations.addEvm(before, capture(row), capture(ethereum)) } returns 17
        repository().importAdditionalFromJson(17, "json", "password")
        assertEquals(17L, row.captured.id)
        assertEquals(before.name, row.captured.name)
        assertEquals(before.position, row.captured.position)
        assertArrayEquals(before.tonPublicKey, row.captured.tonPublicKey)
        assertArrayEquals(ethSeed, EthereumSecrets.read(ethereum.captured)[EthereumSecrets.EthereumKeypair][KeyPairSchema.PrivateKey])
        assertNull(EthereumSecrets.read(ethereum.captured)[EthereumSecrets.Seed])
    }

    @Test
    fun duplicateCreationMapsToAccountAlreadyExistsButOtherFailuresRemainIntact() = runTest {
        every { decoder.decode("json", "password") } returns jsonData()
        val repo = repository()
        for (reason in listOf(WalletMutationFailureReason.IDENTITY_CONFLICT, WalletMutationFailureReason.DURABILITY_UNKNOWN)) {
            val failure = WalletSecretMutationCoordinatorException(reason, "refused")
            coEvery { mutations.create(any(), any(), any(), any()) } throws failure
            val actual = runCatching { repo.importFromJson("json", "password", "Name", null, null) }.exceptionOrNull()
            if (reason == WalletMutationFailureReason.IDENTITY_CONFLICT) assertTrue(actual is AccountAlreadyExistsException)
            else assertSame(failure, actual)
        }
    }

    @Test
    fun duplicateAdditionMapsToAccountAlreadyExistsButOtherFailuresRemainIntact() = runTest {
        val before = existing()
        coEvery { dao.getMetaAccount(17) } returns before
        every { decoder.decode("json", "password") } returns jsonData(true)
        val repo = repository()
        for (reason in listOf(WalletMutationFailureReason.IDENTITY_CONFLICT, WalletMutationFailureReason.RECOVERY_REQUIRED)) {
            val failure = WalletSecretMutationCoordinatorException(reason, "refused")
            coEvery { mutations.addEvm(any(), any(), any()) } throws failure
            val actual = runCatching { repo.importAdditionalFromJson(17, "json", "password") }.exceptionOrNull()
            if (reason == WalletMutationFailureReason.IDENTITY_CONFLICT) assertTrue(actual is AccountAlreadyExistsException)
            else assertSame(failure, actual)
        }
    }
}
