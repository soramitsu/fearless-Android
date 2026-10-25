package jp.co.soramitsu.account.impl.domain

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.math.BigDecimal
import java.math.BigInteger
import java.security.MessageDigest
import jp.co.soramitsu.account.api.domain.model.TotalBalance
import jp.co.soramitsu.account.impl.data.mappers.mapMetaAccountLocalToMetaAccount
import jp.co.soramitsu.common.data.secrets.v1.SecretStoreV1Impl
import jp.co.soramitsu.common.data.secrets.v2.SecretStoreV2
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecretStore
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecretStore
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.coredb.AppDatabase
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.AssetSearchSection
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.PortfolioVisibility
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.SharedPortfolioConfigSource
import jp.co.soramitsu.testshared.HashMapEncryptedPreferences
import jp.co.soramitsu.wallet.impl.data.mappers.mapCachedAssetToAsset
import jp.co.soramitsu.wallet.impl.data.repository.CurrentAccountAssetContext
import jp.co.soramitsu.wallet.impl.data.repository.CurrentAccountAssetSnapshot
import jp.co.soramitsu.wallet.impl.domain.model.FiatValuationContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Upgrade path for what the portfolio shows and counts (WP-1). Every released build from 3.5.1
 * (database 66) to 4.2.0 (database 75) wrote asset preferences with the same rule: on the first
 * sync of a funded wallet it set `enabled = 0` on every empty asset, it never revealed a later
 * deposit into one of them, and a wallet that was empty at its first sync kept its popular
 * utility rows at `enabled = 1`. The header counted only `enabled = 1` rows, testnets included.
 *
 * The rows below are written in exactly those states into the frozen 3.7.4 database (version 71,
 * extracted from the released APK) and the production open replays every shipped migration up to
 * the current version. The current visibility rule and total are then computed from what the
 * upgrade left, and compared with what the shipped total formula gave for the same rows.
 */
@RunWith(AndroidJUnit4::class)
class ReleasedPortfolioUpgradeTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    @After
    fun clearDatabase() {
        closeProductionDatabase()
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun released374WalletShowsFundedAndPopularRowsAndCountsTheShippedTotalMinusTheIntendedFixes() {
        installReleased374Database(DATABASE_NAME)
        withVersion71Database(DATABASE_NAME) { database ->
            CHAINS.forEach { chain -> database.insertVersion71Chain(chain) }
            RELEASED_ROWS.forEach { row -> database.insertVersion71Asset(row) }
        }

        val preferences = HashMapEncryptedPreferences()
        // The production open, which replays every shipped migration from version 71.
        val roomDatabase = AppDatabase.get(
            context = context,
            storeV1 = SecretStoreV1Impl(preferences),
            storeV2 = SecretStoreV2(preferences),
            encryptedPreferences = preferences,
            substrateSecretStore = SubstrateSecretStore(preferences),
            ethereumSecretStore = EthereumSecretStore(preferences)
        )
        try {
            val database = roomDatabase.openHelper.writableDatabase
            assertTrue("upgraded through the 80 to 81 hide normalization", database.version >= AUTO_HIDE_NORMALIZED_VERSION)
            // Prices are a rebuildable cache: the price sync writes them after the upgrade.
            PRICES.forEach { (priceId, rate) -> database.insertPrice(priceId, rate) }

            val upgradedRows = kotlinx.coroutines.runBlocking { roomDatabase.assetDao().getAssets(WALLET_META_ID) }
            assertEquals(RELEASED_ROWS.size, upgradedRows.size)
            val visibility = PortfolioVisibility(bundledConfigSource())
            val chainsById = CHAINS.associateBy(Chain::id)

            val visible = upgradedRows.filter { row ->
                val chain = chainsById.getValue(row.asset.chainId)
                visibility.isVisible(
                    enabled = row.asset.enabled,
                    total = checkNotNull(mapCachedAssetToAsset(
                        row, chain.assetsById.getValue(row.asset.id), chain.minSupportedVersion, true, FiatValuationContext("usd")
                    ).total),
                    chain = chain,
                    asset = chain.assetsById.getValue(row.asset.id),
                    isFavourite = false,
                    isOnlySelectedNetwork = false,
                    isSendVetoed = false,
                    showTestnets = false
                )
            }.map { RowKey(it.asset.chainId, it.asset.id) }.toSet()
            assertEquals(
                RELEASED_ROWS.filter(ReleasedRow::visibleAfterUpgrade).map(ReleasedRow::key).toSet(),
                visible
            )

            val upgradedWallet = kotlinx.coroutines.runBlocking { roomDatabase.metaAccountDao().getJoinedMetaAccountInfo(WALLET_META_ID) }
            val snapshot = CurrentAccountAssetSnapshot(
                CurrentAccountAssetContext(mapMetaAccountLocalToMetaAccount(chainsById, upgradedWallet), FiatValuationContext("usd")),
                chainsById, upgradedRows, quotesReady = true
            )
            val total = calculateTrustedTotalBalance(
                snapshot = snapshot,
                isTestnet = visibility::isTestnet,
                hasTrustedPrice = visibility::hasTrustedPrice
            ) as TotalBalance.Ready
            assertSameAmount("50", total.shown.knownSubtotal)
            assertSameAmount("25", total.hidden.amount)
            assertSameAmount("75", total.allHoldings.knownSubtotal)
            assertNull("untrusted USDT keeps the complete shown valuation unavailable", total.shown.amount)
            assertNull("untrusted USDT keeps the complete holdings valuation unavailable", total.allHoldings.amount)
            assertEquals("the deposit the old build left hidden is reported", 1, total.hidden.holdingCount)
            assertEquals("the third-party Kusama AssetHub USDT has no trusted price", 1, total.shown.unpricedAssetCount)

            // The shipped header over the same rows counted the distrusted USDT at $1 and the
            // priced testnet token; those two are the only intended differences.
            val shippedTotal = shippedTotalFormula(RELEASED_ROWS)
            assertSameAmount("120", shippedTotal)
            assertSameAmount(
                (shippedTotal - THIRD_PARTY_USDT_VALUE - TESTNET_VALUE).toPlainString(),
                total.shown.knownSubtotal
            )
        } finally {
            closeProductionDatabase()
        }
    }

    /**
     * Search and the asset pickers on the same upgraded rows (WP-2). Released builds listed only
     * `enabled = 1` rows there; after the upgrade Search must still find every row 4.2.0 found
     * (testnets now wait for the Testnets toggle), and it must also find what 4.2.0 hid or never
     * showed: the funded row the old build left hidden (Hidden section, still spendable), the
     * empty rows it hid automatically (offered to receive), and the zero default.
     */
    @Test
    fun released374WalletIsFoundBySearchAndThePickersAfterTheUpgrade() {
        installReleased374Database(DATABASE_NAME)
        withVersion71Database(DATABASE_NAME) { database ->
            CHAINS.forEach { chain -> database.insertVersion71Chain(chain) }
            RELEASED_ROWS.forEach { row -> database.insertVersion71Asset(row) }
        }

        val preferences = HashMapEncryptedPreferences()
        val roomDatabase = AppDatabase.get(
            context = context,
            storeV1 = SecretStoreV1Impl(preferences),
            storeV2 = SecretStoreV2(preferences),
            encryptedPreferences = preferences,
            substrateSecretStore = SubstrateSecretStore(preferences),
            ethereumSecretStore = EthereumSecretStore(preferences)
        )
        try {
            val upgradedRows = kotlinx.coroutines.runBlocking { roomDatabase.assetDao().getAssets(WALLET_META_ID) }
            assertEquals(RELEASED_ROWS.size, upgradedRows.size)
            val visibility = PortfolioVisibility(bundledConfigSource())
            val chainsById = CHAINS.associateBy(Chain::id)

            val sections = upgradedRows.associate { row ->
                val chain = chainsById.getValue(row.asset.chainId)
                val asset = chain.assetsById.getValue(row.asset.id)
                RowKey(row.asset.chainId, row.asset.id) to visibility.searchSection(
                    enabled = row.asset.enabled,
                    total = checkNotNull(mapCachedAssetToAsset(
                        row, asset, chain.minSupportedVersion, true, FiatValuationContext("usd")
                    ).total),
                    chain = chain,
                    asset = asset,
                    isFavourite = false,
                    isOnlySelectedNetwork = false,
                    isSendVetoed = false,
                    showTestnets = false
                )
            }
            assertEquals(
                mapOf(
                    RowKey(POLKADOT, DOT) to AssetSearchSection.Portfolio,
                    RowKey(KUSAMA, KSM) to AssetSearchSection.Portfolio,
                    RowKey(PENDULUM, PEN) to AssetSearchSection.Portfolio,
                    RowKey(POLKADOT_ASSET_HUB, ASSET_HUB_USDT) to AssetSearchSection.Hidden,
                    RowKey(POLKADOT_ASSET_HUB, ASSET_HUB_USDC) to AssetSearchSection.Other,
                    RowKey(POLKADOT_ASSET_HUB, DOT) to AssetSearchSection.Other,
                    RowKey(KUSAMA_ASSET_HUB, THIRD_PARTY_USDT) to AssetSearchSection.Portfolio,
                    RowKey(CANARY_MATRIXCHAIN, CENJ) to null
                ),
                sections
            )
            // What the shipped search listed (enabled = 1) is still found, except the testnet.
            val shippedSearch = RELEASED_ROWS.filter { it.enabled == SHOWN }.map(ReleasedRow::key)
            shippedSearch.filterNot { it.chainId == CANARY_MATRIXCHAIN }.forEach { key ->
                assertTrue("$key was found by 4.2.0", sections[key] == AssetSearchSection.Portfolio)
            }

            val spendable = upgradedRows.filter { row ->
                val chain = chainsById.getValue(row.asset.chainId)
                val asset = chain.assetsById.getValue(row.asset.id)
                visibility.isPickableToSpend(
                    sendAvailable = checkNotNull(mapCachedAssetToAsset(
                        row, asset, chain.minSupportedVersion, true, FiatValuationContext("usd")
                    ).sendAvailable),
                    chain = chain,
                    isPickerNetwork = false,
                    showTestnets = false
                )
            }.map { RowKey(it.asset.chainId, it.asset.id) }.toSet()
            assertEquals(
                "every funded mainnet row can be picked to send, the one 4.2.0 left hidden included",
                setOf(
                    RowKey(POLKADOT, DOT),
                    RowKey(POLKADOT_ASSET_HUB, ASSET_HUB_USDT),
                    RowKey(KUSAMA_ASSET_HUB, THIRD_PARTY_USDT)
                ),
                spendable
            )

            val receivableOnAssetHub = upgradedRows.filter { row ->
                val chain = chainsById.getValue(row.asset.chainId)
                row.asset.chainId == POLKADOT_ASSET_HUB && visibility.isPickableToReceive(
                    chain = chain,
                    asset = chain.assetsById.getValue(row.asset.id),
                    isPickerNetwork = true,
                    showTestnets = false
                )
            }.map { it.asset.id }.toSet()
            assertEquals(setOf(DOT, ASSET_HUB_USDT, ASSET_HUB_USDC), receivableOnAssetHub)
        } finally {
            closeProductionDatabase()
        }
    }

    // AppDatabase.get keeps a process singleton; drop it so the next test opens the file again.
    private fun closeProductionDatabase() {
        val singleton = AppDatabase::class.java.getDeclaredField("instance").apply { isAccessible = true }
        (singleton.get(null) as? AppDatabase)?.close()
        singleton.set(null, null)
    }

    /** 4.2.0 `TotalBalanceUseCaseImpl` (b5e22d49d): `enabled == true` rows with a positive free balance. */
    private fun shippedTotalFormula(rows: List<ReleasedRow>): BigDecimal {
        val chainsById = CHAINS.associateBy(Chain::id)
        return rows
            .filter { it.enabled == SHOWN && it.free.signum() > 0 }
            .fold(BigDecimal.ZERO) { sum, row ->
                val asset = chainsById.getValue(row.chainId).assetsById.getValue(row.assetId)
                val rate = PRICES.getValue(requireNotNull(asset.priceId))
                sum + (row.free + row.reserved).toBigDecimal(scale = asset.precision) * BigDecimal(rate)
            }
    }

    private fun bundledConfigSource() = SharedPortfolioConfigSource(
        readAsset = { name -> context.assets.open(name).use { it.readBytes() } },
        onLoadFailure = { name, error -> throw AssertionError("Bundled $name failed to load", error) }
    )

    // The frozen database the core-db upgrade tests use (ReleasedV374DatabaseFixture), byte for byte.
    private fun installReleased374Database(databaseName: String) {
        val target = context.getDatabasePath(databaseName)
        context.deleteDatabase(databaseName)
        checkNotNull(target.parentFile).mkdirs()
        context.assets.open(RELEASED_374_DATABASE_ASSET).use { source ->
            target.outputStream().use { destination -> source.copyTo(destination) }
        }
        assertEquals("frozen 3.7.4 database", RELEASED_374_DATABASE_SHA256, target.sha256())
    }

    private fun withVersion71Database(databaseName: String, block: (SupportSQLiteDatabase) -> Unit) {
        val callback = object : SupportSQLiteOpenHelper.Callback(RELEASED_374_DATABASE_VERSION) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                error("The frozen 3.7.4 database was not installed")
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                error("Unexpected fixture setup migration from $oldVersion to $newVersion")
            }
        }
        val openHelper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(callback)
                .build()
        )
        try {
            openHelper.writableDatabase.let { database ->
                database.setForeignKeyConstraintsEnabled(true)
                assertEquals(RELEASED_374_DATABASE_VERSION, database.version)
                block(database)
            }
        } finally {
            openHelper.close()
        }
    }

    private fun SupportSQLiteDatabase.insertVersion71Chain(chain: Chain) {
        execSQL(
            """
            INSERT INTO chains(
                id, rank, name, icon, prefix, isEthereumBased, isTestNet, hasCrowdloans,
                supportStakingPool, isEthereumChain, isChainlinkProvider, supportNft,
                isUsesAppId
            ) VALUES(?, ?, ?, 'icon', 0, 0, ?, 0, 0, 0, 0, 0, 0)
            """.trimIndent(),
            arrayOf<Any?>(chain.id, chain.rank, chain.name, if (chain.isTestNet) 1 else 0)
        )
    }

    private fun SupportSQLiteDatabase.insertVersion71Asset(row: ReleasedRow) {
        val asset = CHAINS.single { it.id == row.chainId }.assetsById.getValue(row.assetId)
        execSQL(
            """
            INSERT INTO assets(
                id, chainId, accountId, metaId, tokenPriceId,
                freeInPlanks, reservedInPlanks, miscFrozenInPlanks, feeFrozenInPlanks,
                bondedInPlanks, redeemableInPlanks, unbondingInPlanks,
                sortIndex, enabled, markedNotNeed, chainAccountName, status
            ) VALUES(?, ?, ?, ?, ?, ?, ?, '0', '0', NULL, NULL, NULL, ?, ?, 0, NULL, NULL)
            """.trimIndent(),
            arrayOf<Any?>(
                row.assetId,
                row.chainId,
                WALLET_ACCOUNT_ID,
                WALLET_META_ID,
                asset.priceId,
                row.free.toString(),
                row.reserved.toString(),
                Int.MAX_VALUE,
                row.enabled
            )
        )
    }

    private fun SupportSQLiteDatabase.insertPrice(priceId: String, rate: String) {
        execSQL(
            "INSERT OR REPLACE INTO token_price(priceId, fiatRate, fiatSymbol, recentRateChange, fiatCurrencyId) VALUES(?, ?, '$', '0', 'usd')",
            arrayOf<Any>(priceId, rate)
        )
    }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun assertSameAmount(expected: String, actual: BigDecimal?) {
        assertTrue("expected $expected but was $actual", actual != null && BigDecimal(expected).compareTo(actual) == 0)
    }

    private data class RowKey(val chainId: String, val assetId: String)

    /** One asset row exactly as a released build left it. */
    private class ReleasedRow(
        val chainId: String,
        val assetId: String,
        val enabled: Int,
        val free: BigInteger,
        val reserved: BigInteger = BigInteger.ZERO,
        val visibleAfterUpgrade: Boolean
    ) {
        val key: RowKey
            get() = RowKey(chainId, assetId)
    }

    private companion object {
        const val RELEASED_374_DATABASE_ASSET = "migrations/3.7.4/app-database-v71.db"
        const val RELEASED_374_DATABASE_SHA256 = "ca0c5efa69b56e46bba3cd218194e443133c83dd03ff044b972af65657d075a9"
        const val RELEASED_374_DATABASE_VERSION = 71
        const val AUTO_HIDE_NORMALIZED_VERSION = 81

        // The name AppDatabase.get opens; this test APK has its own data directory.
        const val DATABASE_NAME = "app.db"

        const val WALLET_META_ID = 7L
        const val HIDDEN = 0
        const val SHOWN = 1
        val WALLET_ACCOUNT_ID = ByteArray(32) { index -> (index + 1).toByte() }

        const val POLKADOT = "91b171bb158e2d3848fa23a9f1c25182fb8e20313b2c1eb49219da7a70ce90c3"
        const val KUSAMA = "b0a8d493285c2df73290dfb7e61f870f17b41801197a149ca93654499ea3dafe"
        const val PENDULUM = "5d3c298622d5634ed019bf61ea4b71655030015bde9beb0d6a24743714462c86"
        const val POLKADOT_ASSET_HUB = "68d56f15f85d3136970ec16946040bc1752654e906147f7e43e9d539d7c3de2f"
        const val KUSAMA_ASSET_HUB = "48239ef607d7928874027a43a67689209727dfb3d3dc5e5b03a39bdc2eda771a"
        const val CANARY_MATRIXCHAIN = "a37725fd8943d2a524cb7ecc65da438f9fa644db78ba24dcd0003e2f95645e8f"

        const val DOT = "887a17c7-1370-4de0-97dd-5422e294fa75"
        const val KSM = "1e0c2ec6-935f-49bd-a854-5e12ee6c9f1b"
        const val PEN = "ebf2822f-2dd4-4f59-aeb3-ae7defa53932"
        const val ASSET_HUB_USDT = "ea33432f-9bd9-4d42-93bc-cd45a0e8a44c"
        const val ASSET_HUB_USDC = "951ca8aa-c390-4512-a35c-d3851440b580"
        const val THIRD_PARTY_USDT = "27768227-8a9a-4443-a57d-6013c24f85e9"
        const val CENJ = "85c17bd8-2565-4cf3-8e0f-14f38ff55eee"

        val PRICES = mapOf(
            "polkadot" to "5",
            "kusama" to "30",
            "pendulum-chain" to "0.1",
            "tether" to "1",
            "usd-coin" to "1",
            "enjincoin" to "0.2"
        )
        val THIRD_PARTY_USDT_VALUE = BigDecimal("50")
        val TESTNET_VALUE = BigDecimal("20")

        // Registry v13 entries, as the reviewed snapshot lists them.
        val CHAINS = listOf(
            chain(POLKADOT, "Polkadot", rank = 8, asset(DOT, "dot", 10, "polkadot", isUtility = true)),
            chain(KUSAMA, "Kusama", rank = 9, asset(KSM, "ksm", 12, "kusama", isUtility = true)),
            chain(PENDULUM, "Pendulum", rank = 10, asset(PEN, "pen", 12, "pendulum-chain", isUtility = true)),
            chain(
                POLKADOT_ASSET_HUB,
                "Polkadot AssetHub",
                rank = null,
                asset(DOT, "dot", 10, "polkadot", isUtility = true),
                asset(ASSET_HUB_USDT, "usdt", 6, "tether", type = ChainAssetType.Assets),
                asset(ASSET_HUB_USDC, "usdc", 6, "usd-coin", type = ChainAssetType.Assets)
            ),
            chain(
                KUSAMA_ASSET_HUB,
                "Kusama AssetHub",
                rank = null,
                asset(KSM, "ksm", 12, "kusama", isUtility = true),
                asset(THIRD_PARTY_USDT, "usdt", 4, "tether", type = ChainAssetType.Assets)
            ),
            chain(
                CANARY_MATRIXCHAIN,
                "Canary Matrixchain",
                rank = null,
                asset(CENJ, "cenj", 18, "enjincoin", isUtility = true),
                isTestNet = true
            )
        )

        /**
         * A wallet that was empty at its first sync (popular rows written as 1) and funded later,
         * then synced again by a build that hid every empty asset of a funded wallet.
         */
        val RELEASED_ROWS = listOf(
            // Funded and shown: stays shown and counted.
            ReleasedRow(POLKADOT, DOT, SHOWN, free = BigInteger("100000000000"), visibleAfterUpgrade = true),
            // Empty, hidden automatically: Auto after the upgrade, a ranked mainnet utility at zero.
            ReleasedRow(KUSAMA, KSM, HIDDEN, free = BigInteger.ZERO, visibleAfterUpgrade = true),
            // Empty popular row the old build wrote as shown: still shown at zero, as 4.2.0 showed it.
            ReleasedRow(PENDULUM, PEN, SHOWN, free = BigInteger.ZERO, visibleAfterUpgrade = true),
            // A deposit into an automatically hidden row, never revealed by the old build: stays
            // hidden, is reported beside the total and is never silently revealed.
            ReleasedRow(POLKADOT_ASSET_HUB, ASSET_HUB_USDT, HIDDEN, free = BigInteger("25000000"), visibleAfterUpgrade = false),
            // Empty and hidden automatically, not a zero default: Auto and not listed.
            ReleasedRow(POLKADOT_ASSET_HUB, ASSET_HUB_USDC, HIDDEN, free = BigInteger.ZERO, visibleAfterUpgrade = false),
            ReleasedRow(POLKADOT_ASSET_HUB, DOT, HIDDEN, free = BigInteger.ZERO, visibleAfterUpgrade = false),
            // The third-party "USDT" the registry prices as Tether, shown: listed, never valued.
            ReleasedRow(KUSAMA_ASSET_HUB, THIRD_PARTY_USDT, SHOWN, free = BigInteger("500000"), visibleAfterUpgrade = true),
            // A priced testnet token the old header counted: behind the Testnets toggle, never counted.
            ReleasedRow(
                CANARY_MATRIXCHAIN,
                CENJ,
                SHOWN,
                free = BigInteger("100000000000000000000"),
                visibleAfterUpgrade = false
            )
        )

        fun asset(
            id: String,
            symbol: String,
            precision: Int,
            priceId: String,
            isUtility: Boolean = false,
            type: ChainAssetType = ChainAssetType.Normal
        ) = Asset(
            id = id,
            name = symbol,
            symbol = symbol,
            iconUrl = "",
            chainId = "",
            chainName = "",
            chainIcon = null,
            isTestNet = false,
            priceId = priceId,
            precision = precision,
            staking = Asset.StakingType.UNSUPPORTED,
            purchaseProviders = null,
            supportStakingPool = false,
            isUtility = isUtility,
            type = type,
            currencyId = null,
            existentialDeposit = null,
            color = null,
            isNative = null
        )

        fun chain(id: String, name: String, rank: Int?, vararg assets: Asset, isTestNet: Boolean = false) = Chain(
            id = id,
            paraId = null,
            rank = rank,
            name = name,
            minSupportedVersion = null,
            assets = assets.map { it.copy(chainId = id, chainName = name, isTestNet = isTestNet) },
            nodes = emptyList(),
            explorers = emptyList(),
            externalApi = null,
            icon = "",
            addressPrefix = 0,
            isEthereumBased = false,
            isTestNet = isTestNet,
            hasCrowdloans = false,
            parentId = null,
            supportStakingPool = false,
            isEthereumChain = false,
            chainlinkProvider = false,
            supportNft = false,
            isUsesAppId = false,
            identityChain = null,
            ecosystem = Ecosystem.Substrate,
            remoteAssetsSource = null
        )
    }
}
