package jp.co.soramitsu.wallet.impl.domain.interfaces

import jp.co.soramitsu.coredb.model.AddressBookContact
import kotlinx.coroutines.flow.Flow

interface AddressBookRepository {

    suspend fun saveAddress(name: String, address: String, chainId: String)

    fun observeAddressBook(): Flow<List<AddressBookContact>>
}
