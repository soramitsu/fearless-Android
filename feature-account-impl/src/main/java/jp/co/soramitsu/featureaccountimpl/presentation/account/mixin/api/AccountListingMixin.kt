package jp.co.soramitsu.featureaccountimpl.presentation.account.mixin.api

import jp.co.soramitsu.featureaccountimpl.presentation.account.model.LightMetaAccountUi
import kotlinx.coroutines.flow.Flow

interface AccountListingMixin {

    fun accountsFlow(): Flow<List<LightMetaAccountUi>>
}
