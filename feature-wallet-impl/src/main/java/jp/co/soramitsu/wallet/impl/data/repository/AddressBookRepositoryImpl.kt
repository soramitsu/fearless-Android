package jp.co.soramitsu.wallet.impl.data.repository

import jp.co.soramitsu.coredb.dao.AddressBookDao
import jp.co.soramitsu.coredb.model.AddressBookContact
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.wallet.impl.domain.interfaces.AddressBookRepository
import kotlinx.coroutines.flow.Flow

class AddressBookRepositoryImpl(
    private val addressBookDao: AddressBookDao
) : AddressBookRepository {
    override suspend fun saveAddress(name: String, address: String, chainId: String) {
        addressBookDao.insert(AddressBookContact(address, name, chainId))
    }

    override fun observeAddressBook(chainId: ChainId): Flow<List<AddressBookContact>> = addressBookDao.observeAddressBook(chainId)
}
