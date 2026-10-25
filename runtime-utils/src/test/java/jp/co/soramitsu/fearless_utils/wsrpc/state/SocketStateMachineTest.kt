package jp.co.soramitsu.fearless_utils.wsrpc.state

import jp.co.soramitsu.fearless_utils.common.assertInstance
import jp.co.soramitsu.fearless_utils.wsrpc.request.DeliveryType
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.createFakeChange
import jp.co.soramitsu.fearless_utils.wsrpc.response.RpcResponse
import jp.co.soramitsu.fearless_utils.wsrpc.state.SocketStateMachine.Event
import jp.co.soramitsu.fearless_utils.wsrpc.state.SocketStateMachine.SideEffect
import jp.co.soramitsu.fearless_utils.wsrpc.state.SocketStateMachine.SideEffect.Connect
import jp.co.soramitsu.fearless_utils.wsrpc.state.SocketStateMachine.SideEffect.Disconnect
import jp.co.soramitsu.fearless_utils.wsrpc.state.SocketStateMachine.SideEffect.RespondSendablesError
import jp.co.soramitsu.fearless_utils.wsrpc.state.SocketStateMachine.SideEffect.RespondToSubscription
import jp.co.soramitsu.fearless_utils.wsrpc.state.SocketStateMachine.SideEffect.ResponseToSendable
import jp.co.soramitsu.fearless_utils.wsrpc.state.SocketStateMachine.SideEffect.ScheduleReconnect
import jp.co.soramitsu.fearless_utils.wsrpc.state.SocketStateMachine.SideEffect.SendSendables
import jp.co.soramitsu.fearless_utils.wsrpc.state.SocketStateMachine.SideEffect.Unsubscribe
import jp.co.soramitsu.fearless_utils.wsrpc.state.SocketStateMachine.State
import jp.co.soramitsu.fearless_utils.wsrpc.subscription.response.SubscriptionChange
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnitRunner

private const val URL = "TEST"
private const val SWITCH_URL = "SWITCHED"

val emptyConnectedState = State.Connected(
    url = URL,
    toResendOnReconnect = emptySet(),
    waitingForResponse = emptySet(),
    subscriptions = emptySet(),
    unknownSubscriptionResponses = emptyMap(),
)

class TestSendable(override val id: Int, override val deliveryType: DeliveryType) :
    SocketStateMachine.Sendable

class TestSubscription(override val id: String, override val initiatorId: Int) :
    SocketStateMachine.Subscription

fun singleTestSendable(deliveryType: DeliveryType) = TestSendable(0, deliveryType)

fun singleTestSubscription() = TestSubscription("0", 0)

private val TEST_RESPONSE = RpcResponse("", null, 0, null)

private val TEST_EXCEPTION = Exception()
private val CONNECT_SIDE_EFFECT = Connect(URL)

private val TEST_RESPONSE_EVENT = Event.SendableResponse(TEST_RESPONSE)

@RunWith(MockitoJUnitRunner::class)
class SocketStateMachineTest {

    private val sideEffectLog = mutableListOf<SideEffect>()

    @Mock
    private lateinit var testSubscriptionChange: SubscriptionChange

    @Before
    fun before() {
        `when`(testSubscriptionChange.subscriptionId).thenReturn("0")

        sideEffectLog.clear()
    }

    @Test
    fun `should be disconnected from beginning`() {
        val state = SocketStateMachine.initialState()

        assertEquals(state, State.Disconnected)
    }

    @Test
    fun `should start`() {
        val state = moveToStart()

        assertEquals(state, State.Connecting(attempt = 0, pendingSendables = emptySet(), url = URL))
        assertSideAffectLogWithConnect()
    }

    @Test
    fun `should start remain paused`() {
        val state = moveToStart(remainPaused = true)

        assertEquals(state, State.Paused(pendingSendables = emptySet(), url = URL))
        assertEquals(emptyList<SideEffect>(), sideEffectLog)
    }

    @Test
    fun `should connect`() {
        val state = moveToConnected()

        assertEquals(state, emptyConnectedState)
        assertSideAffectLogWithConnect()
    }

    @Test
    fun `should send AT_LEAST_ONCE request if connected`() {
        var state = moveToConnected()

        val sendable = singleTestSendable(DeliveryType.AT_LEAST_ONCE)
        val sendables = setOf(sendable)

        state = transition(state, Event.Send(sendable))

        assertEquals(state, emptyConnectedState.copy(waitingForResponse = sendables))

        assertEquals(listOf(CONNECT_SIDE_EFFECT, SendSendables(sendables)), sideEffectLog)
    }

    @Test
    fun `should resend ON_RECONNECT request on each reconnect`() {
        var state = moveToConnected()

        val sendable = singleTestSendable(DeliveryType.ON_RECONNECT)
        val sendables = setOf(sendable)

        state = transition(state, Event.Send(sendable))

        val sendSideEffect = SendSendables(sendables)

        val expectedConnectedState = emptyConnectedState.copy(
            toResendOnReconnect = sendables,
            waitingForResponse = sendables
        )

        assertEquals(expectedConnectedState, state)
        assertEquals(listOf(CONNECT_SIDE_EFFECT, sendSideEffect), sideEffectLog)

        state = transition(state, Event.ConnectionError(TEST_EXCEPTION))
        assertEquals(
            State.WaitingForReconnect(
                attempt = 0,
                pendingSendables = sendables,
                url = URL
            ), state
        )

        state = transition(state, Event.ReadyToReconnect)
        assertEquals(State.Connecting(attempt = 0, pendingSendables = sendables, url = URL), state)

        state = transition(state, Event.Connected)

        assertEquals(expectedConnectedState, state)

        assertEquals(
            listOf(
                CONNECT_SIDE_EFFECT, sendSideEffect,
                Disconnect, ScheduleReconnect(0),
                CONNECT_SIDE_EFFECT, sendSideEffect
            ), sideEffectLog
        )
    }

    @Test
    fun `should delay request when connecting`() {
        var state = moveToStart()

        val sendable = singleTestSendable(DeliveryType.AT_LEAST_ONCE)
        val sendables = setOf(sendable)

        state = transition(state, Event.Send(sendable))

        assertEquals(State.Connecting(attempt = 0, pendingSendables = sendables, url = URL), state)

        state = transition(state, Event.Connected)

        assertEquals(emptyConnectedState.copy(waitingForResponse = sendables), state)
        assertEquals(listOf(CONNECT_SIDE_EFFECT, SendSendables(sendables)), sideEffectLog)
    }

    @Test
    fun `should force reconnect after send request`() {
        var state = moveToWaitingForReconnect()

        val sendable = singleTestSendable(DeliveryType.AT_LEAST_ONCE)
        val sendables = setOf(sendable)

        state = transition(state, Event.Send(sendable))

        assertEquals(State.Connecting(attempt = 0, pendingSendables = sendables, url = URL), state)

        assertEquals(
            listOf(
                CONNECT_SIDE_EFFECT, Disconnect, ScheduleReconnect(attempt = 1),
                Connect(URL)
            ), sideEffectLog
        )
    }

    @Test
    fun `should cancel when waiting to reconnect`() {
        var state = moveToWaitingForReconnect()

        val sendable = singleTestSendable(DeliveryType.AT_LEAST_ONCE)

        state = transition(state, Event.Send(sendable))
        state = transition(state, Event.Cancel(sendable))

        assertEquals(State.Connecting(attempt = 0, pendingSendables = emptySet(), url = URL), state)

        assertEquals(
            listOf(
                CONNECT_SIDE_EFFECT, Disconnect, ScheduleReconnect(attempt = 1),
                Connect(URL)
            ), sideEffectLog
        )
    }

    @Test
    fun `should ignore invalid transition from waiting`() {
        val state = State.WaitingForReconnect(URL, pendingSendables = emptySet())

        val newState = transition(state, Event.ConnectionError(TEST_EXCEPTION))

        assertEquals(state, newState)
    }

    @Test
    fun `should ignore invalid transition from connecting`() {
        val state = State.Connecting(URL, pendingSendables = emptySet())

        val newState = transition(state, Event.Start(URL, remainPaused = false))

        assertEquals(state, newState)
    }

    @Test
    fun `should ignore invalid transition from connected`() {
        val newState = transition(emptyConnectedState, Event.Start(URL, remainPaused = false))

        assertEquals(emptyConnectedState, newState)
    }

    @Test
    fun `should ignore invalid transition from disconnected`() {
        val newState = transition(State.Disconnected, Event.ConnectionError(TEST_EXCEPTION))

        assertEquals(State.Disconnected, newState)
    }

    @Test
    fun `should cancel not sent request`() {
        var state = moveToStart()

        val sendable = singleTestSendable(DeliveryType.AT_LEAST_ONCE)

        state = transition(state, Event.Send(sendable))
        state = transition(state, Event.Cancel(sendable))

        assertEquals(State.Connecting(attempt = 0, pendingSendables = emptySet(), url = URL), state)
        assertSideAffectLogWithConnect()
    }

    @Test
    fun `should subscribe`() {
        var state = moveToConnected()

        val sendable = singleTestSendable(DeliveryType.ON_RECONNECT)
        val sendables = setOf(sendable)
        val subscription = singleTestSubscription()

        state = transition(
            state,
            Event.Send(sendable),
            TEST_RESPONSE_EVENT,
            Event.Subscribed(subscription)
        )

        val subscribedLog = listOf(
            CONNECT_SIDE_EFFECT, SendSendables(sendables),
            ResponseToSendable(sendable, TEST_RESPONSE)
        )

        val expectedState = emptyConnectedState.copy(
            toResendOnReconnect = sendables,
            subscriptions = setOf(subscription)
        )

        assertEquals(expectedState, state)
        assertEquals(subscribedLog, sideEffectLog)

        state = transition(state, Event.SubscriptionResponse(testSubscriptionChange))

        assertEquals(expectedState, state)
        assertEquals(
            subscribedLog + RespondToSubscription(subscription, testSubscriptionChange),
            sideEffectLog
        )
    }

    @Test
    fun `should unsubscribe`() {
        var state = moveToConnected()

        val sendable = singleTestSendable(DeliveryType.ON_RECONNECT)
        val sendables = setOf(sendable)
        val subscription = singleTestSubscription()

        state = transition(
            state,
            Event.Send(sendable),
            TEST_RESPONSE_EVENT,
            Event.Subscribed(subscription),
            Event.Cancel(sendable)
        )

        val expectedLog = listOf(
            CONNECT_SIDE_EFFECT, SendSendables(sendables),
            ResponseToSendable(sendable, TEST_RESPONSE), Unsubscribe(subscription)
        )

        assertEquals(emptyConnectedState, state)
        assertEquals(expectedLog, sideEffectLog)
    }

    @Test
    fun `should be able to stop when connected`() {
        var state = moveToConnected()

        state = transition(state, Event.Stop)

        assertEquals(State.Disconnected, state)
        assertEquals(sideEffectLog, listOf(CONNECT_SIDE_EFFECT, Disconnect))
    }

    @Test
    fun `should delay reconnect when error occurs`() {
        val state = moveToWaitingForReconnect()

        assertEquals(
            State.WaitingForReconnect(URL, attempt = 1, pendingSendables = emptySet()),
            state
        )
        assertSideAffectLogForReconnect()
    }

    @Test
    fun `should be able to stop when connecting`() {
        var state = moveToWaitingForReconnect()

        state = transition(state, Event.Stop)

        assertEquals(State.Disconnected, state)
        assertEquals(reconnectSideEffectLog() + Disconnect, sideEffectLog)
    }

    @Test
    fun `should be able to stop when waiting for reconnect`() {
        var state = moveToStart()

        state = transition(state, Event.Stop)

        assertEquals(State.Disconnected, state)
        assertEquals(sideEffectLog, listOf(CONNECT_SIDE_EFFECT, Disconnect))
    }

    @Test
    fun `should cancel while waiting for reconnect sent request`() {
        var state = moveToStart()

        val sendable = singleTestSendable(DeliveryType.AT_LEAST_ONCE)

        state = transition(state, Event.Send(sendable))

        state = transition(state, Event.ConnectionError(TEST_EXCEPTION))

        state = transition(state, Event.Cancel(sendable))

        assertEquals(
            State.WaitingForReconnect(
                attempt = 1,
                pendingSendables = emptySet(),
                url = URL
            ), state
        )
        assertSideAffectLogForReconnect()
    }

    @Test
    fun `should cancel request when connected`() {
        var state = moveToConnected()

        val sendable = singleTestSendable(DeliveryType.ON_RECONNECT)
        val sendables = setOf(sendable)

        state = transition(state, Event.Send(sendable), Event.Cancel(sendable))

        val expectedLog = listOf(CONNECT_SIDE_EFFECT, SendSendables(sendables))

        assertEquals(emptyConnectedState, state)
        assertEquals(expectedLog, sideEffectLog)
    }

    @Test
    fun `should ignore cancellation of unknown request`() {
        var state = moveToConnected()

        val sendable = singleTestSendable(DeliveryType.ON_RECONNECT)

        state = transition(state, Event.Cancel(sendable))

        val expectedLog = listOf(CONNECT_SIDE_EFFECT)

        assertEquals(emptyConnectedState, state)
        assertEquals(expectedLog, sideEffectLog)
    }

    @Test
    fun `should ignore response to unknown request`() {
        var state = moveToConnected()

        state = transition(state, Event.SendableResponse(TEST_RESPONSE))

        val expectedLog = listOf(CONNECT_SIDE_EFFECT)

        assertEquals(emptyConnectedState, state)
        assertEquals(expectedLog, sideEffectLog)
    }

    @Test
    fun `should report error to AT_MOST_ONCE request and forget about it on failure`() {
        var state = moveToConnected()

        val sendable = singleTestSendable(DeliveryType.AT_MOST_ONCE)
        val sendables = setOf(sendable)

        state = transition(state, Event.Send(sendable), Event.ConnectionError(TEST_EXCEPTION))

        val expectedLog = listOf(
            CONNECT_SIDE_EFFECT, SendSendables(sendables),
            RespondSendablesError(sendables, TEST_EXCEPTION), Disconnect, ScheduleReconnect(attempt = 0)
        )

        assertEquals(
            State.WaitingForReconnect(URL, attempt = 0, pendingSendables = emptySet()),
            state
        )
        assertEquals(expectedLog, sideEffectLog)
    }

    @Test
    fun `should report error to AT_MOST_ONCE request on url switch and do not resend them`() {
        var state = moveToConnected()

        val sendable = singleTestSendable(DeliveryType.AT_MOST_ONCE)
        val sendables = setOf(sendable)

        state = transition(state, Event.Send(sendable), Event.SwitchUrl(SWITCH_URL))

        val expectedLog = listOf(
            CONNECT_SIDE_EFFECT, SendSendables(sendables),
            RespondSendablesError(sendables, SocketStateMachine.ConnectionClosedException),
            Disconnect, Connect(SWITCH_URL)
        )

        assertEquals(
            State.Connecting(SWITCH_URL, attempt = 0, pendingSendables = emptySet()),
            state
        )
        assertEquals(expectedLog, sideEffectLog)
    }

    @Test
    fun `should resend AT_LEAST_ONCE and ON_RECONNECT requests on url switch`() {
        var state = moveToConnected()

        val atLeastOnce = singleTestSendable(DeliveryType.AT_LEAST_ONCE)
        val onReconnect =  singleTestSendable(DeliveryType.ON_RECONNECT)

        state = transition(state, Event.Send(onReconnect), Event.Send(atLeastOnce), Event.SwitchUrl(SWITCH_URL))

        val expectedLog = listOf(
            CONNECT_SIDE_EFFECT, SendSendables(setOf(onReconnect)), SendSendables(setOf(atLeastOnce)),
            Disconnect, Connect(SWITCH_URL)
        )

        assertEquals(
            State.Connecting(SWITCH_URL, attempt = 0, pendingSendables = setOf(onReconnect, atLeastOnce)),
            state
        )
        assertEquals(expectedLog, sideEffectLog)
    }

    @Test
    fun `should change url while waiting for reconnect`() {
        val sendable = singleTestSendable(DeliveryType.AT_MOST_ONCE)
        val sendables = setOf(sendable)

        var state: State = State.WaitingForReconnect(URL, pendingSendables = sendables)

        state = transition(state, Event.SwitchUrl(SWITCH_URL))

        val expectedLog = listOf(Disconnect, Connect(SWITCH_URL))

        assertEquals(
            State.Connecting(SWITCH_URL, attempt = 0, pendingSendables = sendables),
            state
        )
        assertEquals(expectedLog, sideEffectLog)
    }

    @Test
    fun `should pause from waiting to reconnect`() {
        val sendable = singleTestSendable(DeliveryType.AT_MOST_ONCE)
        val sendables = setOf(sendable)

        var state: State = State.WaitingForReconnect(URL, pendingSendables = sendables)

        state = transition(state, Event.Pause)

        val expectedLog = listOf(Disconnect)

        assertEquals(
            State.Paused(URL, pendingSendables = sendables),
            state
        )
        assertEquals(expectedLog, sideEffectLog)
    }

    @Test
    fun `should switch url while connecting`() {
        val sendable = singleTestSendable(DeliveryType.AT_MOST_ONCE)
        val sendables = setOf(sendable)

        var state: State = State.Connecting(URL, pendingSendables = sendables)

        state = transition(state, Event.SwitchUrl(SWITCH_URL))

        val expectedLog = listOf(Disconnect, Connect(SWITCH_URL))

        assertEquals(
            State.Connecting(SWITCH_URL, pendingSendables = sendables),
            state
        )
        assertEquals(expectedLog, sideEffectLog)
    }

    @Test
    fun `should pause from connecting`() {
        val sendable = singleTestSendable(DeliveryType.AT_MOST_ONCE)
        val sendables = setOf(sendable)

        var state: State = State.Connecting(URL, pendingSendables = sendables)

        state = transition(state, Event.Pause)

        val expectedLog = listOf(Disconnect)

        assertEquals(
            State.Paused(URL, pendingSendables = sendables),
            state
        )
        assertEquals(expectedLog, sideEffectLog)
    }

    @Test
    fun `should pause from connected reporting errors to AT_MOST_ONCE requests`() {
        val atLeastOnce = singleTestSendable(DeliveryType.AT_LEAST_ONCE)
        val onReconnect = singleTestSendable(DeliveryType.ON_RECONNECT)
        val atMostOnce = singleTestSendable(DeliveryType.AT_MOST_ONCE)

        val sendables = setOf(atLeastOnce, onReconnect, atMostOnce)

        var state: State = State.Connected(
            URL,
            toResendOnReconnect = emptySet(),
            waitingForResponse = sendables,
            subscriptions = emptySet(),
            unknownSubscriptionResponses = emptyMap(),
        )

        state = transition(state, Event.Pause)

        val expectedLog = listOf(
            RespondSendablesError(setOf(atMostOnce), SocketStateMachine.ConnectionClosedException),
            Disconnect
        )

        assertEquals(
            State.Paused(URL, pendingSendables = setOf(onReconnect, atLeastOnce)),
            state
        )
        assertEquals(expectedLog, sideEffectLog)
    }

    @Test
    fun `should add request to pending when paused`() {
        val atLeastOnce = singleTestSendable(DeliveryType.AT_LEAST_ONCE)

        var state: State = State.Paused(URL, pendingSendables = emptySet())

        state = transition(state, Event.Send(atLeastOnce))

        val expectedLog = emptyList<SideEffect>()

        assertEquals(
            State.Paused(URL, pendingSendables = setOf(atLeastOnce)),
            state
        )
        assertEquals(expectedLog, sideEffectLog)
    }

    @Test
    fun `should cancel request while paused`() {
        val atLeastOnce = singleTestSendable(DeliveryType.AT_LEAST_ONCE)

        var state: State = State.Paused(URL, pendingSendables = setOf(atLeastOnce))

        state = transition(state, Event.Cancel(atLeastOnce))

        val expectedLog = emptyList<SideEffect>()

        assertEquals(
            State.Paused(URL, pendingSendables = emptySet()),
            state
        )
        assertEquals(expectedLog, sideEffectLog)
    }

    @Test
    fun `should stop while paused`() {
        var state: State = State.Paused(URL, pendingSendables = emptySet())

        state = transition(state, Event.Stop)

        val expectedLog = emptyList<SideEffect>()

        assertEquals(State.Disconnected, state)
        assertEquals(expectedLog, sideEffectLog)
    }

    @Test
    fun `should change url while paused`() {
        var state: State = State.Paused(URL, pendingSendables = emptySet())

        state = transition(state, Event.SwitchUrl(SWITCH_URL))

        val expectedLog = emptyList<SideEffect>()

        assertEquals(State.Paused(SWITCH_URL, pendingSendables = emptySet()), state)
        assertEquals(expectedLog, sideEffectLog)
    }

    @Test
    fun `should resume when paused`() {
        var state: State = State.Paused(URL, pendingSendables = emptySet())

        state = transition(state, Event.Resume)

        val expectedLog = listOf(Connect(URL))

        assertEquals(State.Connecting(URL, pendingSendables = emptySet()), state)
        assertEquals(expectedLog, sideEffectLog)
    }

    @Test
    fun `should ignore other events when paused`() {
        val initialState = State.Paused(URL, pendingSendables = emptySet())

        val newState = transition(
            initialState, Event.Connected, Event.Pause
        )

        val expectedLog = emptyList<SideEffect>()

        assertEquals(initialState, newState)
        assertEquals(expectedLog, sideEffectLog)
    }

    @Test
    fun `should remember unknown subscription respondes`() {
        val subscriptionChange = createFakeChange(result = "test")
        val event = Event.SubscriptionResponse(subscriptionChange)

        val newState = transition(emptyConnectedState, event)

        assertInstance<State.Connected>(newState)
        assertEquals(subscriptionChange, newState.unknownSubscriptionResponses[subscriptionChange.subscriptionId])
    }

    @Test
    fun `should response to unknown subscription responses`() {
        val subscriptionChange = createFakeChange(result = "test", subscriptionId = "test")

        val subscription = TestSubscription(id = "test", initiatorId = 0)

        val newState = transition(
            emptyConnectedState,
            Event.SubscriptionResponse(subscriptionChange),
            Event.Subscribed(subscription)
        )

        assertInstance<State.Connected>(newState)
        assertEquals(0, newState.unknownSubscriptionResponses.size)

        assertEquals(1, sideEffectLog.size)
        assertEquals(RespondToSubscription(subscription, subscriptionChange), sideEffectLog.first())
    }

    private fun moveToConnected(): State {
        val started = moveToStart()

        return transition(started, Event.Connected)
    }

    private fun moveToStart(remainPaused: Boolean = false): State {
        val initial = SocketStateMachine.initialState()

        return transition(initial, Event.Start(URL, remainPaused))
    }

    private fun moveToWaitingForReconnect(): State {
        val initial = SocketStateMachine.initialState()

        return transition(
            initial, Event.Start(URL, remainPaused = false),
            Event.ConnectionError(TEST_EXCEPTION)
        )
    }

    private fun assertSideAffectLogWithConnect() =
        assertEquals(listOf(CONNECT_SIDE_EFFECT), sideEffectLog)

    private fun assertSideAffectLogForReconnect() =
        assertEquals(reconnectSideEffectLog(), sideEffectLog)

    private fun reconnectSideEffectLog() = listOf(CONNECT_SIDE_EFFECT, Disconnect, ScheduleReconnect(1))

    private fun transition(state: State, vararg events: Event): State {
        var tempState = state

        events.forEach {
            val (updatedState, sideEffects) = SocketStateMachine.transition(tempState, it)

            tempState = updatedState

            sideEffectLog += sideEffects
        }

        return tempState
    }
}