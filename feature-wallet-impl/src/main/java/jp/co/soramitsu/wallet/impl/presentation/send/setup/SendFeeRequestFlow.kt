package jp.co.soramitsu.wallet.impl.presentation.send.setup

import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.wallet.impl.domain.model.Transfer
import java.math.BigDecimal
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retry

/** A failed estimate belongs to one edit; it must never terminate future amount/recipient requests. */
object SendFeeRequestFlow {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(requests: Flow<Transfer?>, load: suspend (Transfer) -> Flow<BigDecimal>, failed: suspend (Throwable) -> Unit): Flow<BigDecimal?> =
        requests.flatMapLatest { request ->
            if (request == null || request.chainAsset.chainId in nativeChains && request.amount.signum() <= 0) flowOf(null)
            else flow { emitAll(load(request)) }.retry(3).map<BigDecimal, BigDecimal?> { it }.catch { failed(it); emit(null) }
        }

    private val nativeChains = setOf(UniversalWalletRegistry.bitcoinMainnet.id, UniversalWalletRegistry.bitcoinTestnet.id,
        UniversalWalletRegistry.solanaMainnet.id, UniversalWalletRegistry.solanaDevnet.id)
}
