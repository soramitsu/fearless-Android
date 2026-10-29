package jp.co.soramitsu.staking.impl.domain.validators.contract

import android.graphics.drawable.PictureDrawable
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.validators.current.search.SearchCustomBlockProducerInteractor
import jp.co.soramitsu.staking.impl.domain.validators.current.search.SearchCustomBlockProducerInteractor.BlockProducer
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.validators.change.custom.search.SearchBlockProducerModel
import jp.co.soramitsu.staking.impl.presentation.validators.change.custom.search.SearchBlockProducersState
import jp.co.soramitsu.staking.impl.presentation.validators.change.custom.search.SearchCustomValidatorsViewModel
import jp.co.soramitsu.staking.impl.presentation.validators.parcel.ValidatorDetailsParcelModel
import jp.co.soramitsu.wallet.impl.domain.TokenUseCase
import jp.co.soramitsu.wallet.impl.domain.model.Token
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.rules.ExternalResource
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.CopyOnWriteArrayList

internal class ProducerScreenArchitectureRule : ExternalResource() {
    override fun before() {
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) { runnable.run() }
            override fun postToMainThread(runnable: Runnable) { runnable.run() }
            override fun isMainThread(): Boolean = true
        })
    }
    override fun after() { ArchTaskExecutor.getInstance().setDelegate(null) }
}

internal class ProducerSearchScreenFixture(parachain: Boolean = false) {
    val data = ProducerSearchFixture(parachain)
    val router = mock<StakingRouter> { on { currentStackEntryLifecycle } doReturn data.lifecycle }
    val resources = mock<ResourceManager>()
    val producer = mock<SearchCustomBlockProducerInteractor>()
    val staking = mock<StakingInteractor>()
    val tokens = MutableStateFlow(Token(data.asset, null, null, null))
    val tokenUseCase = mock<TokenUseCase> { on { currentTokenFlow() } doReturn tokens }
    val details = MutableStateFlow<Map<String, ValidatorDetailsParcelModel>>(emptyMap())
    val icon = mock<PictureDrawable>()
    val lookups = CopyOnWriteArrayList<String>()
    val lookupStarted = Channel<String>(Channel.UNLIMITED)
    val lookupDelivered = Channel<String>(Channel.UNLIMITED)
    val addresses = if (parachain) data.para.hex else data.relay.addresses
    val catalogue = listOf(
        BlockProducer("Alice", addresses[0], "12.5%"),
        BlockProducer("ALPINE", addresses[1], "8%"),
        BlockProducer("Charlie", addresses[2], "0%")
    )
    var remoteResult: BlockProducer? = null
    var remoteHandler: suspend (String) -> BlockProducer? = { remoteResult }
    lateinit var model: SearchCustomValidatorsViewModel

    suspend fun start() {
        data.defaults()
        whenever(producer.observeSelectionContext()).thenReturn(data.interactor.observeSelectionContext())
        whenever(resources.getString(any(), any<Int>())).thenAnswer { "Results: ${it.arguments[1]}" }
        whenever(resources.getString(any())).thenReturn("Validator blocked")
        whenever(producer.getBlockProducers(any())).thenReturn(catalogue)
        whenever(producer.getIcon(any(), any(), any())).thenReturn(icon)
        whenever(producer.getBlockProducerByAddress(any())).doSuspendableAnswer {
            val query = it.getArgument<String>(0)
            lookups += query
            lookupStarted.send(query)
            val value = remoteHandler(query)
            lookupDelivered.send(query)
            value
        }
        whenever(producer.blockProducerSelected(any(), any(), any())).thenReturn(Result.success(Unit))
        whenever(staking.validatorDetailsCache).thenReturn(details)
        model = SearchCustomValidatorsViewModel(router, resources, data.process, tokenUseCase, producer, staking)
        await { it is SearchBlockProducersState.NoInput }
    }

    suspend fun await(predicate: (SearchBlockProducersState) -> Boolean): SearchBlockProducersState =
        withContext(Dispatchers.Default) {
            withTimeout(3_000) { model.screenState.first(predicate) }
        }

    suspend fun search(text: String): SearchBlockProducersState.Success {
        model.enteredQuery.value = text
        return await { it is SearchBlockProducersState.Success } as SearchBlockProducersState.Success
    }

    suspend fun lookup(): String = withContext(Dispatchers.Default) {
        withTimeout(3_000) { lookupDelivered.receive() }
    }

    fun row(index: Int = 0) = SearchBlockProducerModel(
        catalogue[index].name, addresses[index], false, catalogue[index].rewardsPercent, icon
    )

    suspend fun close() {
        if (::model.isInitialized) model.coroutineContext[Job]?.cancelAndJoin()
        lookupDelivered.close()
        lookupStarted.close()
    }
}
