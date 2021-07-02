package jp.co.soramitsu.common.utils

import android.widget.CompoundButton
import android.widget.EditText
import android.widget.RadioGroup
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import jp.co.soramitsu.common.presentation.LoadingState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch

inline fun <T, R> Flow<List<T>>.mapList(crossinline mapper: suspend (T) -> R) = map { list ->
    list.map { item -> mapper(item) }
}

/**
 * Modifies flow so that it firstly emits [LoadingState.Loading] state.
 * Then emits each element from upstream wrapped into [LoadingState.Loaded] state.
 */
fun <T> Flow<T>.withLoading(): Flow<LoadingState<T>> {
    return map<T, LoadingState<T>> { LoadingState.Loaded(it) }
        .onStart { emit(LoadingState.Loading()) }
}

/**
 * Modifies flow so that it firstly emits [LoadingState.Loading] state for each element from upstream.
 * Then, it constructs new source via [sourceSupplier] and emits all of its items wrapped into [LoadingState.Loaded] state
 * Old suppliers are discarded as per [Flow.transformLatest] behavior
 */
fun <T, R> Flow<T>.withLoading(sourceSupplier: suspend (T) -> Flow<R>): Flow<LoadingState<R>> {
    return transformLatest { item ->
        emit(LoadingState.Loading<R>())

        val newSource = sourceSupplier(item).map { LoadingState.Loaded(it) }

        emitAll(newSource)
    }
}

/**
 * Modifies flow so that it firstly emits [LoadingState.Loading] state for each element from upstream.
 * Then, it constructs new source via [sourceSupplier] and emits all of its items wrapped into [LoadingState.Loaded] state
 * Old suppliers are discarded as per [Flow.transformLatest] behavior
 */
fun <T, R> Flow<T>.withLoadingSingle(sourceSupplier: suspend (T) -> R): Flow<LoadingState<R>> {
    return transformLatest { item ->
        emit(LoadingState.Loading<R>())

        val newSource = LoadingState.Loaded(sourceSupplier(item))

        emit(newSource)
    }
}

fun <T> Flow<T>.asLiveData(scope: CoroutineScope): LiveData<T> {
    val liveData = MutableLiveData<T>()

    onEach {
        liveData.value = it
    }.launchIn(scope)

    return liveData
}

fun <T> viewModelSharedFlow() = MutableSharedFlow<T>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

fun <T> Flow<T>.inBackground() = flowOn(Dispatchers.Default)

fun EditText.bindTo(flow: MutableStateFlow<String>, scope: CoroutineScope) {
    scope.launch {
        flow.collect { input ->
            if (text.toString() != input) {
                setText(input)
            }
        }
    }

    onTextChanged {
        scope.launch {
            flow.emit(it)
        }
    }
}

fun CompoundButton.bindTo(flow: MutableStateFlow<Boolean>, scope: CoroutineScope) {
    scope.launch {
        flow.collect { newValue ->
            if (isChecked != newValue) {
                isChecked = newValue
            }
        }
    }

    setOnCheckedChangeListener { _, newValue ->
        if (flow.value != newValue) {
            flow.value = newValue
        }
    }
}

fun RadioGroup.bindTo(flow: MutableStateFlow<Int>, scope: LifecycleCoroutineScope) {
    setOnCheckedChangeListener { _, checkedId ->
        if (flow.value != checkedId) {
            flow.value = checkedId
        }
    }

    scope.launchWhenResumed {
        flow.collect {
            if (it != checkedRadioButtonId) {
                check(it)
            }
        }
    }
}

inline fun <T> Flow<T>.observe(
    scope: LifecycleCoroutineScope,
    crossinline collector: suspend (T) -> Unit
) {
    scope.launchWhenResumed {
        collect(collector)
    }
}

fun MutableStateFlow<Boolean>.toggle() {
    value = !value
}
