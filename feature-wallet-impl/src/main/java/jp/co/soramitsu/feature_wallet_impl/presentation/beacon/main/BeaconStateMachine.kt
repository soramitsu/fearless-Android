package jp.co.soramitsu.feature_wallet_impl.presentation.beacon.main

import it.airgap.beaconsdk.blockchain.substrate.message.request.PermissionSubstrateRequest
import it.airgap.beaconsdk.blockchain.substrate.message.request.SignPayloadSubstrateRequest
import jp.co.soramitsu.common.utils.StateMachine
import jp.co.soramitsu.feature_wallet_impl.presentation.beacon.main.BeaconStateMachine.Event
import jp.co.soramitsu.feature_wallet_impl.presentation.beacon.main.BeaconStateMachine.SideEffect
import jp.co.soramitsu.feature_wallet_impl.presentation.beacon.main.BeaconStateMachine.State

class BeaconStateMachine : StateMachine<State, Event, SideEffect>(State.Initializing) {

    sealed class State {
        object Initializing : State()

        class Connected(val dAppMetadata: DAppMetadataModel) : State()

        class AwaitingPermissionsApproval(
            val request: PermissionSubstrateRequest,
            val dAppMetadata: DAppMetadataModel
        ) : State()

        class AwaitingSigningApproval(
            val awaitingRequest: SignPayloadSubstrateRequest,
            val dAppMetadata: DAppMetadataModel
        ) : State()

        object Finished : State()
    }

    sealed class Event {
        class ReceivedMetadata(val dAppMetadata: DAppMetadataModel) : Event()

        class ReceivedPermissionsRequest(val request: PermissionSubstrateRequest) : Event()

        object ApprovedPermissions : Event()

        object DeclinedPermissions : Event()

        class ReceivedSigningRequest(val request: SignPayloadSubstrateRequest) : Event()

        object ApprovedSigning : Event()

        object DeclinedSigning : Event()

        object ExistRequested : Event()
    }

    sealed class SideEffect {
        class AskPermissionsApproval(val dAppName: String) : SideEffect()

        class AskSignApproval(val request: SignPayloadSubstrateRequest) : SideEffect()

        class RespondApprovedPermissions(val request: PermissionSubstrateRequest) : SideEffect()

        class RespondApprovedSign(val request: SignPayloadSubstrateRequest) : SideEffect()

        class RespondDeclinedSign(val request: SignPayloadSubstrateRequest) : SideEffect()

        class RespondDeclinedPermissions(val request: PermissionSubstrateRequest) : SideEffect()

        object Exit : SideEffect()
    }

    override fun performTransition(state: State, event: Event): State {
        return when (event) {
            is Event.ReceivedMetadata -> when (state) {
                is State.Initializing -> State.Connected(event.dAppMetadata)
                else -> state
            }

            is Event.ReceivedPermissionsRequest -> when (state) {
                is State.Connected -> {
                    sideEffect(SideEffect.AskPermissionsApproval(event.request.appMetadata.name))

                    State.AwaitingPermissionsApproval(event.request, state.dAppMetadata)
                }

                else -> state
            }

            is Event.ApprovedPermissions -> when (state) {
                is State.AwaitingPermissionsApproval -> {
                    sideEffect(SideEffect.RespondApprovedPermissions(state.request))

                    State.Connected(state.dAppMetadata)
                }

                else -> state
            }

            is Event.ReceivedSigningRequest -> when (state) {
                is State.Connected -> {
                    sideEffect(SideEffect.AskSignApproval(event.request))

                    State.AwaitingSigningApproval(event.request, state.dAppMetadata)
                }

                else -> state
            }

            Event.ApprovedSigning -> when (state) {
                is State.AwaitingSigningApproval -> {
                    sideEffect(SideEffect.RespondApprovedSign(state.awaitingRequest))

                    State.Connected(state.dAppMetadata)
                }

                else -> state
            }

            Event.DeclinedPermissions -> when (state) {
                is State.AwaitingPermissionsApproval -> {
                    sideEffect(SideEffect.RespondDeclinedPermissions(state.request))
                    sideEffect(SideEffect.Exit)

                    State.Finished
                }

                else -> state
            }

            Event.DeclinedSigning -> {
                when (state) {
                    is State.AwaitingSigningApproval -> {
                        sideEffect(SideEffect.RespondDeclinedSign(state.awaitingRequest))

                        State.Connected(state.dAppMetadata)
                    }

                    else -> state
                }
            }

            Event.ExistRequested -> {
                sideEffect(SideEffect.Exit)

                State.Finished
            }
        }
    }
}
