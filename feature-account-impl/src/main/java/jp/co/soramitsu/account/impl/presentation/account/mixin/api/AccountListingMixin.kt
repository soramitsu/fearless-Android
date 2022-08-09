package jp.co.soramitsu.account.impl.presentation.account.mixin.api

import jp.co.soramitsu.account.impl.presentation.account.model.LightMetaAccountUi
import kotlinx.coroutines.flow.Flow

interface AccountListingMixin {

    fun accountsFlow(): Flow<List<LightMetaAccountUi>>
}
