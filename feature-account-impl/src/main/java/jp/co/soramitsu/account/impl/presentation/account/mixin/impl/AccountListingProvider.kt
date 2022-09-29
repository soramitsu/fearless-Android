package jp.co.soramitsu.account.impl.presentation.account.mixin.impl

import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.LightMetaAccount
import jp.co.soramitsu.account.impl.presentation.account.mixin.api.AccountListingMixin
import jp.co.soramitsu.account.impl.presentation.account.model.LightMetaAccountUi
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.utils.IgnoredOnEquals
import jp.co.soramitsu.common.utils.mapList

@Suppress("EXPERIMENTAL_API_USAGE")
class AccountListingProvider(
    private val accountInteractor: AccountInteractor,
    private val addressIconGenerator: AddressIconGenerator
) : AccountListingMixin {

    override fun accountsFlow(metaAccountIconDpSize: Int?) = accountInteractor.lightMetaAccountsFlow()
        .mapList {
            mapMetaAccountToUi(
                it,
                addressIconGenerator,
                metaAccountIconDpSize
            )
        }

    private suspend fun mapMetaAccountToUi(
        metaAccount: LightMetaAccount,
        iconGenerator: AddressIconGenerator,
        iconSizeDp: Int?
    ) = with(metaAccount) {
        val icon = iconGenerator.createAddressIcon(
            metaAccount.substrateAccountId,
            iconSizeDp ?: AddressIconGenerator.SIZE_MEDIUM
        )

        LightMetaAccountUi(
            id = id,
            name = name,
            isSelected = isSelected,
            picture = IgnoredOnEquals(icon)
        )
    }
}
