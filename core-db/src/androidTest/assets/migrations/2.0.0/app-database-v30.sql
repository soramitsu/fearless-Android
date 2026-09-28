-- Frozen Room schema extracted from
-- jp.co.soramitsu.core_db.AppDatabase_Impl$1.createAllTables in the official
-- Fearless Wallet v2.0.0 (51) Google APK. Do not regenerate this file from
-- current entities. Provenance and immutable hashes live in fixture-manifest.json.
PRAGMA page_size = 4096;
PRAGMA journal_mode = DELETE;
PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS `users` (`address` TEXT NOT NULL, `username` TEXT NOT NULL, `publicKey` TEXT NOT NULL, `cryptoType` INTEGER NOT NULL, `position` INTEGER NOT NULL, `networkType` INTEGER NOT NULL, PRIMARY KEY(`address`));
CREATE TABLE IF NOT EXISTS `nodes` (`name` TEXT NOT NULL, `link` TEXT NOT NULL, `networkType` INTEGER NOT NULL, `isDefault` INTEGER NOT NULL, `isActive` INTEGER NOT NULL, `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL);
CREATE TABLE IF NOT EXISTS `assets` (`tokenSymbol` TEXT NOT NULL, `chainId` TEXT NOT NULL, `accountId` BLOB NOT NULL, `metaId` INTEGER NOT NULL, `freeInPlanks` TEXT NOT NULL, `reservedInPlanks` TEXT NOT NULL, `miscFrozenInPlanks` TEXT NOT NULL, `feeFrozenInPlanks` TEXT NOT NULL, `bondedInPlanks` TEXT NOT NULL, `redeemableInPlanks` TEXT NOT NULL, `unbondingInPlanks` TEXT NOT NULL, PRIMARY KEY(`tokenSymbol`, `chainId`, `accountId`));
CREATE INDEX IF NOT EXISTS `index_assets_metaId` ON `assets` (`metaId`);
CREATE TABLE IF NOT EXISTS `tokens` (`symbol` TEXT NOT NULL, `dollarRate` TEXT, `recentRateChange` TEXT, PRIMARY KEY(`symbol`));
CREATE TABLE IF NOT EXISTS `phishing_addresses` (`publicKey` TEXT NOT NULL, PRIMARY KEY(`publicKey`));
CREATE TABLE IF NOT EXISTS `storage` (`storageKey` TEXT NOT NULL, `content` TEXT, `chainId` TEXT NOT NULL, PRIMARY KEY(`chainId`, `storageKey`));
CREATE TABLE IF NOT EXISTS `account_staking_accesses` (`chainId` TEXT NOT NULL, `chainAssetId` TEXT NOT NULL, `accountId` BLOB NOT NULL, `stashId` BLOB, `controllerId` BLOB, PRIMARY KEY(`chainId`, `chainAssetId`, `accountId`));
CREATE TABLE IF NOT EXISTS `total_reward` (`accountAddress` TEXT NOT NULL, `totalReward` TEXT NOT NULL, PRIMARY KEY(`accountAddress`));
CREATE TABLE IF NOT EXISTS `operations` (`id` TEXT NOT NULL, `address` TEXT NOT NULL, `chainId` TEXT NOT NULL, `chainAssetId` TEXT NOT NULL, `time` INTEGER NOT NULL, `status` INTEGER NOT NULL, `source` INTEGER NOT NULL, `operationType` INTEGER NOT NULL, `module` TEXT, `call` TEXT, `amount` TEXT, `sender` TEXT, `receiver` TEXT, `hash` TEXT, `fee` TEXT, `isReward` INTEGER, `era` INTEGER, `validator` TEXT, PRIMARY KEY(`id`, `address`, `chainId`, `chainAssetId`));
CREATE TABLE IF NOT EXISTS `chains` (`id` TEXT NOT NULL, `parentId` TEXT, `name` TEXT NOT NULL, `icon` TEXT NOT NULL, `prefix` INTEGER NOT NULL, `isEthereumBased` INTEGER NOT NULL, `isTestNet` INTEGER NOT NULL, `hasCrowdloans` INTEGER NOT NULL, `url` TEXT, `overridesCommon` INTEGER, `staking_url` TEXT, `staking_type` TEXT, `history_url` TEXT, `history_type` TEXT, `crowdloans_url` TEXT, `crowdloans_type` TEXT, PRIMARY KEY(`id`));
CREATE TABLE IF NOT EXISTS `chain_nodes` (`chainId` TEXT NOT NULL, `url` TEXT NOT NULL, `name` TEXT NOT NULL, PRIMARY KEY(`chainId`, `url`), FOREIGN KEY(`chainId`) REFERENCES `chains`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE );
CREATE INDEX IF NOT EXISTS `index_chain_nodes_chainId` ON `chain_nodes` (`chainId`);
CREATE TABLE IF NOT EXISTS `chain_assets` (`id` TEXT NOT NULL, `chainId` TEXT NOT NULL, `name` TEXT NOT NULL, `icon` TEXT NOT NULL, `priceId` TEXT, `staking` TEXT NOT NULL, `precision` INTEGER NOT NULL, `priceProviders` TEXT, `nativeChainId` TEXT, PRIMARY KEY(`chainId`, `id`), FOREIGN KEY(`chainId`) REFERENCES `chains`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE );
CREATE INDEX IF NOT EXISTS `index_chain_assets_chainId` ON `chain_assets` (`chainId`);
CREATE TABLE IF NOT EXISTS `chain_runtimes` (`chainId` TEXT NOT NULL, `syncedVersion` INTEGER NOT NULL, `remoteVersion` INTEGER NOT NULL, PRIMARY KEY(`chainId`));
CREATE INDEX IF NOT EXISTS `index_chain_runtimes_chainId` ON `chain_runtimes` (`chainId`);
CREATE TABLE IF NOT EXISTS `meta_accounts` (`substratePublicKey` BLOB NOT NULL, `substrateCryptoType` TEXT NOT NULL, `substrateAccountId` BLOB NOT NULL, `ethereumPublicKey` BLOB, `ethereumAddress` BLOB, `name` TEXT NOT NULL, `isSelected` INTEGER NOT NULL, `position` INTEGER NOT NULL, `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL);
CREATE INDEX IF NOT EXISTS `index_meta_accounts_substrateAccountId` ON `meta_accounts` (`substrateAccountId`);
CREATE INDEX IF NOT EXISTS `index_meta_accounts_ethereumAddress` ON `meta_accounts` (`ethereumAddress`);
CREATE TABLE IF NOT EXISTS `chain_accounts` (`metaId` INTEGER NOT NULL, `chainId` TEXT NOT NULL, `publicKey` BLOB NOT NULL, `accountId` BLOB NOT NULL, `cryptoType` TEXT NOT NULL, PRIMARY KEY(`metaId`, `chainId`), FOREIGN KEY(`chainId`) REFERENCES `chains`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION DEFERRABLE INITIALLY DEFERRED, FOREIGN KEY(`metaId`) REFERENCES `meta_accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE );
CREATE INDEX IF NOT EXISTS `index_chain_accounts_chainId` ON `chain_accounts` (`chainId`);
CREATE INDEX IF NOT EXISTS `index_chain_accounts_metaId` ON `chain_accounts` (`metaId`);
CREATE INDEX IF NOT EXISTS `index_chain_accounts_accountId` ON `chain_accounts` (`accountId`);
CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT);
INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '11b78abe23c541401fca8e23fc427a86');

PRAGMA user_version = 30;
