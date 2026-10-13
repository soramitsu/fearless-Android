-- Frozen Room schema extracted from
-- jp.co.soramitsu.coredb.AppDatabase_Impl$1.createAllTables in the official
-- Fearless Wallet 3.7.4 (209) APK. Do not regenerate this file from current
-- entities. Provenance and immutable hashes live in fixture-manifest.json.
PRAGMA page_size = 4096;
PRAGMA journal_mode = DELETE;
PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS `users` (`address` TEXT NOT NULL, `username` TEXT NOT NULL, `publicKey` TEXT NOT NULL, `cryptoType` INTEGER NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`address`));
CREATE TABLE IF NOT EXISTS `address_book` (`address` TEXT NOT NULL, `name` TEXT, `chainId` TEXT NOT NULL, `created` INTEGER NOT NULL, `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL);
CREATE UNIQUE INDEX IF NOT EXISTS `index_address_book_address_chainId` ON `address_book` (`address`, `chainId`);
CREATE TABLE IF NOT EXISTS `assets` (`id` TEXT NOT NULL, `chainId` TEXT NOT NULL, `accountId` BLOB NOT NULL, `metaId` INTEGER NOT NULL, `tokenPriceId` TEXT, `freeInPlanks` TEXT, `reservedInPlanks` TEXT, `miscFrozenInPlanks` TEXT, `feeFrozenInPlanks` TEXT, `bondedInPlanks` TEXT, `redeemableInPlanks` TEXT, `unbondingInPlanks` TEXT, `sortIndex` INTEGER NOT NULL, `enabled` INTEGER, `markedNotNeed` INTEGER NOT NULL, `chainAccountName` TEXT, `status` TEXT, PRIMARY KEY(`id`, `chainId`, `accountId`, `metaId`), FOREIGN KEY(`chainId`) REFERENCES `chains`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE );
CREATE INDEX IF NOT EXISTS `index_assets_chainId` ON `assets` (`chainId`);
CREATE INDEX IF NOT EXISTS `index_assets_metaId` ON `assets` (`metaId`);
CREATE TABLE IF NOT EXISTS `token_price` (`priceId` TEXT NOT NULL, `fiatRate` TEXT, `fiatSymbol` TEXT, `recentRateChange` TEXT, PRIMARY KEY(`priceId`));
CREATE TABLE IF NOT EXISTS `phishing` (`address` TEXT NOT NULL, `name` TEXT, `type` TEXT NOT NULL, `subtype` TEXT, PRIMARY KEY(`address`, `type`));
CREATE TABLE IF NOT EXISTS `storage` (`storageKey` TEXT NOT NULL, `content` TEXT, `chainId` TEXT NOT NULL, PRIMARY KEY(`chainId`, `storageKey`));
CREATE TABLE IF NOT EXISTS `account_staking_accesses` (`chainId` TEXT NOT NULL, `chainAssetId` TEXT NOT NULL, `accountId` BLOB NOT NULL, `stashId` BLOB, `controllerId` BLOB, PRIMARY KEY(`chainId`, `chainAssetId`, `accountId`));
CREATE TABLE IF NOT EXISTS `total_reward` (`accountAddress` TEXT NOT NULL, `totalReward` TEXT NOT NULL, PRIMARY KEY(`accountAddress`));
CREATE TABLE IF NOT EXISTS `operations` (`id` TEXT NOT NULL, `address` TEXT NOT NULL, `chainId` TEXT NOT NULL, `chainAssetId` TEXT NOT NULL, `time` INTEGER NOT NULL, `status` INTEGER NOT NULL, `source` INTEGER NOT NULL, `operationType` INTEGER NOT NULL, `module` TEXT, `call` TEXT, `amount` TEXT, `sender` TEXT, `receiver` TEXT, `hash` TEXT, `fee` TEXT, `isReward` INTEGER, `era` INTEGER, `validator` TEXT, `liquidityFee` TEXT, `market` TEXT, `targetAssetId` TEXT, `targetAmount` TEXT, PRIMARY KEY(`id`, `address`, `chainId`, `chainAssetId`));
CREATE TABLE IF NOT EXISTS `chains` (`id` TEXT NOT NULL, `paraId` TEXT, `parentId` TEXT, `rank` INTEGER, `name` TEXT NOT NULL, `minSupportedVersion` TEXT, `icon` TEXT NOT NULL, `prefix` INTEGER NOT NULL, `isEthereumBased` INTEGER NOT NULL, `isTestNet` INTEGER NOT NULL, `hasCrowdloans` INTEGER NOT NULL, `supportStakingPool` INTEGER NOT NULL, `isEthereumChain` INTEGER NOT NULL, `isChainlinkProvider` INTEGER NOT NULL, `supportNft` INTEGER NOT NULL, `isUsesAppId` INTEGER NOT NULL, `identityChain` TEXT, `remoteAssetsSource` TEXT, `staking_url` TEXT, `staking_type` TEXT, `history_url` TEXT, `history_type` TEXT, `crowdloans_url` TEXT, `crowdloans_type` TEXT, PRIMARY KEY(`id`));
CREATE TABLE IF NOT EXISTS `chain_nodes` (`chainId` TEXT NOT NULL, `url` TEXT NOT NULL, `name` TEXT NOT NULL, `isActive` INTEGER NOT NULL, `isDefault` INTEGER NOT NULL, PRIMARY KEY(`chainId`, `url`), FOREIGN KEY(`chainId`) REFERENCES `chains`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE );
CREATE INDEX IF NOT EXISTS `index_chain_nodes_chainId` ON `chain_nodes` (`chainId`);
CREATE TABLE IF NOT EXISTS `chain_assets` (`id` TEXT NOT NULL, `name` TEXT, `symbol` TEXT NOT NULL, `chainId` TEXT NOT NULL, `icon` TEXT NOT NULL, `priceId` TEXT, `staking` TEXT NOT NULL, `precision` INTEGER NOT NULL, `purchaseProviders` TEXT, `isUtility` INTEGER, `type` TEXT, `currencyId` TEXT, `existentialDeposit` TEXT, `color` TEXT, `isNative` INTEGER, `ethereumType` TEXT, `priceProvider` TEXT, PRIMARY KEY(`chainId`, `id`), FOREIGN KEY(`chainId`) REFERENCES `chains`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE );
CREATE INDEX IF NOT EXISTS `index_chain_assets_chainId` ON `chain_assets` (`chainId`);
CREATE TABLE IF NOT EXISTS `favorite_chains` (`metaId` INTEGER NOT NULL, `chainId` TEXT NOT NULL, `isFavorite` INTEGER NOT NULL, PRIMARY KEY(`metaId`, `chainId`), FOREIGN KEY(`chainId`) REFERENCES `chains`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION DEFERRABLE INITIALLY DEFERRED, FOREIGN KEY(`metaId`) REFERENCES `meta_accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE );
CREATE TABLE IF NOT EXISTS `chain_runtimes` (`chainId` TEXT NOT NULL, `syncedVersion` INTEGER NOT NULL, `remoteVersion` INTEGER NOT NULL, PRIMARY KEY(`chainId`));
CREATE INDEX IF NOT EXISTS `index_chain_runtimes_chainId` ON `chain_runtimes` (`chainId`);
CREATE TABLE IF NOT EXISTS `meta_accounts` (`substratePublicKey` BLOB NOT NULL, `substrateCryptoType` TEXT NOT NULL, `substrateAccountId` BLOB NOT NULL, `ethereumPublicKey` BLOB, `ethereumAddress` BLOB, `name` TEXT NOT NULL, `isSelected` INTEGER NOT NULL, `position` INTEGER NOT NULL, `isBackedUp` INTEGER NOT NULL, `googleBackupAddress` TEXT, `initialized` INTEGER NOT NULL, `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL);
CREATE INDEX IF NOT EXISTS `index_meta_accounts_substrateAccountId` ON `meta_accounts` (`substrateAccountId`);
CREATE INDEX IF NOT EXISTS `index_meta_accounts_ethereumAddress` ON `meta_accounts` (`ethereumAddress`);
CREATE TABLE IF NOT EXISTS `chain_accounts` (`metaId` INTEGER NOT NULL, `chainId` TEXT NOT NULL, `publicKey` BLOB NOT NULL, `accountId` BLOB NOT NULL, `cryptoType` TEXT NOT NULL, `name` TEXT NOT NULL, `initialized` INTEGER NOT NULL, PRIMARY KEY(`metaId`, `chainId`), FOREIGN KEY(`chainId`) REFERENCES `chains`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION DEFERRABLE INITIALLY DEFERRED, FOREIGN KEY(`metaId`) REFERENCES `meta_accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE );
CREATE INDEX IF NOT EXISTS `index_chain_accounts_chainId` ON `chain_accounts` (`chainId`);
CREATE INDEX IF NOT EXISTS `index_chain_accounts_metaId` ON `chain_accounts` (`metaId`);
CREATE INDEX IF NOT EXISTS `index_chain_accounts_accountId` ON `chain_accounts` (`accountId`);
CREATE TABLE IF NOT EXISTS `chain_explorers` (`chainId` TEXT NOT NULL, `type` TEXT NOT NULL, `types` TEXT NOT NULL, `url` TEXT NOT NULL, PRIMARY KEY(`chainId`, `type`), FOREIGN KEY(`chainId`) REFERENCES `chains`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE );
CREATE INDEX IF NOT EXISTS `index_chain_explorers_chainId` ON `chain_explorers` (`chainId`);
CREATE TABLE IF NOT EXISTS `sora_card` (`id` TEXT NOT NULL, `accessToken` TEXT NOT NULL, `refreshToken` TEXT NOT NULL, `accessTokenExpirationTime` INTEGER NOT NULL, `kycStatus` TEXT NOT NULL, PRIMARY KEY(`id`));
CREATE TABLE IF NOT EXISTS `chain_types` (`chainId` TEXT NOT NULL, `typesConfig` TEXT NOT NULL, PRIMARY KEY(`chainId`));
CREATE TABLE IF NOT EXISTS `nomis_wallet_score` (`metaId` INTEGER NOT NULL, `score` INTEGER NOT NULL, `updated` INTEGER NOT NULL, `nativeBalanceUsd` TEXT NOT NULL, `holdTokensUsd` TEXT NOT NULL, `walletAgeInMonths` INTEGER NOT NULL, `totalTransactions` INTEGER NOT NULL, `rejectedTransactions` INTEGER NOT NULL, `avgTransactionTimeInHours` REAL NOT NULL, `maxTransactionTimeInHours` REAL NOT NULL, `minTransactionTimeInHours` REAL NOT NULL, `scoredAt` TEXT NOT NULL, PRIMARY KEY(`metaId`), FOREIGN KEY(`metaId`) REFERENCES `meta_accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE );
CREATE TABLE IF NOT EXISTS `allpools` (`tokenIdBase` TEXT NOT NULL, `tokenIdTarget` TEXT NOT NULL, `reserveBase` TEXT NOT NULL, `reserveTarget` TEXT NOT NULL, `totalIssuance` TEXT NOT NULL, `reservesAccount` TEXT NOT NULL, PRIMARY KEY(`tokenIdBase`, `tokenIdTarget`));
CREATE TABLE IF NOT EXISTS `userpools` (`userTokenIdBase` TEXT NOT NULL, `userTokenIdTarget` TEXT NOT NULL, `accountAddress` TEXT NOT NULL, `poolProvidersBalance` TEXT NOT NULL, PRIMARY KEY(`userTokenIdBase`, `userTokenIdTarget`, `accountAddress`), FOREIGN KEY(`userTokenIdBase`, `userTokenIdTarget`) REFERENCES `allpools`(`tokenIdBase`, `tokenIdTarget`) ON UPDATE NO ACTION ON DELETE CASCADE );
CREATE INDEX IF NOT EXISTS `index_userpools_accountAddress` ON `userpools` (`accountAddress`);
CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT);
INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '64c235aeb5a511d5cb4df675040540b4');

PRAGMA user_version = 71;
