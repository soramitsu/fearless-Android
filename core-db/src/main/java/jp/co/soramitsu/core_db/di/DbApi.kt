package jp.co.soramitsu.core_db.di

import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.dao.AccountDao
import jp.co.soramitsu.core_db.dao.AccountStakingDao
import jp.co.soramitsu.core_db.dao.AssetDao
import jp.co.soramitsu.core_db.dao.ChainDao
import jp.co.soramitsu.core_db.dao.NodeDao
import jp.co.soramitsu.core_db.dao.PhishingAddressDao
import jp.co.soramitsu.core_db.dao.RuntimeDao
import jp.co.soramitsu.core_db.dao.StakingRewardDao
import jp.co.soramitsu.core_db.dao.StakingTotalRewardDao
import jp.co.soramitsu.core_db.dao.StorageDao
import jp.co.soramitsu.core_db.dao.TokenDao
import jp.co.soramitsu.core_db.dao.TransactionDao

interface DbApi {

    fun provideDatabase(): AppDatabase

    fun provideAccountDao(): AccountDao

    fun provideNodeDao(): NodeDao

    fun provideAssetDao(): AssetDao

    fun provideTransactionsDao(): TransactionDao

    fun provideRuntimeDao(): RuntimeDao

    fun providePhishingAddressDao(): PhishingAddressDao

    fun storageDao(): StorageDao

    fun tokenDao(): TokenDao

    fun accountStakingDao(): AccountStakingDao

    fun stakingRewardDao(): StakingRewardDao

    fun stakingTotalRewardDao(): StakingTotalRewardDao

    fun chainDao(): ChainDao
}
