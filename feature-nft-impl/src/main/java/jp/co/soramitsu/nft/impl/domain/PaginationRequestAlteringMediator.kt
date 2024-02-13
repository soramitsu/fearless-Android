package jp.co.soramitsu.nft.impl.domain

import jp.co.soramitsu.nft.data.pagination.PaginationRequest
import jp.co.soramitsu.nft.domain.models.NFTCollectionResult
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

class PaginationRequestAlteringMediator @Inject constructor() {

    fun interface AlterRequestFlowCallback {
        fun alterTo(flag: Int)
    }

    class Holder(
        val flag: Int,
        val builder: (Flow<PaginationRequest>, AlterRequestFlowCallback) -> Flow<Pair<NFTCollectionResult, PaginationRequest>>
    )

    operator fun invoke(
        paginationRequestFlow: Flow<PaginationRequest>,
        vararg holders: Holder
    ): Flow<Pair<NFTCollectionResult, PaginationRequest>> {
        return channelFlow {
            val mutableSharedFlow = MutableSharedFlow<Unit>(
                replay = 1,
                onBufferOverflow = BufferOverflow.DROP_OLDEST
            )

            val nonBlockingSemaphore = AtomicInteger(holders.first().flag)

            fun <T> Flow<T>.withNonBlockingLock(unlockOn: Int) = onStart {
                mutableSharedFlow.tryEmit(Unit)
            }.combine(mutableSharedFlow) { value, _ ->
                return@combine value
            }.filter {
                nonBlockingSemaphore.get() == unlockOn
            }

            for (holder in holders) {
                launch {
                    holder.builder.invoke(
                        paginationRequestFlow.withNonBlockingLock(holder.flag).onEach {
                            println("This is checkpoint: paginationRequest with flag - ${holder.flag}, request - $it")
                        }
                    ) {
                        nonBlockingSemaphore.set(it)
                        mutableSharedFlow.tryEmit(Unit)
                    }.collect {
                        send(it)
                    }
                }
            }
        }
    }

}