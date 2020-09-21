package jp.co.soramitsu.feature_wallet_api.di

import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository

interface WalletFeatureApi {
    fun provideWalletRepository() : WalletRepository
}