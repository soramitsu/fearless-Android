package jp.co.soramitsu.feature_account_impl.presentation.account.mixin.impl

import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.account.AddressIconGenerator
import jp.co.soramitsu.common.account.AddressModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.asLiveData
import jp.co.soramitsu.common.utils.asMutableLiveData
import jp.co.soramitsu.common.utils.combine
import jp.co.soramitsu.common.utils.zipSimilar
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_account_impl.data.mappers.mapAccountToAccountModel
import jp.co.soramitsu.feature_account_impl.data.mappers.mapNetworkTypeToNetworkModel
import jp.co.soramitsu.feature_account_impl.presentation.account.mixin.api.AccountListing
import jp.co.soramitsu.feature_account_impl.presentation.account.mixin.api.AccountListingMixin
import jp.co.soramitsu.feature_account_impl.presentation.account.model.AccountModel

private const val ICON_SIZE_IN_DP = 24

class AccountListingProvider(
    private val accountInteractor: AccountInteractor,
    private val resourceManager: ResourceManager,
    private val addressIconGenerator: AddressIconGenerator
) : AccountListingMixin {
    override val accountListingDisposable: CompositeDisposable = CompositeDisposable()

    private val groupedAccountModelsLiveData = getGroupedAccounts()
        .asLiveData(accountListingDisposable)

    override val selectedAccountLiveData = getSelectedAccountModel()
        .asMutableLiveData(accountListingDisposable)

    override val accountListingLiveData = groupedAccountModelsLiveData
        .combine(selectedAccountLiveData) { groupedAccounts, selected ->
            AccountListing(groupedAccounts, selected)
        }

    private fun getSelectedAccountModel() = accountInteractor.observeSelectedAccount()
        .subscribeOn(Schedulers.computation())
        .flatMapSingle(::transformAccount)
        .observeOn(AndroidSchedulers.mainThread())

    private fun getGroupedAccounts() = accountInteractor.observeGroupedAccounts()
        .subscribeOn(Schedulers.computation())
        .flatMapSingle(::transformToModels)
        .observeOn(AndroidSchedulers.mainThread())

    private fun transformToModels(list: List<Any>): Single<List<Any>> {
        val singles = list.map {
            when (it) {
                is Account -> transformAccount(it)
                is Node.NetworkType -> Single.just(mapNetworkTypeToNetworkModel(it))
                else -> throw IllegalArgumentException()
            }
        }

        return singles.zipSimilar()
    }

    private fun transformAccount(account: Account): Single<AccountModel> {
        return generateIcon(account)
            .map { addressModel ->
                mapAccountToAccountModel(account, addressModel.image, resourceManager)
            }
    }

    private fun generateIcon(account: Account): Single<AddressModel> {
        return accountInteractor.getAddressId(account)
            .flatMap { addressId ->
                addressIconGenerator.createAddressModel(account.address, addressId, ICON_SIZE_IN_DP)
            }
    }
}