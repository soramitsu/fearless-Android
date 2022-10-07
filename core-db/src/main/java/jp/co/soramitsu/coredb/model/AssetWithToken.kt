package jp.co.soramitsu.coredb.model

import androidx.room.Embedded

class AssetWithToken(
    @Embedded
    val asset: AssetLocal,

    @Embedded
    val token: TokenPriceLocal?
)
