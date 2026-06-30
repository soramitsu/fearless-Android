package jp.co.soramitsu.account.impl.domain

import java.io.File
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.AndroidUniversalWalletMigrationSnapshotBuilder
import jp.co.soramitsu.account.api.domain.model.LightMetaAccount
import jp.co.soramitsu.backup.BackupService
import jp.co.soramitsu.common.data.storage.InitialValueProducer
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.interfaces.FileProvider
import jp.co.soramitsu.common.model.UniversalWalletEcosystem
import jp.co.soramitsu.common.model.UniversalWalletMigrationRequiredAction
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.core.model.Language
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AccountInteractorImplTest {

    private lateinit var accountRepository: AccountRepository
    private lateinit var fileProvider: FileProvider
    private lateinit var backupService: BackupService
    private lateinit var walletInteractor: WalletInteractor
    private lateinit var preferences: Preferences

    private var pinCode: String? = null

    @Before
    fun setup() {
        preferences = InMemoryPreferences()

        accountRepository = interfaceProxy { method, args ->
            when (method.name) {
                "getPinCode" -> pinCode
                "savePinCode" -> {
                    pinCode = args?.firstOrNull() as? String
                    Unit
                }

                else -> unexpectedCall(method)
            }
        }

        fileProvider = object : FileProvider {
            override suspend fun getFileInExternalCacheStorage(fileName: String): File = File(fileName)

            override suspend fun getFileInInternalCacheStorage(fileName: String): File = File(fileName)
        }

        backupService = allocateWithoutConstructor(BackupService::class.java)
        walletInteractor = interfaceProxy { method, _ -> unexpectedCall(method) }
    }

    @Test
    fun `isPinCorrect should lock after max failed attempts and unlock after timeout`() {
        runBlocking {
            var now = 1_000L
            val interactor = AccountInteractorImpl(
                accountRepository = accountRepository,
                fileProvider = fileProvider,
                preferences = preferences,
                backupService = backupService,
                walletInteractor = walletInteractor,
                nowProvider = { now }
            )

            accountRepository.savePinCode("1234")

            repeat(5) {
                assertFalse(interactor.isPinCorrect("0000"))
            }

            assertFalse(interactor.isPinCorrect("1234"))

            now += 31_000

            assertTrue(interactor.isPinCorrect("1234"))
        }
    }

    @Test
    fun `savePin should reset lockout state`() {
        runBlocking {
            var now = 5_000L
            val interactor = AccountInteractorImpl(
                accountRepository = accountRepository,
                fileProvider = fileProvider,
                preferences = preferences,
                backupService = backupService,
                walletInteractor = walletInteractor,
                nowProvider = { now }
            )

            accountRepository.savePinCode("0000")

            repeat(5) {
                interactor.isPinCorrect("1111")
            }
            assertFalse(interactor.isPinCorrect("0000"))

            interactor.savePin("0000")

            assertTrue(interactor.isPinCorrect("0000"))
        }
    }

    @Test
    fun `universalWalletMigrationSnapshotFlow maps public light accounts without secret access`() {
        runBlocking {
            val interactor = interactorWithLightAccounts(
                listOf(
                    lightAccount(
                        substrateAccountId = ByteArray(32) { 1 }
                    )
                )
            )

            val snapshot = interactor.universalWalletMigrationSnapshotFlow().first()

            assertEquals(UniversalWalletMigrationRequiredAction.MigrateBeforeAccess, snapshot.requiredAction())
            assertEquals(listOf(UniversalWalletEcosystem.Substrate.id), snapshot.legacyVaults.map { it.ecosystem })
            assertTrue(snapshot.validationErrors().isEmpty())
        }
    }

    @Test
    fun `universalWalletMigrationSnapshot returns create state for empty install`() {
        runBlocking {
            val interactor = interactorWithLightAccounts(emptyList())

            val snapshot = interactor.universalWalletMigrationSnapshot()

            assertEquals(UniversalWalletMigrationRequiredAction.CreateUniversalWallet, snapshot.requiredAction())
            assertTrue(snapshot.legacyVaults.isEmpty())
            assertTrue(snapshot.validationErrors().isEmpty())
        }
    }

    private fun interactorWithLightAccounts(accounts: List<LightMetaAccount>): AccountInteractorImpl {
        accountRepository = interfaceProxy { method, _ ->
            when (method.name) {
                "lightMetaAccountsFlow" -> flowOf(accounts)
                else -> unexpectedCall(method)
            }
        }

        return AccountInteractorImpl(
            accountRepository = accountRepository,
            fileProvider = fileProvider,
            preferences = preferences,
            backupService = backupService,
            walletInteractor = walletInteractor,
            migrationSnapshotBuilder = AndroidUniversalWalletMigrationSnapshotBuilder(
                cutoffAtMillis = CUTOFF_AT,
                clockMillis = { EVALUATED_AT }
            )
        )
    }

    private fun lightAccount(
        substrateAccountId: ByteArray? = null,
        ethereumAddress: ByteArray? = null,
        tonPublicKey: ByteArray? = null,
        name: String = "Wallet"
    ): LightMetaAccount {
        return LightMetaAccount(
            id = 1,
            substratePublicKey = substrateAccountId,
            substrateCryptoType = substrateAccountId?.let { CryptoType.ED25519 },
            substrateAccountId = substrateAccountId,
            ethereumAddress = ethereumAddress,
            ethereumPublicKey = ethereumAddress,
            tonPublicKey = tonPublicKey,
            universalWalletChainAccounts = emptyMap(),
            isSelected = true,
            name = name,
            isBackedUp = true,
            initialized = true
        )
    }

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T> interfaceProxy(
        crossinline handler: (Method, Array<out Any?>?) -> Any?
    ): T {
        return Proxy.newProxyInstance(
            T::class.java.classLoader,
            arrayOf(T::class.java)
        ) { _, method, args ->
            when (method.name) {
                "toString" -> "${T::class.java.simpleName}Proxy"
                "hashCode" -> System.identityHashCode(this)
                "equals" -> false
                else -> handler(method, args)
            }
        } as T
    }

    private fun unexpectedCall(method: Method): Nothing {
        throw UnsupportedOperationException("Unexpected method call in test: ${method.name}")
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> allocateWithoutConstructor(clazz: Class<T>): T {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val unsafeField = unsafeClass.getDeclaredField("theUnsafe")
        unsafeField.isAccessible = true
        val unsafe = unsafeField.get(null)
        val allocateInstance = unsafeClass.getMethod("allocateInstance", Class::class.java)
        return allocateInstance.invoke(unsafe, clazz) as T
    }

    private companion object {
        const val CUTOFF_AT = 1_710_000_000_000L
        const val EVALUATED_AT = 1_710_000_000_100L
    }

    private class InMemoryPreferences : Preferences {
        private val data = mutableMapOf<String, Any?>()

        override fun contains(field: String): Boolean = data.containsKey(field)

        override fun putString(field: String, value: String?) {
            data[field] = value
        }

        override fun getString(field: String, defaultValue: String): String {
            return data[field] as? String ?: defaultValue
        }

        override fun getString(field: String): String? {
            return data[field] as? String
        }

        override fun putStringSet(field: String, value: Set<String>?) {
            data[field] = value
        }

        override fun getStringSet(field: String, defaultValue: Set<String>): Set<String> {
            @Suppress("UNCHECKED_CAST")
            return data[field] as? Set<String> ?: defaultValue
        }

        override fun putBoolean(field: String, value: Boolean) {
            data[field] = value
        }

        override fun getBoolean(field: String, defaultValue: Boolean): Boolean {
            return data[field] as? Boolean ?: defaultValue
        }

        override fun putInt(field: String, value: Int) {
            data[field] = value
        }

        override fun getInt(field: String, defaultValue: Int): Int {
            return data[field] as? Int ?: defaultValue
        }

        override fun putLong(field: String, value: Long) {
            data[field] = value
        }

        override fun getLong(field: String, defaultValue: Long): Long {
            return data[field] as? Long ?: defaultValue
        }

        override fun getCurrentLanguage(): Language? = null

        override fun saveCurrentLanguage(languageIsoCode: String) {
            data["selected_language"] = languageIsoCode
        }

        override fun removeField(field: String) {
            data.remove(field)
        }

        override fun stringFlow(
            field: String,
            initialValueProducer: InitialValueProducer<String>?
        ): Flow<String?> = flowOf(getString(field))

        override fun stringSetFlow(
            field: String,
            initialValueProducer: InitialValueProducer<Set<String>>?
        ): Flow<Set<String>> = flowOf(getStringSet(field, emptySet()))

        override fun intFlow(field: String, initialValue: Int): Flow<Int> = flowOf(getInt(field, initialValue))

        override fun booleanFlow(field: String, initialValue: Boolean): Flow<Boolean> = flowOf(getBoolean(field, initialValue))
    }
}
