package jp.co.soramitsu.common.address

import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.fearless_utils.exceptions.AddressFormatException
import jp.co.soramitsu.fearless_utils.icon.IconGenerator
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// TODO ethereum address icon generation
class AddressIconGenerator(
    private val iconGenerator: IconGenerator,
    private val resourceManager: ResourceManager
) {

    companion object {
        const val SIZE_SMALL = 18
        const val SIZE_MEDIUM = 24
        const val SIZE_BIG = 32
    }

    @Throws(AddressFormatException::class)
    suspend fun createAddressModel(accountAddress: String, sizeInDp: Int, accountName: String? = null): AddressModel {
        val icon = createAddressIcon(accountAddress, sizeInDp)

        return AddressModel(accountAddress, icon, accountName)
    }

    @Throws(AddressFormatException::class)
    suspend fun createAddressIcon(accountAddress: String, sizeInDp: Int) = withContext(Dispatchers.Default) {
        val addressId = accountAddress.toAccountId()

        createAddressIcon(addressId, sizeInDp)
    }

    suspend fun createAddressIcon(accountId: AccountId, sizeInDp: Int) = withContext(Dispatchers.Default) {
        val sizeInPx = resourceManager.measureInPx(sizeInDp)

        iconGenerator.getSvgImage(accountId, sizeInPx)
    }
}
