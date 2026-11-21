package jp.co.soramitsu.account.impl.domain

import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.AddAccountPayload
import jp.co.soramitsu.account.api.domain.model.ImportJsonData
import jp.co.soramitsu.account.api.domain.model.MetaAccountOrdering
import jp.co.soramitsu.account.impl.presentation.VmTestFixtures
import jp.co.soramitsu.backup.BackupService
import jp.co.soramitsu.backup.domain.models.BackupAccountMeta
import jp.co.soramitsu.backup.domain.models.BackupAccountType
import jp.co.soramitsu.backup.domain.models.DecryptedBackupAccount
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecrets
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.storage.encrypt.PinHealth
import jp.co.soramitsu.common.model.WalletEcosystem
import jp.co.soramitsu.core.model.Language
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.keypair.ethereum.EthereumKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.Mnemonic
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.MnemonicCreator
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.runtime.multiNetwork.chain.model.moonriverChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polkadotChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.westendChainId
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountInteractorOperationsTest {
    private val repository = mockk<AccountRepository>(relaxed = true)
    private val backup = mockk<BackupService>(relaxed = true)
    private val walletInteractor = mockk<WalletInteractor>(relaxed = true)
    private val wallet = VmTestFixtures.meta()
    private val interactor = AccountInteractorImpl(repository, mockk(), mockk(relaxed = true), backup,
        walletInteractor, UnconfinedTestDispatcher())

    @Test
    fun accountCreationAndImportReturnSuccessAndRetainTheOriginalFailure() = runTest {
        val payload = mockk<AddAccountPayload.SubstrateOrEvm>()
        coEvery { repository.createAccount(payload) } returns 42
        assertEquals(42L, interactor.createAccount(payload).getOrThrow())
        val failure = IllegalStateException("storage unavailable")
        coEvery { repository.createAccount(payload) } throws failure
        assertSame(failure, interactor.createAccount(payload).exceptionOrNull())
        interactor.importFromSeed(7, "sub", "Name", "//4", CryptoType.ED25519, "eth", "drive").getOrThrow()
        coVerify { repository.importFromSeed(7, "sub", "Name", "//4", CryptoType.ED25519, "eth", "drive") }
        coEvery { repository.importFromSeed(any(), any(), any(), any(), any(), any(), any()) } throws failure
        assertSame(failure, interactor.importFromSeed(null, "sub", "Name", "", CryptoType.ED25519, null, null).exceptionOrNull())
    }

    @Test
    fun jsonImportRoutesNewAndAdditionalWalletsAndHandlesMissingInput() = runTest {
        assertTrue(interactor.importFromJson(null, "sub", "pw", "Name", "eth", "drive").isSuccess)
        assertTrue(interactor.importFromJson(7, "ignored", "pw", "ignored", "eth", null).isSuccess)
        assertTrue(interactor.importFromJson(null, null, "pw", "Name", "eth", null).isSuccess)
        assertTrue(interactor.importFromJson(7, "sub", "pw", "Name", null, null).isSuccess)
        coVerify(exactly = 1) { repository.importFromJson("sub", "pw", "Name", "eth", "drive") }
        coVerify(exactly = 1) { repository.importAdditionalFromJson(7, "eth", "pw") }
        val failure = IllegalArgumentException("invalid JSON")
        coEvery { repository.importFromJson(any(), any(), any(), any(), any()) } throws failure
        assertSame(failure, interactor.importFromJson(null, "bad", "pw", "Name", null, null).exceptionOrNull())
    }

    @Test
    fun accountQueriesAndFlowsPreserveTheRequestedWallet() = runTest {
        coEvery { repository.getSelectedMetaAccount() } returns wallet
        coEvery { repository.getSelectedLightMetaAccount() } returns wallet
        coEvery { repository.getMetaAccount(7) } returns wallet
        coEvery { repository.getLightMetaAccount(7) } returns wallet
        every { repository.selectedMetaAccountFlow() } returns flowOf(wallet)
        every { repository.selectedLightMetaAccountFlow() } returns flowOf(wallet)
        every { repository.lightMetaAccountsFlow() } returns flowOf(listOf(wallet))
        assertSame(wallet, interactor.selectedMetaAccount())
        assertSame(wallet, interactor.selectedLightMetaAccount())
        assertSame(wallet, interactor.getMetaAccount(7))
        assertSame(wallet, interactor.getLightMetaAccount(7))
        assertSame(wallet, interactor.selectedMetaAccountFlow().first())
        assertSame(wallet, interactor.selectedLightMetaAccountFlow().first())
        assertEquals(listOf(wallet), interactor.lightMetaAccountsFlow().first())
    }

    @Test
    fun preferencesAndAccountEditsReachTheCorrectWallet() = runTest {
        val ordering = slot<List<MetaAccountOrdering>>()
        coEvery { repository.updateAccountsOrdering(capture(ordering)) } returns Unit
        interactor.updateAccountPositionsInNetwork(listOf(9, 7, 2))
        assertEquals(listOf(9L to 0, 7L to 1, 2L to 2), ordering.captured.map { it.id to it.position })
        interactor.selectMetaAccount(7)
        interactor.deleteAccount(9)
        interactor.updateAccountName(7, "Renamed")
        interactor.updateWalletBackedUp(7)
        interactor.updateFavoriteChain("dot", false, 7)
        interactor.saveChainSelectFilter(7, "assets")
        coVerify { repository.selectMetaAccount(7) }
        coVerify { repository.deleteAccount(9) }
        coVerify { repository.updateMetaAccountName(7, "Renamed") }
        coVerify { repository.updateMetaAccountBackedUp(7) }
        coVerify { repository.updateFavoriteChain(7, "dot", false) }
        coVerify { walletInteractor.saveChainSelectFilter(7, "assets") }
    }

    @Test
    fun selectedFavoritesAndRecoveryStatusFollowRepositoryValues() = runTest {
        every { repository.selectedMetaAccountFlow() } returns flowOf(wallet)
        every { repository.observeFavoriteChains(7) } returns flowOf(mapOf("dot" to true))
        coEvery { repository.isWalletRecoveryRequired(7) } returns true
        every { repository.walletRecoveryRequiredFlow(7) } returns flowOf(true)
        assertEquals(mapOf("dot" to true), interactor.observeSelectedMetaAccountFavoriteChains().first())
        assertTrue(interactor.isWalletRecoveryRequired(7))
        assertTrue(interactor.walletRecoveryRequiredFlow(7).first())
    }

    @Test
    fun configurationAndJsonMetadataReturnRepositoryValues() = runTest {
        val language = Language("ja")
        every { repository.getEncryptionTypes() } returns listOf(CryptoType.ECDSA)
        coEvery { repository.getPreferredCryptoType() } returns CryptoType.ECDSA
        every { repository.getLanguages() } returns listOf(language)
        coEvery { repository.selectedLanguage() } returns language
        coEvery { repository.generateMnemonic(Mnemonic.Length.TWELVE) } returns listOf("fixture")
        val json = ImportJsonData("Wallet", CryptoType.ED25519)
        coEvery { repository.processAccountJson("json") } returns json
        assertEquals(listOf(CryptoType.ECDSA), interactor.getCryptoTypes())
        assertEquals(CryptoType.ECDSA, interactor.getPreferredCryptoType())
        assertEquals(listOf(language), interactor.getLanguages())
        assertEquals(language, interactor.getSelectedLanguage())
        assertEquals(listOf("fixture"), interactor.generateMnemonic(Mnemonic.Length.TWELVE))
        assertSame(json, interactor.processAccountJson("json").getOrThrow())
        interactor.changeSelectedLanguage(language)
        interactor.validateJsonBackup("json", "password")
        coVerify { repository.changeLanguage(language) }
        coVerify { repository.validateJsonBackup("json", "password") }
        val failure = IllegalArgumentException("invalid")
        coEvery { repository.processAccountJson("bad") } throws failure
        assertSame(failure, interactor.processAccountJson("bad").exceptionOrNull())
    }

    @Test
    fun biometricAndPinReadinessReflectRepositoryState() = runTest {
        coEvery { repository.isCodeSet() } returns true
        coEvery { repository.getPinHealth() } returns PinHealth.ABSENT
        coEvery { repository.isBiometricEnabled() } returns true
        assertTrue(interactor.isCodeSet())
        assertEquals(PinHealth.ABSENT, interactor.getPinHealth())
        assertTrue(interactor.isBiometricEnabled())
        interactor.setBiometricOn()
        interactor.setBiometricOff()
        coVerify { repository.setBiometricOn() }
        coVerify { repository.setBiometricOff() }
    }

    @Test
    fun backupQueriesAndAuthorizationPreserveServiceResults() = runTest {
        val copies = listOf(BackupAccountMeta("Wallet", "address", "id"))
        coEvery { backup.getBackupAccounts() } returns copies
        coEvery { backup.getWebBackupAccounts() } returns copies
        coEvery { backup.hasSession() } returns true
        val launcher = mockk<ActivityResultLauncher<Intent>>()
        coEvery { backup.authorize(launcher) } returns true
        assertEquals(copies, interactor.getGoogleBackupAccounts())
        assertEquals(copies, interactor.getExtensionGoogleBackups())
        assertTrue(interactor.hasGoogleBackupSession())
        assertTrue(interactor.authorizeGoogleBackup(launcher))
        coVerifyOrder { backup.logout(); backup.authorize(launcher) }
        coEvery { repository.googleBackupAddressForWallet(7) } returns "address"
        coEvery { repository.isGoogleBackupSupported(7) } returns true
        coEvery { repository.getBestBackupType(7, WalletEcosystem.Substrate) } returns BackupAccountType.SEED
        every { repository.googleAddressAllWalletsFlow() } returns flowOf(listOf("address"))
        every { repository.polkadotAddressForSelectedAccountFlow() } returns flowOf("dot-address")
        assertEquals("address", interactor.googleBackupAddressForWallet(7))
        assertTrue(interactor.isGoogleBackupSupported(7))
        assertEquals(BackupAccountType.SEED, interactor.getBestBackupType(7, WalletEcosystem.Substrate))
        assertEquals(listOf("address"), interactor.getMetaAccountsGoogleAddresses().first())
        assertEquals("dot-address", interactor.polkadotAddressForSelectedAccountFlow().first())
    }

    @Test
    fun backupUploadContainsTheWalletsOwnAddressPhraseSeedsAndJson() = runTest {
        val entropy = ByteArray(16)
        val seed = ByteArray(32) { 3 }
        val ethSeed = ByteArray(32) { 5 }
        val sub = SubstrateSecrets(SubstrateKeypairFactory.generate(EncryptionType.ED25519, seed, emptyList()),
            entropy = entropy, seed = seed, substrateDerivationPath = "//2")
        val eth = EthereumSecrets(ethereumKeypair = EthereumKeypairFactory.createWithPrivateKey(ethSeed),
            ethereumDerivationPath = "m/44'/60'/0'/0/0")
        val chain = VmTestFixtures.chain(westendChainId).copy(addressPrefix = 42)
        coEvery { repository.getMetaAccount(7) } returns wallet
        coEvery { repository.getChain(westendChainId) } returns chain
        coEvery { repository.getSubstrateSecrets(7) } returns sub
        coEvery { repository.getEthereumSecrets(7) } returns eth
        coEvery { repository.generateRestoreJson(7, polkadotChainId, "pw") } returns "sub-json"
        coEvery { repository.generateRestoreJson(7, moonriverChainId, "pw") } returns "eth-json"
        coEvery { repository.getSupportedBackupTypes(7) } returns setOf(BackupAccountType.PASSPHRASE, BackupAccountType.JSON)
        val uploaded = slot<DecryptedBackupAccount>()
        coEvery { backup.saveBackupAccount(capture(uploaded), "pw") } returns Unit

        assertTrue(interactor.saveGoogleBackupAccount(7, "pw").isEmpty())
        assertEquals(wallet.name, uploaded.captured.name)
        assertEquals(requireNotNull(wallet.substrateAccountId).toAddress(42), uploaded.captured.address)
        assertEquals(MnemonicCreator.fromEntropy(entropy).words, uploaded.captured.mnemonicPhrase)
        assertEquals(seed.toHexString(true), uploaded.captured.seed?.substrateSeed)
        assertEquals(ethSeed.toHexString(true), uploaded.captured.seed?.ethSeed)
        assertEquals("sub-json", uploaded.captured.json?.substrateJson)
        assertEquals("eth-json", uploaded.captured.json?.ethJson)
        assertEquals("//2", uploaded.captured.substrateDerivationPath)
        assertSame(sub, interactor.getSubstrateSecrets(7))
    }
}
