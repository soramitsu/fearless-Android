package jp.co.soramitsu.common.address

import android.graphics.drawable.PictureDrawable
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.fearless_utils.exceptions.AddressFormatException
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.icon.IconGenerator
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

// TODO ethereum address icon generation
interface AddressIconGenerator {

    companion object {

        const val SIZE_SMALL = 18
        const val SIZE_MEDIUM = 24
        const val SIZE_BIG = 32
    }

    suspend fun createAddressIcon(accountId: AccountId, sizeInDp: Int): PictureDrawable
}

@Throws(AddressFormatException::class)
suspend fun AddressIconGenerator.createAddressModel(accountAddress: String, sizeInDp: Int, accountName: String? = null): AddressModel {
    val icon = createAddressIcon(accountAddress, sizeInDp)

    return AddressModel(accountAddress, icon, accountName)
}

@Throws(AddressFormatException::class)
suspend fun AddressIconGenerator.createAddressIcon(accountAddress: String, sizeInDp: Int) = withContext(Dispatchers.Default) {
    val addressId = accountAddress.toAccountId()

    createAddressIcon(addressId, sizeInDp)
}

class CachingAddressIconGenerator(
    private val delegate: AddressIconGenerator
) : AddressIconGenerator {

    val cache = ConcurrentHashMap<String, PictureDrawable>()

    override suspend fun createAddressIcon(accountId: AccountId, sizeInDp: Int): PictureDrawable = withContext(Dispatchers.Default) {
        val key = "${accountId.toHexString()}:$sizeInDp"

        cache.getOrPut(key) {
            delegate.createAddressIcon(accountId, sizeInDp)
        }
    }
}

class StatelessAddressIconGenerator(
    private val iconGenerator: IconGenerator,
    private val resourceManager: ResourceManager
) : AddressIconGenerator {


    override suspend fun createAddressIcon(accountId: AccountId, sizeInDp: Int) = withContext(Dispatchers.Default) {
        val sizeInPx = resourceManager.measureInPx(sizeInDp)

        iconGenerator.getSvgImage(accountId, sizeInPx)
    }
}
