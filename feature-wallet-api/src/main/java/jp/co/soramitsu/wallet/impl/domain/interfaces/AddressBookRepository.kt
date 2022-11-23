package jp.co.soramitsu.wallet.impl.domain.interfaces

import jp.co.soramitsu.coredb.model.AddressBookContact
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.coroutines.flow.Flow

interface AddressBookRepository {

    suspend fun saveAddress(name: String, address: String, chainId: String)

    fun observeAddressBook(chainId: ChainId): Flow<List<AddressBookContact>>
}
