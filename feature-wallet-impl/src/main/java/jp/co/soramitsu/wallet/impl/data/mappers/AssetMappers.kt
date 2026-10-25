package jp.co.soramitsu.wallet.impl.data.mappers

import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.Token
import jp.co.soramitsu.wallet.impl.presentation.model.AssetModel
import jp.co.soramitsu.wallet.impl.presentation.model.TokenModel

/** Retains canonical quote identity and amount availability in the presentation model. */
fun mapTokenToTokenModel(token: Token): TokenModel = TokenModel(token)

/** Retains canonical account, quantity availability and captured quote identity. */
fun mapAssetToAssetModel(asset: Asset): AssetModel = AssetModel(asset)
