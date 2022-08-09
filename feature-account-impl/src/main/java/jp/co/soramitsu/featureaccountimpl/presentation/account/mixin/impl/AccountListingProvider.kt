package jp.co.soramitsu.featureaccountimpl.presentation.account.mixin.impl

import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.utils.IgnoredOnEquals
import jp.co.soramitsu.common.utils.mapList
import jp.co.soramitsu.featureaccountapi.domain.interfaces.AccountInteractor
import jp.co.soramitsu.featureaccountapi.domain.model.LightMetaAccount
import jp.co.soramitsu.featureaccountimpl.presentation.account.mixin.api.AccountListingMixin
import jp.co.soramitsu.featureaccountimpl.presentation.account.model.LightMetaAccountUi

@Suppress("EXPERIMENTAL_API_USAGE")
class AccountListingProvider(
    private val accountInteractor: AccountInteractor,
    private val addressIconGenerator: AddressIconGenerator
) : AccountListingMixin {

    override fun accountsFlow() = accountInteractor.lightMetaAccountsFlow()
        .mapList { mapMetaAccountToUi(it, addressIconGenerator) }

    private suspend fun mapMetaAccountToUi(
        metaAccount: LightMetaAccount,
        iconGenerator: AddressIconGenerator
    ) = with(metaAccount) {
        val icon = iconGenerator.createAddressIcon(metaAccount.substrateAccountId, AddressIconGenerator.SIZE_MEDIUM)

        LightMetaAccountUi(
            id = id,
            name = name,
            isSelected = isSelected,
            picture = IgnoredOnEquals(icon)
        )
    }
}
