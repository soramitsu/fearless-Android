package jp.co.soramitsu.staking.impl.domain.validators.contract

import jp.co.soramitsu.staking.api.domain.model.Validator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.mockito.kotlin.any
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.whenever

internal class ProducerSuspendedSelectionFixture {
    val data = ProducerSearchFixture()
    val started = CompletableDeferred<Unit>()
    val reply = CompletableDeferred<Validator>()

    suspend fun start() {
        data.defaults()
        whenever(data.validatorProvider.getValidatorWithoutElectedInfo(any(), any())).doSuspendableAnswer {
            started.complete(Unit)
            reply.await()
        }
    }

    suspend fun choose() = data.choose(data.relay.addresses[2])

    suspend fun <T> await(value: Deferred<T>): T = withContext(Dispatchers.Default) {
        withTimeout(3_000) { value.await() }
    }

    fun complete(blocked: Boolean = false) { reply.complete(data.relay.validator(2, blocked = blocked)) }
    fun close() {
        complete()
    data.lifecycle.destroy()
    }
}
