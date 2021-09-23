package jp.co.soramitsu.core_db.model

import androidx.room.Embedded

class AssetWithToken(
    @Embedded(prefix = "asset_")
    val asset: AssetLocal,

    @Embedded(prefix = "token_")
    val token: TokenLocal
)
