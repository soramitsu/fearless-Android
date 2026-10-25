package jp.co.soramitsu.coredb.migrations

import androidx.room.migration.Migration

/**
 * The SQL-only portion between the historical preference-writing edges.
 * The production graph and rollback-only qualification use this same list.
 */
internal val HISTORICAL_SQL_MIGRATIONS_32_TO_71: List<Migration> = listOf(
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
)
