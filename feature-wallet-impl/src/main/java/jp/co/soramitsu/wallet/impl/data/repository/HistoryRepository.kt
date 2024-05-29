package jp.co.soramitsu.wallet.impl.data.repository

import android.util.Log
import jp.co.soramitsu.common.data.model.CursorPage
import jp.co.soramitsu.common.utils.mapList
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.coredb.dao.OperationDao
import jp.co.soramitsu.coredb.model.OperationLocal
import jp.co.soramitsu.runtime.ext.addressOf
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import jp.co.soramitsu.shared_utils.runtime.AccountId
import jp.co.soramitsu.wallet.impl.data.historySource.HistorySourceProvider
import jp.co.soramitsu.wallet.impl.data.mappers.mapOperationLocalToOperation
import jp.co.soramitsu.wallet.impl.data.mappers.mapOperationToOperationLocalDb
import jp.co.soramitsu.wallet.impl.data.network.subquery.HistoryNotSupportedException
import jp.co.soramitsu.wallet.impl.data.storage.TransferCursorStorage
import jp.co.soramitsu.wallet.impl.domain.CurrentAccountAddressUseCase
import jp.co.soramitsu.wallet.impl.domain.interfaces.TransactionFilter
import jp.co.soramitsu.wallet.impl.domain.model.Operation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext

class HistoryRepository(
    private val historySourceProvider: HistorySourceProvider,
    private val operationDao: OperationDao,
    private val cursorStorage: TransferCursorStorage,
    private val currentAccountAddress: CurrentAccountAddressUseCase
) {
    companion object {
        private const val NO_LIMIT = -1
    }

    suspend fun getOperations(
        pageSize: Int,
        cursor: String?,
        filters: Set<TransactionFilter>,
        accountId: AccountId,
        chain: Chain,
        chainAsset: Asset
    ): CursorPage<Operation> {
        return withContext(Dispatchers.Default) {
            val historyUrl = chain.externalApi?.history?.url
            val historyType = chain.externalApi?.history?.type

            if (historyUrl == null || historyType?.isHistory() != true) {
                throw HistoryNotSupportedException()
            }
            if (historyType in listOf(
                    Chain.ExternalApi.Section.Type.GIANTSQUID,
                    Chain.ExternalApi.Section.Type.SUBSQUID
                ) && chainAsset.isUtility.not() && chain.id != soraMainChainId
            ) {
                throw HistoryNotSupportedException()
            }

            val accountAddress = chain.addressOf(accountId)

            val historySource = historySourceProvider(historyUrl, historyType)
            val operations = historySource?.getOperations(
                pageSize,
                cursor,
                filters,
                accountId,
                chain,
                chainAsset,
                accountAddress
            )
            return@withContext operations ?: CursorPage(
                null,
                emptyList()
            )
        }
    }

    suspend fun syncOperationsFirstPage(
        pageSize: Int,
        filters: Set<TransactionFilter>,
        accountId: AccountId,
        chain: Chain,
        chainAsset: Asset
    ): CursorPage<Operation> {
        val accountAddress = chain.addressOf(accountId)
        val elements: MutableList<OperationLocal> = mutableListOf()

        var page: CursorPage<Operation> = CursorPage(null, emptyList())
        var nextCursor: String? = null
        var hasNextPage = true

        while (elements.size <= pageSize.div(2) && hasNextPage) {
            page = kotlin.runCatching {
                getOperations(pageSize, cursor = nextCursor, filters, accountId, chain, chainAsset)
            }.getOrDefault(CursorPage(null, emptyList()))
            nextCursor = page.nextCursor
            hasNextPage = nextCursor != null
            elements.addAll(page.map {
                mapOperationToOperationLocalDb(
                    it,
                    OperationLocal.Source.SUBQUERY
                )
            })
        }
        operationDao.insertFromSubquery(accountAddress, chain.id, chainAsset.id, elements)

        cursorStorage.saveCursor(chain.id, chainAsset.id, accountId, page.nextCursor)
        return page
    }

    fun operationsFirstPageFlow(
        accountId: AccountId,
        chain: Chain,
        chainAsset: Asset
    ): Flow<CursorPage<Operation>> {
        val accountAddress = chain.addressOf(accountId)
        return operationDao.observe(accountAddress, chain.id, chainAsset.id)
            .mapList {
                mapOperationLocalToOperation(it, chainAsset, chain)
            }
            .mapLatest { operations ->
                val cursor = cursorStorage.awaitCursor(chain.id, chainAsset.id, accountId)

                CursorPage(cursor, operations)
            }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getOperationAddressWithChainIdFlow(chainId: ChainId, limit: Int?): Flow<Set<String>> {
        return flow { emit(currentAccountAddress.invoke(chainId)) }
            .mapNotNull { it }
            .flatMapMerge { accountAddress ->
                operationDao.observeOperationAddresses(chainId, accountAddress, limit ?: NO_LIMIT)
            }.map { addresses ->
                addresses.toSet()
            }
    }
}
