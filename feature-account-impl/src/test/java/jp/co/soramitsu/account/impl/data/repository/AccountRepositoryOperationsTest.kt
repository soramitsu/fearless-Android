package jp.co.soramitsu.account.impl.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.api.domain.model.Account
import jp.co.soramitsu.account.api.domain.model.AuthType
import jp.co.soramitsu.account.api.domain.model.MetaAccountOrdering
import jp.co.soramitsu.account.impl.data.repository.datasource.AccountDataSource
import jp.co.soramitsu.account.impl.domain.UniversalWalletFreshRoots
import jp.co.soramitsu.account.impl.domain.UniversalWalletWatchList
import jp.co.soramitsu.account.impl.presentation.VmTestFixtures
import jp.co.soramitsu.common.data.storage.encrypt.PinHealth
import jp.co.soramitsu.common.data.storage.envelope.PinVerification
import jp.co.soramitsu.common.resources.LanguagesHolder
import jp.co.soramitsu.core.model.Language
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.coredb.dao.MetaAccountDao
import jp.co.soramitsu.coredb.dao.NomisScoresDao
import jp.co.soramitsu.coredb.model.NomisWalletScoreLocal
import jp.co.soramitsu.coredb.model.chain.FavoriteChainLocal
import jp.co.soramitsu.fearless_utils.encrypt.MultiChainEncryption
import jp.co.soramitsu.fearless_utils.encrypt.json.JsonSeedDecoder
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.Mnemonic
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.MnemonicCreator
import jp.co.soramitsu.fearless_utils.encrypt.model.ImportAccountMeta
import jp.co.soramitsu.fearless_utils.encrypt.model.NetworkTypeIdentifier
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountRepositoryOperationsTest {
    private val source = mockk<AccountDataSource>(relaxed = true)
    private val dao = mockk<MetaAccountDao>(relaxed = true)
    private val decoder = mockk<JsonSeedDecoder>()
    private val languages = mockk<LanguagesHolder>()
    private val chains = mockk<ChainsRepository>()
    private val scores = mockk<NomisScoresDao>()
    private val mutations = mockk<WalletSecretMutationCoordinator>(relaxed = true)
    private val roots = mockk<UniversalWalletFreshRoots>(relaxed = true)
    private val watchList = mockk<UniversalWalletWatchList>(relaxed = true)
    private val wallet = VmTestFixtures.meta()
    private val repo = AccountRepositoryImpl(
        source, dao, mockk(), decoder, mockk(), languages, chains, scores, mockk(), mockk(),
        mockk(), mockk(), mutations, mockk(), UnconfinedTestDispatcher(), roots, watchList
    )

    @Test
    fun walletQueriesReturnTheSelectedAndRequestedWallets() = runTest {
        coEvery { source.getSelectedMetaAccount() } returns wallet
        coEvery { source.getSelectedLightMetaAccount() } returns wallet
        coEvery { source.getLightMetaAccount(7) } returns wallet
        coEvery { source.getMetaAccount(7) } returns wallet
        coEvery { source.findMetaAccount(any()) } returns wallet
        coEvery { source.allMetaAccounts() } returns listOf(wallet)
        coEvery { source.anyAccountSelected() } returns true
        assertSame(wallet, repo.getSelectedMetaAccount())
        assertSame(wallet, repo.getSelectedLightMetaAccount())
        assertSame(wallet, repo.getLightMetaAccount(7))
        assertSame(wallet, repo.getMetaAccount(7))
        assertSame(wallet, repo.findMetaAccount(byteArrayOf(1)))
        assertEquals(listOf(wallet), repo.allMetaAccounts())
        assertTrue(repo.isAccountSelected())
    }

    @Test
    fun walletFlowsExposeUpstreamValues() = runTest {
        every { source.selectedLightMetaAccount() } returns flowOf(wallet)
        every { source.lightMetaAccountsFlow() } returns flowOf(listOf(wallet))
        every { source.lightMetaAccountFlow(7) } returns flowOf(wallet)
        every { source.observeAllMetaAccounts() } returns flowOf(listOf(wallet))
        assertSame(wallet, repo.selectedLightMetaAccountFlow().first())
        assertEquals(listOf(wallet), repo.lightMetaAccountsFlow().first())
        assertSame(wallet, repo.lightMetaAccountFlow(7).first())
        assertEquals(listOf(wallet), repo.allMetaAccountsFlow().first { it.isNotEmpty() })
    }

    @Test
    fun walletEditsKeepIdsNamesAndOrdering() = runTest {
        val ordering = listOf(MetaAccountOrdering(7, 1), MetaAccountOrdering(2, 0))
        repo.selectAccount(7)
        repo.selectMetaAccount(7)
        repo.updateMetaAccountName(7, "Renamed")
        repo.updateMetaAccountBackedUp(7)
        repo.updateWalletOnGoogleBackupDelete(7)
        repo.updateAccountsOrdering(ordering)
        coVerify(exactly = 1) { dao.selectMetaAccount(7) }
        coVerify(exactly = 1) { source.selectMetaAccount(7) }
        coVerify(exactly = 1) { source.updateMetaAccountName(7, "Renamed") }
        coVerify(exactly = 1) { source.updateMetaAccountBackedUp(7) }
        coVerify(exactly = 1) { source.updateWalletOnGoogleBackupDelete(7) }
        coVerify(exactly = 1) { source.updateAccountPositions(ordering) }
    }

    @Test
    fun pinAndBiometricStateAreReadAndWrittenWithoutConflatingThem() = runTest {
        for (health in PinHealth.entries) {
            coEvery { source.getPinHealth() } returns health
            assertEquals(health, repo.getPinHealth())
            assertEquals(health != PinHealth.ABSENT, repo.isCodeSet())
        }
        coEvery { source.verifyPinCode("123456") } returns PinVerification.CORRECT
        assertEquals(PinVerification.CORRECT, repo.verifyPinCode("123456"))
        repo.savePinCode("123456")
        coVerify { source.savePinCode("123456") }
        coEvery { source.getAuthType() } returns AuthType.BIOMETRY
        assertTrue(repo.isBiometricEnabled())
        coEvery { source.getAuthType() } returns AuthType.PINCODE
        assertFalse(repo.isBiometricEnabled())
        repo.setBiometricOn()
        repo.setBiometricOff()
        coVerify { source.saveAuthType(AuthType.BIOMETRY) }
        coVerify { source.saveAuthType(AuthType.PINCODE) }
    }

    @Test
    fun mnemonicGenerationProducesAValidPhraseOfTheRequestedLength() = runTest {
        for (length in listOf(Mnemonic.Length.TWELVE, Mnemonic.Length.TWENTY_FOUR)) {
            val words = repo.generateMnemonic(length)
            assertEquals(if (length == Mnemonic.Length.TWELVE) 12 else 24, words.size)
            assertEquals(words, MnemonicCreator.fromWords(words.joinToString(" ")).wordList)
        }
        assertEquals(CryptoType.entries, repo.getEncryptionTypes())
        coEvery { source.getPreferredCryptoTypeOrSelected() } returns CryptoType.ECDSA
        assertEquals(CryptoType.ECDSA, repo.getPreferredCryptoType())
    }

    @Test
    fun jsonMetadataReportsItsNameAndEncryption() = runTest {
        every { decoder.extractImportMetaData("json") } returns ImportAccountMeta(
            "Imported", NetworkTypeIdentifier.Undefined, MultiChainEncryption.Ethereum
        )
        val result = repo.processAccountJson("json")
        assertEquals("Imported", result.name)
        assertEquals(CryptoType.ECDSA, result.encryptionType)
        val failure = IllegalArgumentException("bad password")
        every { decoder.decode("json", "wrong") } throws failure
        assertSame(failure, runCatching { repo.validateJsonBackup("json", "wrong") }.exceptionOrNull())
    }

    @Test
    fun networkCheckRejectsMalformedOrDifferentPrefixAddresses() = runTest {
        val address = ByteArray(32) { 1 }.toAddress(0)
        val account = Account(address, "Wallet", "01", CryptoType.ED25519, 0)
        every { source.selectedAccountMapping } returns MutableStateFlow(mapOf("dot" to account))
        assertSame(account, repo.getSelectedAccount("dot"))
        assertTrue(repo.isInCurrentNetwork(ByteArray(32) { 2 }.toAddress(0), "dot"))
        assertFalse(repo.isInCurrentNetwork(ByteArray(32) { 2 }.toAddress(2), "dot"))
        assertFalse(repo.isInCurrentNetwork("invalid", "dot"))
        coEvery { source.accountExists(any()) } returns true
        assertTrue(repo.isAccountExists(byteArrayOf(1)))
    }

    @Test
    fun languageAndFavoritesPreserveSelections() = runTest {
        val language = Language("ja")
        every { languages.getLanguages() } returns listOf(language)
        coEvery { source.getSelectedLanguage() } returns language
        assertEquals(listOf(language), repo.getLanguages())
        assertEquals(language, repo.selectedLanguage())
        repo.changeLanguage(language)
        coVerify { source.changeSelectedLanguage(language) }
        repo.updateFavoriteChain(7, "dot", false)
        coVerify { dao.insertOrReplaceFavoriteChain(match { it.metaId == 7L && it.chainId == "dot" && !it.isFavorite }) }
        every { source.observeFavoriteChains(7) } returns flowOf(listOf(
            FavoriteChainLocal(7, "dot", true), FavoriteChainLocal(7, "eth", false)
        ))
        assertEquals(mapOf("dot" to true, "eth" to false), repo.observeFavoriteChains(7).first())
    }

    @Test
    fun scoreFlowsMapMissingLoadingAndErrorStates() = runTest {
        every { scores.observeScores() } returns flowOf(listOf(NomisWalletScoreLocal.loading(7), NomisWalletScoreLocal.error(8)))
        every { scores.observeScore(7) } returns flowOf(null)
        assertEquals(listOf(-1, -2), repo.observeNomisScores().first().map { it.score })
        assertNull(repo.observeNomisScore(7).first())
        every { scores.observeScore(7) } returns flowOf(NomisWalletScoreLocal.error(7))
        assertEquals(-2, repo.observeNomisScore(7).first()?.score)
    }

    @Test
    fun deletionForgetsPublicAuxiliaryStateEvenWhenTheWalletCannotBeRead() = runTest {
        coEvery { source.getMetaAccount(7) } throws IllegalStateException("unreadable row")
        repo.deleteAccount(7)
        coVerify(exactly = 1) { mutations.delete(7) }
        coVerify(exactly = 1) { roots.forget(7, null) }
        coVerify(exactly = 1) { watchList.forget(7) }
    }

    @Test
    fun cancellationBeforeDeletionDoesNotMutateWalletState() = runTest {
        val cancelled = CancellationException("cancelled")
        coEvery { source.getMetaAccount(7) } throws cancelled
        assertSame(cancelled, runCatching { repo.deleteAccount(7) }.exceptionOrNull())
        coVerify(exactly = 0) { mutations.delete(any()) }
        verify(exactly = 0) { roots.forget(any(), any()) }
    }
}
