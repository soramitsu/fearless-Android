package jp.co.soramitsu.feature_wallet_impl.data.storage

import jp.co.soramitsu.feature_wallet_api.domain.model.Operation

class TransferCursorStorage {

    private val storage = mutableMapOf<String, MutableList<Operation>>()

    fun getOperations(
        chain: String,
        account: String,
    ): List<Operation> {
        val key = chain + account
        return storage[key].orEmpty()
    }

    fun saveOperations(
        chain: String,
        account: String,
        operations: List<Operation>
    ) {
        val key = chain + account
        val list = storage.getOrPut(key) { mutableListOf() }
        val listIds = list.map { it.id }
        operations.forEach {
            if (it.id !in listIds) {
                list.add(0, it)
            }
        }
    }

    fun removeOperations(ids: List<String>) {
        storage.forEach { entry ->
            entry.value.removeAll {
                it.id in ids
            }
        }
    }
}
