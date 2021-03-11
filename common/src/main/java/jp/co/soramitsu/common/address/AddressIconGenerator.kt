package jp.co.soramitsu.common.address

import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.fearless_utils.exceptions.AddressFormatException
import jp.co.soramitsu.fearless_utils.icon.IconGenerator
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AddressIconGenerator(
    private val iconGenerator: IconGenerator,
    private val resourceManager: ResourceManager
) {

    @Throws(AddressFormatException::class)
    suspend fun createAddressModel(accountAddress: String, sizeInDp: Int, accountName: String? = null): AddressModel {
        val icon = createAddressIcon(accountAddress, sizeInDp)

        return AddressModel(accountAddress, icon, accountName)
    }

    @Throws(AddressFormatException::class)
    suspend fun createAddressIcon(accountAddress: String, sizeInDp: Int) = withContext(Dispatchers.Default) {
        val addressId = accountAddress.toAccountId()
        val sizeInPx = resourceManager.measureInPx(sizeInDp)

        iconGenerator.getSvgImage(addressId, sizeInPx)
    }
}