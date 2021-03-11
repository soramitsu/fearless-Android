package jp.co.soramitsu.core_db.model

import androidx.room.Embedded

class AssetWithToken(
    @Embedded
    val asset: AssetLocal,

    @Embedded
    val token: TokenLocal
)