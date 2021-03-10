package jp.co.soramitsu.feature_account_impl.presentation.account.mixin.impl

import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_account_impl.data.mappers.mapAccountToAccountModel
import jp.co.soramitsu.feature_account_impl.data.mappers.mapNetworkTypeToNetworkModel
import jp.co.soramitsu.feature_account_impl.presentation.account.mixin.api.AccountListing
import jp.co.soramitsu.feature_account_impl.presentation.account.mixin.api.AccountListingMixin
import jp.co.soramitsu.feature_account_impl.presentation.account.model.AccountModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest

private const val ICON_SIZE_IN_DP = 24

@Suppress("EXPERIMENTAL_API_USAGE")
class AccountListingProvider(
    private val accountInteractor: AccountInteractor,
    private val resourceManager: ResourceManager,
    private val addressIconGenerator: AddressIconGenerator
) : AccountListingMixin {

    override fun accountListingFlow() = getGroupedAccounts()
        .combine(selectedAccountFlow()) { groupedAccounts, selected ->
            AccountListing(groupedAccounts, selected)
        }

    override fun selectedAccountFlow() = accountInteractor.selectedAccountFlow()
        .mapLatest { transformAccount(it) }

    private fun getGroupedAccounts() = accountInteractor.groupedAccountsFlow()
        .mapLatest { transformToModels(it) }
        .flowOn(Dispatchers.Default)

    private suspend fun transformToModels(list: List<Any>): List<Any> {
        return list.map {
            when (it) {
                is Account -> transformAccount(it)
                is Node.NetworkType -> mapNetworkTypeToNetworkModel(it)
                else -> throw IllegalArgumentException()
            }
        }
    }

    private suspend fun transformAccount(account: Account): AccountModel {
        val addressModel = generateIcon(account.address)

        return mapAccountToAccountModel(account, addressModel.image, resourceManager)
    }

    private suspend fun generateIcon(address: String): AddressModel {
        return addressIconGenerator.createAddressModel(address, ICON_SIZE_IN_DP)
    }
}