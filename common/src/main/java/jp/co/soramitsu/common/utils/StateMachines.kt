package jp.co.soramitsu.common.utils

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

data class Transition<STATE, SIDE_EFFECT>(val newState: STATE, val sideEffects: List<SIDE_EFFECT>)

infix fun <STATE, SIDE_EFFECT> STATE.with(sideEffect: SIDE_EFFECT) = Transition(this, listOf(sideEffect))

abstract class StateMachine<STATE, EVENT, SIDE_EFFECT>(initialState: STATE) {

    private val _currentState = MutableStateFlow(initialState)
    private val _sideEffects = MutableSharedFlow<SIDE_EFFECT>(replay = 1)

    val currentState: Flow<STATE> = _currentState

    val sideEffects: Flow<SIDE_EFFECT> = _sideEffects

    fun transition(event: EVENT) {
        _currentState.value = performTransition(_currentState.value, event)
    }

    protected abstract fun performTransition(state: STATE, event: EVENT): STATE

    protected fun sideEffect(sideEffect: SIDE_EFFECT) {
        _sideEffects.tryEmit(sideEffect)
    }
}
