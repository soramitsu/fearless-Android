package jp.co.soramitsu.fearless_utils.wsrpc.socket

import jp.co.soramitsu.fearless_utils.wsrpc.state.SocketStateMachine.State

typealias StateObserver = (State) -> Unit

class ObservableState(initialState: State) {

    @Volatile
    private var state: State = initialState

    private val observers = mutableListOf<StateObserver>()

    @Synchronized
    fun setState(newState: State) {
        state = newState

        observers.forEach { it.invoke(newState) }
    }

    @Synchronized
    fun getState() = state

    @Synchronized
    fun addObserver(observer: StateObserver, notifyInitial: Boolean = true) {
        observers.add(observer)

        if (notifyInitial) {
            observer.invoke(state)
        }
    }

    @Synchronized
    fun removeObserver(observer: StateObserver) {
        observers.remove(observer)
    }
}
