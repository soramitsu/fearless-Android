package jp.co.soramitsu.core_db.model

import androidx.room.Embedded

class AccountWithNode(
    @Embedded val accountLocal: AccountLocal,
    @Embedded val nodeLocal: NodeLocal
)