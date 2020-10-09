package jp.co.soramitsu.feature_wallet_impl.presentation.common

import io.reactivex.Single
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.fearless_utils.icon.IconGenerator
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor

class AddressIconGenerator(
    private val interactor: WalletInteractor,
    private val iconGenerator: IconGenerator,
    private val resourceManager: ResourceManager
) {
    fun createAddressIcon(accountAddress: String, sizeInDp: Int) : Single<AddressModel> {
        val sizeInPx = resourceManager.measureInPx(sizeInDp)

        return interactor.getAddressId(accountAddress)
            .map { iconGenerator.getSvgImage(it, sizeInPx) }
            .map { AddressModel(accountAddress, it) }
    }
}