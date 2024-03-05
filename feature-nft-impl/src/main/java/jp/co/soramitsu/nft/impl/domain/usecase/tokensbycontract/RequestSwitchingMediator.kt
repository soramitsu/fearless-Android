package jp.co.soramitsu.nft.impl.domain.usecase.tokensbycontract

import jp.co.soramitsu.nft.data.pagination.PaginationRequest
import jp.co.soramitsu.nft.impl.data.DEFAULT_PAGE_SIZE
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RequestSwitchingMediator @Inject constructor() {

    interface SwitchFlowHandle {
        fun switchToFlowWithFlag(flag: Int)
    }

    class Node<T>(
        val flag: Int,
        val factory: (Flow<PaginationRequest>, SwitchFlowHandle) -> Flow<T>
    )

    operator fun <T> invoke(requestFlow: Flow<PaginationRequest>, vararg nodes: Node<T>): Flow<T> {
        return channelFlow {
            val triggerFlow = createTriggerFlow()
            val nonBlockingSemaphore = AtomicInteger(nodes.first().flag)

            val handle = object : SwitchFlowHandle {
                override fun switchToFlowWithFlag(flag: Int) {
                    val currentFlag = nonBlockingSemaphore.get()
                    nonBlockingSemaphore.set(flag)

                    triggerFlow.tryEmit(
                        if (currentFlag < flag) {
                            PaginationRequest.Start(DEFAULT_PAGE_SIZE)
                        } else {
                            PaginationRequest.ProceedFromLastPage
                        }
                    )
                }
            }

            for (holder in nodes) {
                launch {
                    holder.factory(
                        requestFlow.withNonBlockingLock(
                            triggerFlow = triggerFlow,
                            unlockOnFlag = holder.flag,
                            semaphore = nonBlockingSemaphore
                        ),
                        handle
                    ).collect { value -> send(value) }
                }
            }
        }
    }

    private fun createTriggerFlow() = MutableSharedFlow<PaginationRequest>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private fun <T> Flow<T>.withNonBlockingLock(
        triggerFlow: MutableSharedFlow<T>,
        unlockOnFlag: Int,
        semaphore: AtomicInteger
    ) = merge(this, triggerFlow)
        .filter { semaphore.get() == unlockOnFlag }
}
