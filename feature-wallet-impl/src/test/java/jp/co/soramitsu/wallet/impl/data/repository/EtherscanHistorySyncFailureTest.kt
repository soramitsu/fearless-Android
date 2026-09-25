package jp.co.soramitsu.wallet.impl.data.repository

import jp.co.soramitsu.common.data.model.CursorPage
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.coredb.dao.OperationDao
import jp.co.soramitsu.runtime.multiNetwork.chain.model.BSCChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.testshared.whenever
import jp.co.soramitsu.wallet.impl.data.historySource.HistorySource
import jp.co.soramitsu.wallet.impl.data.historySource.HistorySourceProvider
import jp.co.soramitsu.wallet.impl.data.storage.TransferCursorStorage
import jp.co.soramitsu.wallet.impl.domain.CurrentAccountAddressUseCase
import jp.co.soramitsu.wallet.impl.domain.interfaces.TransactionFilter
import jp.co.soramitsu.wallet.impl.domain.model.Operation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions

class EtherscanHistorySyncFailureTest {
    @Test
    fun `failed provider fetch preserves cached operations and cursor for retry`() {
        val provider = mock(HistorySourceProvider::class.java)
        val dao = mock(OperationDao::class.java)
        val cursor = mock(TransferCursorStorage::class.java)
        val chain = mock(Chain::class.java)
        val asset = mock(Asset::class.java)
        whenever(chain.id).thenReturn(BSCChainId)
        whenever(chain.externalApi).thenReturn(
            Chain.ExternalApi(
                staking = null,
                history = Chain.ExternalApi.Section(Chain.ExternalApi.Section.Type.ETHERSCAN, HISTORY_URL),
                crowdloans = null
            )
        )
        whenever(asset.id).thenReturn("bnb")
        whenever(provider(HISTORY_URL, Chain.ExternalApi.Section.Type.ETHERSCAN)).thenReturn(
            object : HistorySource {
                override suspend fun getOperations(
                    pageSize: Int,
                    cursor: String?,
                    filters: Set<TransactionFilter>,
                    accountId: ByteArray,
                    chain: Chain,
                    chainAsset: Asset,
                    accountAddress: String
                ): CursorPage<Operation> = throw IllegalStateException("provider unavailable")
            }
        )
        val repository = HistoryRepository(
            provider,
            dao,
            cursor,
            mock(CurrentAccountAddressUseCase::class.java)
        )

        val result = runCatching {
            runBlocking {
                repository.syncOperationsFirstPage(
                    pageSize = 25,
                    filters = setOf(TransactionFilter.TRANSFER),
                    accountId = byteArrayOf(1, 2),
                    chain = chain,
                    chainAsset = asset,
                    accountAddress = "0x0102"
                )
            }
        }

        assertTrue(result.exceptionOrNull() is IllegalStateException)
        verifyNoInteractions(dao, cursor)
    }

    private companion object {
        const val HISTORY_URL = "https://api.etherscan.io/v2/api"
    }
}
