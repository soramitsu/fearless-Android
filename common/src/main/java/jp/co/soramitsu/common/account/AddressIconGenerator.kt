package jp.co.soramitsu.common.account

import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.fearless_utils.encrypt.Base58
import jp.co.soramitsu.fearless_utils.exceptions.AddressFormatException
import jp.co.soramitsu.fearless_utils.icon.IconGenerator
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.jvm.Throws

class AddressIconGenerator(
    private val iconGenerator: IconGenerator,
    private val sS58Encoder: SS58Encoder,
    private val resourceManager: ResourceManager
) {

    @Throws(AddressFormatException::class)
    suspend fun createAddressModel(accountAddress: String, sizeInDp: Int): AddressModel {
        val icon = createAddressIcon(accountAddress, sizeInDp)

        return AddressModel(accountAddress, icon)
    }

    @Throws(AddressFormatException::class)
    suspend fun createAddressIcon(accountAddress: String, sizeInDp: Int) = withContext(Dispatchers.Default) {
        val addressId = sS58Encoder.decode(accountAddress)
        val sizeInPx = resourceManager.measureInPx(sizeInDp)

        iconGenerator.getSvgImage(addressId, sizeInPx)
    }
}