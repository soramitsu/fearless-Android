package jp.co.soramitsu.wallet.impl.data.repository.tranfser

import jp.co.soramitsu.wallet.impl.domain.model.Transfer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.math.BigDecimal

/**
 * Stands in for the send service of a network whose sends are paused by a remote veto. It fails
 * every call with [error] before any indexer, RPC or signing work happens.
 */
class PausedTransferService(private val error: () -> Throwable) : TransferService {
    override suspend fun getTransferFee(transfer: Transfer): BigDecimal = throw error()

    override fun observeTransferFee(transfer: Transfer): Flow<BigDecimal> = flow { throw error() }

    override suspend fun transfer(transfer: Transfer): String = throw error()
}
