package jp.co.soramitsu.common.account

import io.reactivex.Single
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.fearless_utils.icon.IconGenerator
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder

class AddressIconGenerator(
    private val iconGenerator: IconGenerator,
    private val sS58Encoder: SS58Encoder,
    private val resourceManager: ResourceManager
) {
    fun createAddressModel(accountAddress: String, sizeInDp: Int): Single<AddressModel> {
        return Single.fromCallable {
            val addressId = sS58Encoder.decode(accountAddress)
            val sizeInPx = resourceManager.measureInPx(sizeInDp)
            val icon = iconGenerator.getSvgImage(addressId, sizeInPx)
            AddressModel(accountAddress, icon)
        }
    }
}