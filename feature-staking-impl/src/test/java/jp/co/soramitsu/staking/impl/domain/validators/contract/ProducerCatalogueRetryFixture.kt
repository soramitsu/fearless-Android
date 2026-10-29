package jp.co.soramitsu.staking.impl.domain.validators.contract

import androidx.lifecycle.Observer
import jp.co.soramitsu.common.base.models.ErrorDialogState
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.domain.validators.current.search.SearchCustomBlockProducerInteractor.BlockProducer
import jp.co.soramitsu.staking.impl.presentation.validators.change.custom.search.SearchCustomValidatorsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.mockito.kotlin.any
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.whenever
import java.util.concurrent.atomic.AtomicInteger

internal class ProducerCatalogueRetryFixture {
    val screen = ProducerSearchScreenFixture()
    val calls = AtomicInteger()
    private val dialogs = Channel<ErrorDialogState>(Channel.UNLIMITED)
    private val observer = Observer<Event<ErrorDialogState>> { dialogs.trySend(it.peekContent()) }
    private var started = false
    var load: suspend (Int) -> List<BlockProducer> = { screen.catalogue }

    suspend fun start(query: String = "al") {
        screen.data.defaults()
        whenever(screen.producer.observeSelectionContext()).thenReturn(screen.data.interactor.observeSelectionContext())
        whenever(screen.resources.getString(R.string.common_error_general_title)).thenReturn("Error")
        whenever(screen.resources.getString(R.string.common_error_general_message)).thenReturn("Please try again later")
        whenever(screen.resources.getString(R.string.common_retry)).thenReturn("Retry")
        whenever(screen.resources.getString(any(), any<Int>())).thenAnswer { "Results: ${it.arguments[1]}" }
        whenever(screen.producer.getIcon(any(), any(), any())).thenReturn(screen.icon)
        whenever(screen.staking.validatorDetailsCache).thenReturn(screen.details)
        whenever(screen.producer.getBlockProducers(any())).doSuspendableAnswer { load(calls.incrementAndGet()) }
        screen.model = SearchCustomValidatorsViewModel(
            screen.router, screen.resources, screen.data.process, screen.tokenUseCase, screen.producer, screen.staking
        )
        started = true
        screen.model.enteredQuery.value = query
        screen.model.errorDialogStateLiveData.observeForever(observer)
    }

    suspend fun dialog(): ErrorDialogState = withContext(Dispatchers.Default) {
        withTimeout(3_000) { dialogs.receive() }
    }

    suspend fun close() {
        if (started) screen.model.errorDialogStateLiveData.removeObserver(observer)
        screen.close()
        screen.data.lifecycle.destroy()
        dialogs.close()
    }
}
