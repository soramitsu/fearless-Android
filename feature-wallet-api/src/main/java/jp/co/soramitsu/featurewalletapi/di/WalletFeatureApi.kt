package jp.co.soramitsu.featurewalletapi.di

import jp.co.soramitsu.core.updater.UpdateSystem
import jp.co.soramitsu.featurewalletapi.data.cache.AssetCache
import jp.co.soramitsu.featurewalletapi.domain.interfaces.TokenRepository
import jp.co.soramitsu.featurewalletapi.domain.interfaces.WalletConstants
import jp.co.soramitsu.featurewalletapi.domain.interfaces.WalletRepository
import jp.co.soramitsu.featurewalletapi.domain.model.BuyTokenRegistry

interface WalletFeatureApi {

    fun provideWalletRepository(): WalletRepository

    fun provideTokenRegistry(): BuyTokenRegistry

    fun provideTokenRepository(): TokenRepository

    fun provideAssetCache(): AssetCache

    fun provideWallConstants(): WalletConstants

    @Wallet
    fun provideWalletUpdateSystem(): UpdateSystem
}
