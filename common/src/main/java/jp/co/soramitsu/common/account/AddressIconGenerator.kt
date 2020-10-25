package jp.co.soramitsu.common.account

import io.reactivex.Single
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.fearless_utils.icon.IconGenerator

class AddressIconGenerator(
    private val iconGenerator: IconGenerator,
    private val resourceManager: ResourceManager
) {
    fun createAddressModel(accountAddress: String, addressId: ByteArray, sizeInDp: Int): Single<AddressModel> {
        return Single.fromCallable {
            val sizeInPx = resourceManager.measureInPx(sizeInDp)
            val icon = iconGenerator.getSvgImage(addressId, sizeInPx)
            AddressModel(accountAddress, icon)
        }
    }
}