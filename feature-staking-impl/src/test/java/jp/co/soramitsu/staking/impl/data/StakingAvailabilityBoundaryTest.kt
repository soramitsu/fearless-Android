package jp.co.soramitsu.staking.impl.data

import jp.co.soramitsu.wallet.impl.domain.model.AssetAmountState
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.staking.api.data.StakingAssetSelection
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.api.data.StakingType
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.whenever
import jp.co.soramitsu.core.models.Asset as CoreAsset

class StakingAvailabilityBoundaryTest {
    private fun chain(
        id: String = "a",
        assetId: String = "0",
        ecosystem: Ecosystem = Ecosystem.Substrate,
        staking: CoreAsset.StakingType = CoreAsset.StakingType.RELAYCHAIN,
        pool: Boolean = false,
        precision: Int = 12
    ): Chain {
        val asset = mock(CoreAsset::class.java)
        whenever(asset.id).thenReturn(assetId)
        whenever(asset.chainId).thenReturn(id)
        whenever(asset.staking).thenReturn(staking)
        whenever(asset.supportStakingPool).thenReturn(pool)
        whenever(asset.precision).thenReturn(precision)
        val result = mock(Chain::class.java)
        whenever(result.id).thenReturn(id)
        whenever(result.name).thenReturn("Catalog $id/$precision")
        whenever(result.ecosystem).thenReturn(ecosystem)
        whenever(result.assets).thenReturn(listOf(asset))
        whenever(result.assetsById).thenReturn(mapOf(assetId to asset))
        return result
    }

    private fun account(id: Long = 42, key: ByteArray? = ByteArray(32) { 7 }, ton: ByteArray? = null) = MetaAccount(
        id = id,
        chainAccounts = emptyMap(),
        favoriteChains = emptyMap(),
        substratePublicKey = key,
        substrateCryptoType = null,
        substrateAccountId = key,
        ethereumAddress = null,
        ethereumPublicKey = null,
        tonPublicKey = ton,
        isSelected = true,
        isBackedUp = true,
        googleBackupAddress = null,
        name = "Public test wallet",
        initialized = true
    )

    private fun stored(chain: Chain, account: MetaAccount): Asset = Asset.createEmpty(
        chain.assets.single(), account.id, checkNotNull(account.accountId(chain)), minSupportedVersion = null
    )

    private inner class Fixture(
        initialCatalog: List<Chain> = listOf(chain()),
        val initialAccount: MetaAccount = account(),
        encoded: String? = "a:0:RELAYCHAIN",
        emitInitialAccount: Boolean = true
    ) : AutoCloseable {
        val catalog = MutableStateFlow(initialCatalog)
        val accounts = MutableSharedFlow<MetaAccount>(replay = 1)
        val preference = MutableStateFlow(encoded)
        val writes = mutableListOf<Pair<String, String?>>()
        val failures = mutableListOf<Throwable>()
        val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob() + CoroutineExceptionHandler { _, error -> failures += error })
        var rows = initialCatalog.filter { initialAccount.accountId(it) != null }.map { stored(it, initialAccount) }
        var read: suspend (Long) -> List<Asset> = { rows }
        val state: StakingSharedState

        init {
            if (emitInitialAccount) check(accounts.tryEmit(initialAccount))
            val preferences = mock(Preferences::class.java)
            whenever(preferences.stringFlow(any(), anyOrNull())).thenReturn(preference)
            doAnswer {
                val field = it.getArgument<String>(0)
                val value = it.getArgument<String?>(1)
                writes += field to value
                preference.value = value
                null
            }.whenever(preferences).putString(any(), anyOrNull())
            val registry = mock(ChainRegistry::class.java)
            val chains = mock(ChainsRepository::class.java)
            whenever(chains.chainsFlow()).thenReturn(catalog)
            val accountsRepository = mock(AccountRepository::class.java)
            whenever(accountsRepository.selectedMetaAccountFlow()).thenReturn(accounts)
            val wallets = object : WalletRepository by mock(WalletRepository::class.java) {
                override suspend fun getAssets(metaId: Long): List<Asset> = read(metaId)
            }
            state = StakingSharedState(
                registry, preferences, wallets, accountsRepository, chains, scope
            )
        }

        suspend fun emitAccount(value: MetaAccount) = accounts.emit(value)

        override fun close() {
            runBlocking { scope.coroutineContext.job.cancelAndJoin() }
            assertTrue("No swallowed fixture errors: $failures", failures.isEmpty())
        }
    }

    @Test
    fun noActionIsAuthorizedBeforeTheFirstAccountAndCatalogObservation() = runBlocking {
        Fixture(emitInitialAccount = false).use {
            val pending = it.state.selectionRevision.capture()
            assertFalse(it.state.selectionRevision.matchesAction(pending, StakingType.RELAYCHAIN, "a", "0"))
            assertFalse(it.state.selectionRevision.matches(pending, "a", "0"))
            it.emitAccount(it.initialAccount)
            assertEquals("a", it.state.chainId())
            assertFalse(it.state.selectionRevision.matchesAction(pending, StakingType.RELAYCHAIN, "a", "0"))
            assertTrue(it.state.selectionRevision.matchesAction(it.state.selectionRevision.capture(), StakingType.RELAYCHAIN, "a", "0"))
        }
    }

    @Test
    fun absentChoiceUsesAnEphemeralDefaultWithoutWritingPreferences() = runBlocking {
        Fixture(encoded = null).use {
            assertEquals(StakingAssetSelection.RelayChainStaking("a", "0"), it.state.selectionItem.first())
            assertNull(it.preference.value)
            assertTrue(it.writes.isEmpty())
        }
    }

    @Test
    fun initialEmptyCatalogCompletesOptionsAndExposesUnavailableState() = runBlocking {
        Fixture(initialCatalog = emptyList(), encoded = null).use {
            assertEquals(emptyList<StakingAssetSelection>(), it.state.availableToSelect())
            assertEquals(emptyList<Asset>(), it.state.availableAssetsToSelect())
            assertNull(it.state.selectionItemAvailability.first())
            assertNull(it.state.currentAssetAvailability.first())
            assertNull(it.state.currentTokenAvailability.first())
            assertNull(withTimeoutOrNull(100) { it.state.chainId() })
            assertTrue(it.writes.isEmpty())
        }
    }

    @Test
    fun missingExplicitChoiceNeverSubstitutesAnotherHealthyNetwork() = runBlocking {
        Fixture(encoded = "missing:0:RELAYCHAIN").use {
            assertEquals(listOf("a"), it.state.availableToSelect().map { option -> option.chainId })
            assertNull(it.state.selectionItemAvailability.first())
            assertNull(withTimeoutOrNull(100) { it.state.currentToken() })
            assertEquals("missing:0:RELAYCHAIN", it.preference.value)
            assertTrue(it.writes.isEmpty())
            assertFalse(it.state.selectionRevision.matchesAction(it.state.selectionRevision.capture(), StakingType.RELAYCHAIN, "a", "0"))
        }
    }

    @Test
    fun malformedStoredChoicesStayExactAndDoNotBecomeDefaults() = runBlocking {
        listOf("", "a", "a:0", ":0:RELAYCHAIN", "a::RELAYCHAIN", "a:0:", "a:0:FUTURE").forEach { raw ->
            Fixture(encoded = raw).use {
                assertNull(raw, it.state.selectionItemAvailability.first())
                assertEquals(raw, it.preference.value)
                assertTrue(it.writes.isEmpty())
            }
        }
    }

    @Test
    fun colonContainingAssetIdentifierRoundTripsWithoutChangingItsSuffix() = runBlocking {
        Fixture(listOf(chain(assetId = "token:section:7")), encoded = "a:token:section:7:RELAYCHAIN").use {
            val selection = StakingAssetSelection.RelayChainStaking("a", "token:section:7")
            assertEquals(selection, it.state.selectionItem.first())
            it.state.update(selection)
            assertEquals(listOf("STAKING_CURRENT_ASSET_TYPE" to "a:token:section:7:RELAYCHAIN"), it.writes)
            assertEquals(selection, it.state.selectionItem.first())
        }
    }

    @Test
    fun revokedPoolSupportDoesNotFallBackToRelayStaking() = runBlocking {
        Fixture(listOf(chain(pool = true)), encoded = "a:0:POOL").use {
            assertEquals(StakingType.POOL, it.state.selectionItem.first().type)
            it.catalog.value = listOf(chain(pool = false))
            assertNull(it.state.selectionItemAvailability.first())
            assertEquals(listOf(StakingType.RELAYCHAIN), it.state.availableToSelect().map { option -> option.type })
            assertEquals("a:0:POOL", it.preference.value)
            assertTrue(it.writes.isEmpty())
        }
    }

    @Test
    fun unsupportedStakingAssetWithoutPoolsHasNoOperationalOption() = runBlocking {
        Fixture(listOf(chain(staking = CoreAsset.StakingType.UNSUPPORTED))).use {
            assertEquals(emptyList<StakingAssetSelection>(), it.state.availableToSelect())
            assertNull(it.state.currentAssetAvailability.first())
            assertTrue(it.writes.isEmpty())
        }
    }

    @Test
    fun refreshedConfigurationPreservesExactAccountAndBalanceInputs() = runBlocking {
        Fixture().use {
            val original = it.rows.single().copy(
                freeInPlanksState = AssetAmountState.Known(BigInteger("1234567890")), reservedInPlanksState = AssetAmountState.Known(BigInteger("9")),
                miscFrozenInPlanksState = AssetAmountState.Known(BigInteger("11")), feeFrozenInPlanksState = AssetAmountState.Known(BigInteger("13")),
                bondedInPlanksState = AssetAmountState.Known(BigInteger("17")), redeemableInPlanksState = AssetAmountState.Known(BigInteger("19")),
                unbondingInPlanksState = AssetAmountState.Known(BigInteger("23")), sortIndex = 29, enabled = false,
                minSupportedVersion = "1.2", chainAccountName = "Stored name", markedNotNeed = true, status = "Frozen"
            )
            val withPrice = original.copy(token = original.token.copy(fiatRateInput = AssetAmountState.Known(BigDecimal("2.75")), recentRateChangeInput = AssetAmountState.Known(BigDecimal("-1.5"))))
            it.rows = listOf(withPrice)
            val refreshed = chain(precision = 8)
            it.catalog.value = listOf(refreshed)
            val observed = it.state.currentAssetFlow().first()
            assertEquals(withPrice.copy(token = withPrice.token.copy(configuration = refreshed.assets.single())), observed)
            assertSame(refreshed.assets.single(), it.state.currentToken().configuration)
            assertSame(refreshed, it.state.chain())
            assertArrayEquals(withPrice.accountId, observed.accountId)
            assertTrue(it.writes.isEmpty())
        }
    }

    @Test
    fun anotherWalletsAssetRowCannotAuthorizeTheSelectedWallet() = runBlocking {
        Fixture().use {
            it.rows = listOf(it.rows.single().copy(metaId = 99))
            it.catalog.value = listOf(chain())
            assertNull(it.state.currentAssetAvailability.first())
            assertEquals(emptyList<StakingAssetSelection>(), it.state.availableToSelect())
        }
    }

    @Test
    fun oldAccountKeyRowCannotAuthorizeAnUpdatedWalletKey() = runBlocking {
        Fixture().use {
            val old = it.state.selectionRevision.capture()
            it.emitAccount(account(key = ByteArray(32) { 8 }))
            assertNull(it.state.currentAssetAvailability.first())
            assertFalse(it.state.selectionRevision.matchesAction(old, StakingType.RELAYCHAIN, "a", "0"))
            assertTrue(it.writes.isEmpty())
        }
    }

    @Test
    fun realTonKeyChangeRechecksRowsEvenWhenMetaAccountsCompareEqual() = runBlocking {
        val initial = account(key = null, ton = ByteArray(32) { 7 })
        val changed = initial.copy(tonPublicKey = ByteArray(32) { 8 })
        assertEquals(initial, changed)
        Fixture(listOf(chain(ecosystem = Ecosystem.Ton)), initial).use {
            val prior = it.state.selectionRevision.capture()
            it.emitAccount(changed)
            assertNull(it.state.currentAssetAvailability.first())
            assertFalse(it.state.selectionRevision.matchesAction(prior, StakingType.RELAYCHAIN, "a", "0"))
            it.rows = listOf(stored(it.catalog.value.single(), changed))
            it.emitAccount(changed)
            assertArrayEquals(changed.tonPublicKey, it.state.currentAssetFlow().first().accountId)
            assertTrue(it.writes.isEmpty())
        }
    }

    @Test
    fun tonKeyRemovalAndIdenticalRestorationNeverReviveAnOldRevision() = runBlocking {
        val initial = account(key = null, ton = ByteArray(32) { 7 })
        Fixture(listOf(chain(ecosystem = Ecosystem.Ton)), initial).use {
            val original = it.state.selectionRevision.capture()
            val observed = mutableListOf<ByteArray?>()
            val job = launch(Dispatchers.Unconfined) { it.state.currentAssetAvailability.collect { value -> observed += value?.accountId } }
            it.emitAccount(initial.copy(tonPublicKey = null))
            assertNull(it.state.currentAssetAvailability.first())
            assertNull(withTimeoutOrNull(100) { it.state.currentToken() })
            it.emitAccount(initial)
            assertArrayEquals(initial.tonPublicKey, it.state.currentAssetFlow().first().accountId)
            assertFalse(it.state.selectionRevision.matchesAction(original, StakingType.RELAYCHAIN, "a", "0"))
            assertTrue(it.state.selectionRevision.matchesAction(it.state.selectionRevision.capture(), StakingType.RELAYCHAIN, "a", "0"))
            assertTrue(observed.any { value -> value == null })
            assertArrayEquals(initial.tonPublicKey, observed.last())
            job.cancelAndJoin()
            assertTrue(it.writes.isEmpty())
        }
    }

    @Test
    fun switchingWalletReloadsExactCurrentWalletAssets() = runBlocking {
        Fixture().use {
            val changed = account(id = 77)
            it.rows = listOf(stored(it.catalog.value.single(), changed))
            it.emitAccount(changed)
            assertEquals(77L, it.state.currentAssetFlow().first().metaId)
            assertTrue(it.writes.isEmpty())
        }
    }

    @Test
    fun pendingRefreshRevokesAuthorityBeforeRepositoryReadCompletes() = runBlocking {
        Fixture().use {
            val previous = it.state.selectionRevision.capture()
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val fixture = it
            it.read = { entered.complete(Unit); release.await(); fixture.rows }
            it.catalog.value = listOf(chain(precision = 10))
            entered.await()
            assertNull(it.state.currentAssetAvailability.first())
            assertFalse(it.state.selectionRevision.matchesAction(previous, StakingType.RELAYCHAIN, "a", "0"))
            assertFalse(it.state.selectionRevision.matchesAction(it.state.selectionRevision.capture(), StakingType.RELAYCHAIN, "a", "0"))
            release.complete(Unit)
            assertEquals(10, it.state.currentToken().configuration.precision)
            assertNotSame(previous, it.state.selectionRevision.capture())
        }
    }

    @Test
    fun cancelledSlowReadCannotResurrectASelectionAfterCatalogRemoval() = runBlocking {
        Fixture().use {
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val fixture = it
            it.read = { withContext(NonCancellable) { entered.complete(Unit); release.await(); fixture.rows } }
            it.catalog.value = listOf(chain(precision = 10))
            entered.await()
            it.catalog.value = emptyList()
            release.complete(Unit)
            assertEquals(emptyList<StakingAssetSelection>(), it.state.availableToSelect())
            assertNull(it.state.currentTokenAvailability.first())
            assertFalse(it.state.selectionRevision.matchesAction(it.state.selectionRevision.capture(), StakingType.RELAYCHAIN, "a", "0"))
        }
    }

    @Test
    fun identicalCatalogRestorationEmitsCurrentValuesAndRequiresANewRevision() = runBlocking {
        Fixture().use {
            val initialCatalog = it.catalog.value
            val oldRevision = it.state.selectionRevision.capture()
            it.catalog.value = emptyList()
            assertNull(it.state.assetWithChainAvailability.first())
            it.catalog.value = initialCatalog
            assertSame(initialCatalog.single(), it.state.chain())
            assertFalse(it.state.selectionRevision.matchesAction(oldRevision, StakingType.RELAYCHAIN, "a", "0"))
            assertTrue(it.state.selectionRevision.matchesAction(it.state.selectionRevision.capture(), StakingType.RELAYCHAIN, "a", "0"))
            assertTrue(it.writes.isEmpty())
        }
    }

    @Test
    fun identicalAssetIdsOnDifferentChainsKeepTheExactExplicitPair() = runBlocking {
        val first = chain(id = "a")
        val second = chain(id = "b")
        Fixture(listOf(first, second), encoded = "b:0:RELAYCHAIN").use {
            assertSame(second, it.state.chain())
            assertEquals("b", it.state.currentAssetFlow().first().chainId)
            assertEquals(listOf("a", "b"), it.state.availableToSelect().map { option -> option.chainId })
            assertTrue(it.writes.isEmpty())
        }
    }

    @Test
    fun onlyAnExplicitUpdateReplacesAnUnavailableStoredChoice() = runBlocking {
        Fixture(encoded = "missing:0:RELAYCHAIN").use {
            assertNull(it.state.selectionItemAvailability.first())
            val selected = StakingAssetSelection.RelayChainStaking("a", "0")
            it.state.update(selected)
            assertEquals(selected, it.state.selectionItem.first())
            assertEquals(listOf("STAKING_CURRENT_ASSET_TYPE" to "a:0:RELAYCHAIN"), it.writes)
            assertTrue(it.state.selectionRevision.matchesAction(it.state.selectionRevision.capture(), StakingType.RELAYCHAIN, "a", "0"))
        }
    }
}
