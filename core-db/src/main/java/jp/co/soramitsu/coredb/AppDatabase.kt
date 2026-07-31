package jp.co.soramitsu.coredb

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import jp.co.soramitsu.common.data.secrets.v1.SecretStoreV1
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecretValidation
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecretValidator
import jp.co.soramitsu.common.data.secrets.v2.SecretStoreV2
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecretStore
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecretStore
import jp.co.soramitsu.common.data.secrets.v3.WalletRootSecretValidation
import jp.co.soramitsu.common.data.secrets.v3.WalletRootSecretValidator
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.coredb.converters.CryptoTypeConverters
import jp.co.soramitsu.coredb.converters.LongMathConverters
import jp.co.soramitsu.coredb.converters.OperationConverters
import jp.co.soramitsu.coredb.dao.AccountStakingDao
import jp.co.soramitsu.coredb.dao.AddressBookDao
import jp.co.soramitsu.coredb.dao.AssetDao
import jp.co.soramitsu.coredb.dao.ChainDao
import jp.co.soramitsu.coredb.dao.MetaAccountDao
import jp.co.soramitsu.coredb.dao.NomisScoresDao
import jp.co.soramitsu.coredb.dao.OperationDao
import jp.co.soramitsu.coredb.dao.PhishingDao
import jp.co.soramitsu.coredb.dao.PoolDao
import jp.co.soramitsu.coredb.dao.StakingTotalRewardDao
import jp.co.soramitsu.coredb.dao.StorageDao
import jp.co.soramitsu.coredb.dao.TokenPriceDao
import jp.co.soramitsu.coredb.dao.TonConnectDao
import jp.co.soramitsu.coredb.migrations.AddAccountStakingTable_14_15
import jp.co.soramitsu.coredb.migrations.AddChainExplorersTable_33_34
import jp.co.soramitsu.coredb.migrations.AddChainRegistryTables_27_28
import jp.co.soramitsu.coredb.migrations.AddLegacyActiveNodeColumn_18_19
import jp.co.soramitsu.coredb.migrations.AddNetworkTypeToStorageCache_13_14
import jp.co.soramitsu.coredb.migrations.AddOperationsTablesToDb_23_24
import jp.co.soramitsu.coredb.migrations.AddPhishingAddressesTable_10_11
import jp.co.soramitsu.coredb.migrations.AddRuntimeCacheTable_11_12
import jp.co.soramitsu.coredb.migrations.AddStakingRewardsTable_15_16
import jp.co.soramitsu.coredb.migrations.AddStorageCacheTable_12_13
import jp.co.soramitsu.coredb.migrations.AddTokenTable_9_10
import jp.co.soramitsu.coredb.migrations.AddTotalRewardsTableToDb_21_22
import jp.co.soramitsu.coredb.migrations.AssetsMigration_38_39
import jp.co.soramitsu.coredb.migrations.AssetsMigration_40_41
import jp.co.soramitsu.coredb.migrations.AssetsOrderMigration
import jp.co.soramitsu.coredb.migrations.ChainAssetsMigration_39_40
import jp.co.soramitsu.coredb.migrations.ChangePrimaryKeyForRewards_16_17
import jp.co.soramitsu.coredb.migrations.DifferentCurrenciesMigrations_37_38
import jp.co.soramitsu.coredb.migrations.EthereumDerivationPathMigration
import jp.co.soramitsu.coredb.migrations.FixAssetsMigration_36_37
import jp.co.soramitsu.coredb.migrations.LegacyNodeCacheCompatibility_19_20
import jp.co.soramitsu.coredb.migrations.LegacyNodeCacheCompatibility_20_21
import jp.co.soramitsu.coredb.migrations.LegacyNodeCacheCompatibility_24_25
import jp.co.soramitsu.coredb.migrations.LegacyNodeCacheCompatibility_25_26
import jp.co.soramitsu.coredb.migrations.LegacyNodeCacheCompatibility_26_27
import jp.co.soramitsu.coredb.migrations.MigrateTablesToV2_29_30
import jp.co.soramitsu.coredb.migrations.MigrateTablesToV2_30_31
import jp.co.soramitsu.coredb.migrations.MigrateTablesToV2_32_33
import jp.co.soramitsu.coredb.migrations.Migration_41_42
import jp.co.soramitsu.coredb.migrations.Migration_42_43
import jp.co.soramitsu.coredb.migrations.Migration_43_44
import jp.co.soramitsu.coredb.migrations.Migration_44_45
import jp.co.soramitsu.coredb.migrations.Migration_45_46
import jp.co.soramitsu.coredb.migrations.Migration_46_47
import jp.co.soramitsu.coredb.migrations.Migration_47_48
import jp.co.soramitsu.coredb.migrations.Migration_48_49
import jp.co.soramitsu.coredb.migrations.Migration_49_50
import jp.co.soramitsu.coredb.migrations.Migration_50_51
import jp.co.soramitsu.coredb.migrations.Migration_51_52
import jp.co.soramitsu.coredb.migrations.Migration_52_53
import jp.co.soramitsu.coredb.migrations.Migration_53_54
import jp.co.soramitsu.coredb.migrations.Migration_54_55
import jp.co.soramitsu.coredb.migrations.Migration_55_56
import jp.co.soramitsu.coredb.migrations.Migration_56_57
import jp.co.soramitsu.coredb.migrations.Migration_57_58
import jp.co.soramitsu.coredb.migrations.Migration_58_59
import jp.co.soramitsu.coredb.migrations.Migration_59_60
import jp.co.soramitsu.coredb.migrations.Migration_60_61
import jp.co.soramitsu.coredb.migrations.Migration_61_62
import jp.co.soramitsu.coredb.migrations.Migration_62_63
import jp.co.soramitsu.coredb.migrations.Migration_63_64
import jp.co.soramitsu.coredb.migrations.Migration_64_65
import jp.co.soramitsu.coredb.migrations.Migration_65_66
import jp.co.soramitsu.coredb.migrations.Migration_66_67
import jp.co.soramitsu.coredb.migrations.Migration_67_68
import jp.co.soramitsu.coredb.migrations.Migration_68_69
import jp.co.soramitsu.coredb.migrations.Migration_69_70
import jp.co.soramitsu.coredb.migrations.Migration_70_71
import jp.co.soramitsu.coredb.migrations.Migration_72_73
import jp.co.soramitsu.coredb.migrations.Migration_73_74
import jp.co.soramitsu.coredb.migrations.Migration_74_75
import jp.co.soramitsu.coredb.migrations.Migration_75_76
import jp.co.soramitsu.coredb.migrations.RemoveAccountForeignKeyFromAsset_17_18
import jp.co.soramitsu.coredb.migrations.RemoveLegacyData_35_36
import jp.co.soramitsu.coredb.migrations.RemoveStakingRewardsTable_22_23
import jp.co.soramitsu.coredb.migrations.TonMigration
import jp.co.soramitsu.coredb.migrations.V2Migration
import jp.co.soramitsu.coredb.migrations.WalletSecretIntegrityMigration
import jp.co.soramitsu.coredb.model.AccountStakingLocal
import jp.co.soramitsu.coredb.model.AddressBookContact
import jp.co.soramitsu.coredb.model.AssetLocal
import jp.co.soramitsu.coredb.model.BasicPoolLocal
import jp.co.soramitsu.coredb.model.ChainAccountLocal
import jp.co.soramitsu.coredb.model.MetaAccountLocal
import jp.co.soramitsu.coredb.model.NomisWalletScoreLocal
import jp.co.soramitsu.coredb.model.OperationLocal
import jp.co.soramitsu.coredb.model.PhishingLocal
import jp.co.soramitsu.coredb.model.StorageEntryLocal
import jp.co.soramitsu.coredb.model.TokenPriceLocal
import jp.co.soramitsu.coredb.model.TonConnectionLocal
import jp.co.soramitsu.coredb.model.TotalRewardLocal
import jp.co.soramitsu.coredb.model.UserPoolLocal
import jp.co.soramitsu.coredb.model.chain.ChainAssetLocal
import jp.co.soramitsu.coredb.model.chain.ChainExplorerLocal
import jp.co.soramitsu.coredb.model.chain.ChainLocal
import jp.co.soramitsu.coredb.model.chain.ChainNodeLocal
import jp.co.soramitsu.coredb.model.chain.ChainRuntimeInfoLocal
import jp.co.soramitsu.coredb.model.chain.ChainTypesLocal
import jp.co.soramitsu.coredb.model.chain.FavoriteChainLocal

@Database(
    version = APP_DATABASE_VERSION,
    entities = [
        AddressBookContact::class,
        AssetLocal::class,
        TokenPriceLocal::class,
        PhishingLocal::class,
        StorageEntryLocal::class,
        AccountStakingLocal::class,
        TotalRewardLocal::class,
        OperationLocal::class,

        ChainLocal::class,
        ChainNodeLocal::class,
        ChainAssetLocal::class,
        FavoriteChainLocal::class,
        ChainRuntimeInfoLocal::class,
        MetaAccountLocal::class,
        ChainAccountLocal::class,
        ChainExplorerLocal::class,
        ChainTypesLocal::class,
        NomisWalletScoreLocal::class,
        BasicPoolLocal::class,
        UserPoolLocal::class,
        TonConnectionLocal::class
    ]
)
@TypeConverters(
    LongMathConverters::class,
    OperationConverters::class,
    CryptoTypeConverters::class
)
abstract class AppDatabase : RoomDatabase() {

    companion object {

        private var instance: AppDatabase? = null

        @Synchronized
        fun get(
            context: Context,
            storeV1: SecretStoreV1,
            storeV2: SecretStoreV2,
            encryptedPreferences: EncryptedPreferences,
            substrateSecretStore: SubstrateSecretStore,
            ethereumSecretStore: EthereumSecretStore
        ): AppDatabase {
            if (instance == null) {
                instance = create(
                    context = context,
                    databaseName = "app.db",
                    storeV1 = storeV1,
                    storeV2 = storeV2,
                    encryptedPreferences = encryptedPreferences,
                    substrateSecretStore = substrateSecretStore,
                    ethereumSecretStore = ethereumSecretStore
                )
            }
            return instance!!
        }

        internal fun create(
            context: Context,
            databaseName: String,
            storeV1: SecretStoreV1,
            storeV2: SecretStoreV2,
            encryptedPreferences: EncryptedPreferences,
            substrateSecretStore: SubstrateSecretStore,
            ethereumSecretStore: EthereumSecretStore,
            chainAccountSecretValidation: ChainAccountSecretValidation =
                ChainAccountSecretValidator,
            walletRootSecretValidation: WalletRootSecretValidation =
                WalletRootSecretValidator
        ): AppDatabase {
            val migrations = migrations(
                storeV1 = storeV1,
                storeV2 = storeV2,
                encryptedPreferences = encryptedPreferences,
                substrateSecretStore = substrateSecretStore,
                ethereumSecretStore = ethereumSecretStore,
                chainAccountSecretValidation = chainAccountSecretValidation,
                walletRootSecretValidation = walletRootSecretValidation
            )

            requireCompleteAppDatabaseUpgradePath(migrations.asList())

            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                databaseName
            )
                .addMigrations(*migrations)
                .build()
        }

        internal fun migrations(
            storeV1: SecretStoreV1,
            storeV2: SecretStoreV2,
            encryptedPreferences: EncryptedPreferences,
            substrateSecretStore: SubstrateSecretStore,
            ethereumSecretStore: EthereumSecretStore,
            chainAccountSecretValidation: ChainAccountSecretValidation =
                ChainAccountSecretValidator,
            walletRootSecretValidation: WalletRootSecretValidation =
                WalletRootSecretValidator
        ): Array<Migration> = arrayOf(
            AddTokenTable_9_10,
            AddPhishingAddressesTable_10_11,
            AddRuntimeCacheTable_11_12,
            AddStorageCacheTable_12_13,
            AddNetworkTypeToStorageCache_13_14,
            AddAccountStakingTable_14_15,
            AddStakingRewardsTable_15_16,
            ChangePrimaryKeyForRewards_16_17,
            RemoveAccountForeignKeyFromAsset_17_18,
            AddLegacyActiveNodeColumn_18_19,
            LegacyNodeCacheCompatibility_19_20,
            LegacyNodeCacheCompatibility_20_21,
            AddTotalRewardsTableToDb_21_22,
            RemoveStakingRewardsTable_22_23,
            AddOperationsTablesToDb_23_24,
            LegacyNodeCacheCompatibility_24_25,
            LegacyNodeCacheCompatibility_25_26,
            LegacyNodeCacheCompatibility_26_27,
            AddChainRegistryTables_27_28,
            V2Migration(storeV1, encryptedPreferences),
            MigrateTablesToV2_29_30,
            MigrateTablesToV2_30_31,
            EthereumDerivationPathMigration(encryptedPreferences),
            MigrateTablesToV2_32_33,
            AddChainExplorersTable_33_34,
            AssetsOrderMigration(),
            RemoveLegacyData_35_36,
            FixAssetsMigration_36_37,
            DifferentCurrenciesMigrations_37_38,
            AssetsMigration_38_39,
            ChainAssetsMigration_39_40,
            AssetsMigration_40_41,
            Migration_41_42,
            Migration_42_43,
            Migration_43_44,
            Migration_44_45,
            Migration_45_46,
            Migration_46_47,
            Migration_47_48,
            Migration_48_49,
            Migration_49_50,
            Migration_50_51,
            Migration_51_52,
            Migration_52_53,
            Migration_53_54,
            Migration_54_55,
            Migration_55_56,
            Migration_56_57,
            Migration_57_58,
            Migration_58_59,
            Migration_59_60,
            Migration_60_61,
            Migration_61_62,
            Migration_62_63,
            Migration_63_64,
            Migration_64_65,
            Migration_65_66,
            Migration_66_67,
            Migration_67_68,
            Migration_68_69,
            Migration_69_70,
            Migration_70_71,
            TonMigration(
                encryptedPreferences = encryptedPreferences,
                walletRootSecretValidation = walletRootSecretValidation
            ),
            Migration_72_73,
            Migration_73_74,
            Migration_74_75,
            Migration_75_76,
            WalletSecretIntegrityMigration(
                encryptedPreferences = encryptedPreferences,
                chainAccountSecretValidation = chainAccountSecretValidation,
                walletRootSecretValidation = walletRootSecretValidation
            )
        )
    }

    abstract fun assetDao(): AssetDao

    abstract fun operationDao(): OperationDao

    abstract fun phishingDao(): PhishingDao

    abstract fun storageDao(): StorageDao

    abstract fun tokenDao(): TokenPriceDao

    abstract fun accountStakingDao(): AccountStakingDao

    abstract fun stakingTotalRewardDao(): StakingTotalRewardDao

    abstract fun chainDao(): ChainDao

    abstract fun metaAccountDao(): MetaAccountDao

    abstract fun addressBookDao(): AddressBookDao

    abstract fun nomisScoresDao(): NomisScoresDao

    abstract fun poolDao(): PoolDao

    abstract fun tonConnectDao(): TonConnectDao
}
