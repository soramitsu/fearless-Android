package jp.co.soramitsu.feature_wallet_api.di

import jp.co.soramitsu.feature_wallet_api.data.cache.AssetCache
import jp.co.soramitsu.feature_wallet_api.domain.AssetUseCase
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.TokenRepository
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletConstants
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.BuyTokenRegistry
import jp.co.soramitsu.feature_wallet_api.presentation.mixin.FeeLoaderMixin

interface WalletFeatureApi {

    fun assetUseCase(): AssetUseCase

    fun provideUpdaters(): WalletUpdaters

    fun provideWalletRepository(): WalletRepository

    fun provideTokenRegistry(): BuyTokenRegistry

    fun provideTokenRepository(): TokenRepository

    fun provideAssetCache(): AssetCache

    fun provideWallConstants(): WalletConstants

    fun feeLoaderMixin() : FeeLoaderMixin.Presentation
}
