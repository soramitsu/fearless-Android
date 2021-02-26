package jp.co.soramitsu.feature_wallet_api.di

import jp.co.soramitsu.feature_wallet_api.domain.interfaces.TokenRepository
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.BuyTokenRegistry

interface WalletFeatureApi {

    fun provideUpdaters(): WalletUpdaters

    fun provideWalletRepository(): WalletRepository

    fun provideTokenRegistry(): BuyTokenRegistry

    fun provideTokenRepository(): TokenRepository
}