package jp.co.soramitsu.account.impl.data.repository

import jp.co.soramitsu.account.api.domain.interfaces.AccountAlreadyExistsException
import jp.co.soramitsu.account.api.domain.model.AddAccountPayload
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.impl.data.repository.datasource.AccountDataSource
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v2.SecretStoreV2
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecretStore
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecrets
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecretStore
import jp.co.soramitsu.common.data.secrets.v3.TonSecretStore
import jp.co.soramitsu.common.data.storage.encrypt.WalletRecoveryRequiredException
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretAccessGuard
import jp.co.soramitsu.common.resources.LanguagesHolder
import jp.co.soramitsu.common.utils.DEFAULT_DERIVATION_PATH
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.coredb.dao.MetaAccountDao
import jp.co.soramitsu.coredb.dao.NomisScoresDao
import jp.co.soramitsu.coredb.model.MetaAccountLocal
import jp.co.soramitsu.fearless_utils.encrypt.json.JsonSeedDecoder
import jp.co.soramitsu.fearless_utils.encrypt.json.JsonSeedEncoder
import jp.co.soramitsu.fearless_utils.encrypt.junction.BIP32JunctionDecoder
import jp.co.soramitsu.fearless_utils.scale.toHexString
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.testshared.HashMapEncryptedPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class AccountRepositoryRecoveryFlowTest {

    @Test
    fun `delete routes exclusively through durable mutation coordinator`() = runTest {
        val accountDataSource = mock<AccountDataSource>()
        val metaAccountDao = mock<MetaAccountDao>()
        val delegate = mock<AccountRepositoryDelegate>()
        val coordinator = mock<WalletSecretMutationCoordinator>()
        val repository = accountRepository(
            accountDataSource = accountDataSource,
            metaAccountDao = metaAccountDao,
            accountRepositoryDelegate = delegate,
            coordinator = coordinator
        )

        repository.deleteAccount(META_ID)

        verify(coordinator).delete(META_ID)
        verifyNoInteractions(accountDataSource, metaAccountDao, delegate)
    }

    @Test
    fun `create delegates once without a second selection mutation`() = runTest {
        val accountDataSource = mock<AccountDataSource>()
        val metaAccountDao = mock<MetaAccountDao>()
        val coordinator = mock<WalletSecretMutationCoordinator>()
        val delegate = mock<AccountRepositoryDelegate>()
        val payload = AddAccountPayload.Ton(
            accountName = "New TON wallet",
            mnemonic = VALID_MNEMONIC,
            isBackedUp = false
        )
        whenever(delegate.create(payload)).thenReturn(NEW_META_ID)
        val repository = accountRepository(
            accountDataSource = accountDataSource,
            metaAccountDao = metaAccountDao,
            accountRepositoryDelegate = delegate,
            coordinator = coordinator
        )

        assertEquals(NEW_META_ID, repository.createAccount(payload))

        verify(delegate).create(payload)
        verify(metaAccountDao, never()).selectMetaAccount(any())
        verify(accountDataSource, never()).selectMetaAccount(any())
        verifyNoInteractions(coordinator)
    }

    @Test
    fun `additional EVM preserves exact wallet fields and routes canonical secret`() = runTest {
        val metaAccountDao = mock<MetaAccountDao>()
        val coordinator = mock<WalletSecretMutationCoordinator>()
        val existing = additionalEvmWallet()
        whenever(metaAccountDao.getMetaAccount(META_ID)).thenReturn(existing)
        whenever(coordinator.addEvm(any(), any(), any())).thenReturn(META_ID)
        val repository = SubstrateOrEvmAccountRepository(
            metaAccountDao = metaAccountDao,
            walletSecretMutationCoordinator = coordinator
        )
        val payload = additionalEvmPayload()

        assertEquals(META_ID, repository.createAdditional(payload))

        val existingCaptor = argumentCaptor<MetaAccountLocal>()
        val afterCaptor = argumentCaptor<MetaAccountLocal>()
        val secretCaptor = argumentCaptor<String>()
        verify(coordinator).addEvm(
            existingCaptor.capture(),
            afterCaptor.capture(),
            secretCaptor.capture()
        )
        verify(metaAccountDao).getMetaAccount(META_ID)
        verifyNoMoreInteractions(metaAccountDao)

        assertSame(existing, existingCaptor.firstValue)
        val after = afterCaptor.firstValue
        assertEquals(existing.id, after.id)
        assertArrayEquals(existing.substratePublicKey, after.substratePublicKey)
        assertArrayEquals(existing.substrateAccountId, after.substrateAccountId)
        assertEquals(existing.substrateCryptoType, after.substrateCryptoType)
        assertArrayEquals(existing.tonPublicKey, after.tonPublicKey)
        assertEquals(existing.name, after.name)
        assertEquals(existing.isSelected, after.isSelected)
        assertEquals(existing.position, after.position)
        assertEquals(existing.googleBackupAddress, after.googleBackupAddress)
        assertEquals(existing.initialized, after.initialized)
        assertEquals(payload.isBackedUp, after.isBackedUp)
        assertTrue(after.ethereumPublicKey?.isNotEmpty() == true)
        assertTrue(after.ethereumAddress?.isNotEmpty() == true)

        val encodedSecret = secretCaptor.firstValue
        assertTrue(CANONICAL_SCALE_HEX.matches(encodedSecret))
        val decodedSecret = EthereumSecrets.read(encodedSecret)
        assertEquals(encodedSecret, decodedSecret.toHexString())
        val keypair = decodedSecret[EthereumSecrets.EthereumKeypair]
        assertArrayEquals(
            requireNotNull(after.ethereumPublicKey),
            keypair[KeyPairSchema.PublicKey]
        )
        assertArrayEquals(
            keypair[KeyPairSchema.PrivateKey],
            decodedSecret[EthereumSecrets.Seed]
        )
        assertEquals(
            payload.ethereumDerivationPath,
            decodedSecret[EthereumSecrets.EthereumDerivationPath]
        )
    }

    @Test
    fun `additional EVM maps only identity conflict to account already exists`() = runTest {
        val metaAccountDao = mock<MetaAccountDao>()
        val coordinator = mock<WalletSecretMutationCoordinator>()
        whenever(metaAccountDao.getMetaAccount(META_ID)).thenReturn(additionalEvmWallet())
        whenever(coordinator.addEvm(any(), any(), any())).thenThrow(
            WalletSecretMutationCoordinatorException(
                reason = WalletMutationFailureReason.IDENTITY_CONFLICT,
                message = "duplicate Ethereum address"
            )
        )
        val repository = SubstrateOrEvmAccountRepository(
            metaAccountDao = metaAccountDao,
            walletSecretMutationCoordinator = coordinator
        )

        expectFailure<AccountAlreadyExistsException> {
            repository.createAdditional(additionalEvmPayload())
        }
    }

    @Test
    fun `additional EVM preserves recovery state conflicts`() = runTest {
        val metaAccountDao = mock<MetaAccountDao>()
        val coordinator = mock<WalletSecretMutationCoordinator>()
        val stateConflict = WalletSecretMutationCoordinatorException(
            reason = WalletMutationFailureReason.STATE_CONFLICT,
            message = "wallet changed"
        )
        whenever(metaAccountDao.getMetaAccount(META_ID)).thenReturn(additionalEvmWallet())
        whenever(coordinator.addEvm(any(), any(), any())).thenThrow(stateConflict)
        val repository = SubstrateOrEvmAccountRepository(
            metaAccountDao = metaAccountDao,
            walletSecretMutationCoordinator = coordinator
        )

        val actual = expectFailure<WalletSecretMutationCoordinatorException> {
            repository.createAdditional(additionalEvmPayload())
        }

        assertSame(stateConflict, actual)
    }

    @Test
    fun `runtime quarantine updates recovery flow without selected account database emission`() =
        runTest {
            val accountDataSource = mock<AccountDataSource>()
            whenever(accountDataSource.getMetaAccount(META_ID)).thenReturn(META_ACCOUNT)

            val encryptedPreferences = HashMapEncryptedPreferences()
            val repository = AccountRepositoryImpl(
                accountDataSource = accountDataSource,
                metaAccountDao = mock<MetaAccountDao>(),
                legacyStoreV2 = mock<SecretStoreV2>(),
                jsonSeedDecoder = mock<JsonSeedDecoder>(),
                jsonSeedEncoder = mock<JsonSeedEncoder>(),
                languagesHolder = mock<LanguagesHolder>(),
                chainsRepository = mock<ChainsRepository>(),
                nomisScoresDao = mock<NomisScoresDao>(),
                substrateSecretStore = mock<SubstrateSecretStore>(),
                ethereumSecretStore = mock<EthereumSecretStore>(),
                tonSecretStore = mock<TonSecretStore>(),
                accountRepositoryDelegate = mock<AccountRepositoryDelegate>(),
                walletSecretMutationCoordinator =
                    mock<WalletSecretMutationCoordinator>(),
                walletSecretAccessGuard = WalletSecretAccessGuard(encryptedPreferences),
                dispatcher = UnconfinedTestDispatcher(testScheduler)
            )
            val recoveryStates = mutableListOf<Boolean>()
            val collection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                repository.walletRecoveryRequiredFlow(META_ID)
                    .take(2)
                    .toList(recoveryStates)
            }
            runCurrent()
            assertEquals(listOf(false), recoveryStates)

            quarantineMalformedSecret(encryptedPreferences, OTHER_META_ID)
            runCurrent()
            assertEquals(listOf(false), recoveryStates)

            quarantineMalformedSecret(encryptedPreferences, META_ID)
            runCurrent()

            assertEquals(listOf(false, true), recoveryStates)
            verify(accountDataSource, never()).selectedMetaAccountFlow()
            collection.join()
        }

    private fun accountRepository(
        accountDataSource: AccountDataSource,
        metaAccountDao: MetaAccountDao,
        accountRepositoryDelegate: AccountRepositoryDelegate,
        coordinator: WalletSecretMutationCoordinator
    ): AccountRepositoryImpl {
        return AccountRepositoryImpl(
            accountDataSource = accountDataSource,
            metaAccountDao = metaAccountDao,
            legacyStoreV2 = mock<SecretStoreV2>(),
            jsonSeedDecoder = mock<JsonSeedDecoder>(),
            jsonSeedEncoder = mock<JsonSeedEncoder>(),
            languagesHolder = mock<LanguagesHolder>(),
            chainsRepository = mock<ChainsRepository>(),
            nomisScoresDao = mock<NomisScoresDao>(),
            substrateSecretStore = mock<SubstrateSecretStore>(),
            ethereumSecretStore = mock<EthereumSecretStore>(),
            tonSecretStore = mock<TonSecretStore>(),
            accountRepositoryDelegate = accountRepositoryDelegate,
            walletSecretMutationCoordinator = coordinator,
            walletSecretAccessGuard = WalletSecretAccessGuard(
                HashMapEncryptedPreferences()
            ),
            dispatcher = UnconfinedTestDispatcher()
        )
    }

    private fun additionalEvmPayload(): AddAccountPayload.AdditionalEvm {
        return AddAccountPayload.AdditionalEvm(
            walletId = META_ID,
            accountName = "Attacker-controlled replacement name",
            mnemonic = VALID_MNEMONIC,
            ethereumDerivationPath = BIP32JunctionDecoder.DEFAULT_DERIVATION_PATH,
            isBackedUp = true
        )
    }

    private fun additionalEvmWallet(): MetaAccountLocal {
        return MetaAccountLocal(
            substratePublicKey = ByteArray(32) { (it + 1).toByte() },
            substrateCryptoType = CryptoType.SR25519,
            substrateAccountId = ByteArray(32) { (it + 33).toByte() },
            ethereumPublicKey = null,
            ethereumAddress = null,
            tonPublicKey = ByteArray(32) { (it + 65).toByte() },
            name = "Preserve me",
            isSelected = false,
            position = 23,
            isBackedUp = false,
            googleBackupAddress = "backup@example.test",
            initialized = true
        ).apply {
            id = META_ID
        }
    }

    private suspend inline fun <reified T : Throwable> expectFailure(
        crossinline action: suspend () -> Unit
    ): T {
        try {
            action()
            throw AssertionError("Expected ${T::class.java.simpleName}")
        } catch (failure: Throwable) {
            if (failure !is T) throw failure
            return failure
        }
    }

    private fun quarantineMalformedSecret(
        encryptedPreferences: HashMapEncryptedPreferences,
        metaId: Long
    ) {
        encryptedPreferences.putEncryptedString(
            "$metaId:SUBSTRATE_SECRETS",
            MALFORMED_SECRET
        )

        assertThrows(WalletRecoveryRequiredException::class.java) {
            SubstrateSecretStore(encryptedPreferences).get(metaId)
        }
    }

    private companion object {

        @JvmStatic
        @BeforeClass
        fun enableByteBuddyForJava21TestRuntime() {
            System.setProperty("net.bytebuddy.experimental", "true")
        }

        const val META_ID = 42L
        const val NEW_META_ID = 43L
        const val OTHER_META_ID = 84L
        const val MALFORMED_SECRET = "not-scale-encoded-secret"
        const val VALID_MNEMONIC =
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"

        val CANONICAL_SCALE_HEX = Regex("^0x(?:[0-9a-f]{2})+$")
        val META_ACCOUNT = MetaAccount(
            id = META_ID,
            chainAccounts = emptyMap(),
            favoriteChains = emptyMap(),
            substratePublicKey = ByteArray(32) { (it + 1).toByte() },
            substrateCryptoType = CryptoType.SR25519,
            substrateAccountId = ByteArray(32) { (it + 33).toByte() },
            ethereumAddress = null,
            ethereumPublicKey = null,
            tonPublicKey = null,
            isSelected = true,
            isBackedUp = true,
            googleBackupAddress = null,
            name = "Recovery flow wallet",
            initialized = true
        )
    }
}
