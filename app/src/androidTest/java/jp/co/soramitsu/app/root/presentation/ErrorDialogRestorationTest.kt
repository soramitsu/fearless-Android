package jp.co.soramitsu.app.root.presentation

import android.content.Intent
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.common.base.errors.ValidationWarning
import jp.co.soramitsu.common.presentation.ErrorDialog
import jp.co.soramitsu.wallet.api.domain.TransferValidationResult
import jp.co.soramitsu.wallet.impl.presentation.PendingTransferValidationDialog
import jp.co.soramitsu.wallet.impl.presentation.TransferValidationDialogContract
import jp.co.soramitsu.wallet.impl.presentation.TransferValidationDialogCoordinator
import jp.co.soramitsu.wallet.impl.presentation.TransferValidationDialogResult
import jp.co.soramitsu.wallet.impl.presentation.isApprovableTransferWarning
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class ErrorDialogRestorationTest {

    private val instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Before
    fun setUp() {
        ErrorDialogRestorationTestController.reset()
    }

    @After
    fun tearDown() {
        ErrorDialogRestorationTestController.reset()
    }

    @Test
    fun validationResultsRemainParcelableAcrossProcessStateRoundTrip() {
        validationResults().forEach { original ->
            val restored = parcelRoundTrip(
                bundleOf(ErrorDialog.RESULT_PAYLOAD to original)
            )

            assertEquals(
                original,
                BundleCompat.getParcelable(
                    restored,
                    ErrorDialog.RESULT_PAYLOAD,
                    TransferValidationResult::class.java
                )
            )
        }
    }

    @Test
    fun productionContractsHaveStableUniqueKeysAndExactCallbackActions() {
        val contracts = TransferValidationDialogContract.entries
        assertEquals(contracts.size, contracts.map { it.requestKey }.toSet().size)
        assertEquals(
            setOf(
                "send_setup_validation_dialog_result",
                "confirm_send_validation_dialog_result",
                "cbdc_send_setup_validation_dialog_result",
                "cross_chain_confirm_validation_dialog_result",
                "cross_chain_setup_validation_dialog_result"
            ),
            contracts.map { it.requestKey }.toSet()
        )
        assertEquals(
            setOf(
                ErrorDialog.Action.POSITIVE,
                ErrorDialog.Action.SECOND_POSITIVE,
                ErrorDialog.Action.NEGATIVE
            ),
            TransferValidationDialogContract.SEND_SETUP.callbackActions
        )
        positiveOnlyContracts().forEach {
            assertEquals(
                setOf(ErrorDialog.Action.POSITIVE),
                it.callbackActions
            )
            assertEquals(
                setOf(
                    ErrorDialog.Action.POSITIVE,
                    ErrorDialog.Action.NEGATIVE
                ),
                it.resultActions
            )
        }
    }

    @Test
    fun restoredPositiveClickDeliversEachProductionContractOnce() {
        TransferValidationDialogContract.entries.forEachIndexed {
                index,
                contract ->
            ErrorDialogRestorationTestController.reset()
            val payload =
                TransferValidationResult.ExistentialDepositWarning(
                    "$index DOT"
                )

            launchHost().use { scenario ->
                restoreDialogAndAssertPayload(scenario, contract, payload)
                clickCurrentButtonTwice(
                    scenario,
                    ErrorDialogRestorationActivityTestHost.POSITIVE_BUTTON
                )
                waitForIdle()

                assertEquals(
                    listOf(
                        ObservedTransferDialogResult(
                            contract,
                            ErrorDialog.Action.POSITIVE,
                            payload
                        )
                    ),
                    ErrorDialogRestorationTestController.results.toList()
                )

                scenario.recreate()
                waitForIdle()
                assertEquals(
                    "A consumed FragmentResult must not be redelivered",
                    1,
                    ErrorDialogRestorationTestController.results.size
                )
            }
        }
    }

    @Test
    fun restoredSecondPositiveAndNegativeActionsDeliverOnce() {
        val secondPayload =
            TransferValidationResult.SubstrateBridgeAmountLessThenFeeWarning(
                "Polkadot"
            )
        launchHost().use { scenario ->
            restoreDialogAndAssertPayload(
                scenario,
                TransferValidationDialogContract.SEND_SETUP,
                secondPayload
            )
            clickCurrentButtonTwice(
                scenario,
                ErrorDialogRestorationActivityTestHost.SECOND_POSITIVE_BUTTON
            )
            waitForIdle()
            assertEquals(
                listOf(
                    ObservedTransferDialogResult(
                        TransferValidationDialogContract.SEND_SETUP,
                        ErrorDialog.Action.SECOND_POSITIVE,
                        secondPayload
                    )
                ),
                ErrorDialogRestorationTestController.results.toList()
            )
        }

        ErrorDialogRestorationTestController.reset()
        val negativePayload =
            TransferValidationResult.UtilityExistentialDepositWarning("1 KSM")
        launchHost().use { scenario ->
            restoreDialogAndAssertPayload(
                scenario,
                TransferValidationDialogContract.SEND_SETUP,
                negativePayload
            )
            clickCurrentButtonTwice(
                scenario,
                ErrorDialogRestorationActivityTestHost.NEGATIVE_BUTTON
            )
            waitForIdle()
            assertEquals(
                listOf(
                    ObservedTransferDialogResult(
                        TransferValidationDialogContract.SEND_SETUP,
                        ErrorDialog.Action.NEGATIVE,
                        negativePayload
                    )
                ),
                ErrorDialogRestorationTestController.results.toList()
            )
        }
    }

    @Test
    fun secondPositiveWithoutRenderedButtonFailsClosed() {
        val contract = TransferValidationDialogContract.SEND_SETUP
        val payload =
            TransferValidationResult.ExistentialDepositWarning("no alternate")
        launchHost().use { scenario ->
            scenario.onActivity {
                assertTrue(
                    it.showRestorableDialog(
                        contract,
                        payload,
                        includeSecondPositiveButton = false
                    )
                )
            }
            waitForIdle()
            val pending = requirePending(scenario)
            assertNull(pending.secondPositiveButtonText)
            assertTrue(
                composeRule.onAllNodesWithText(
                    ErrorDialogRestorationActivityTestHost
                        .SECOND_POSITIVE_BUTTON
                ).fetchSemanticsNodes().isEmpty()
            )

            scenario.onActivity {
                it.publishRawResult(
                    contract,
                    resultBundle(
                        ErrorDialog.Action.SECOND_POSITIVE,
                        pending.asResult()
                    )
                )
                assertEquals(pending, it.pendingDialog())
            }
            waitForIdle()
            assertTrue(ErrorDialogRestorationTestController.results.isEmpty())

            scenario.onActivity {
                it.publishRawResult(
                    contract,
                    resultBundle(
                        ErrorDialog.Action.POSITIVE,
                        pending.asResult()
                    )
                )
            }
            waitForIdle()
            assertEquals(
                listOf(
                    ObservedTransferDialogResult(
                        contract,
                        ErrorDialog.Action.POSITIVE,
                        payload
                    )
                ),
                ErrorDialogRestorationTestController.results.toList()
            )
        }
    }

    @Test
    fun serializedSavedStateRecreatesCoordinatorAndRejectsStaleResult() {
        val contract = TransferValidationDialogContract.CONFIRM_SEND
        val payload =
            TransferValidationResult.ExistentialDepositWarning("saved")
        instrumentation.runOnMainSync {
            val originalHandle = SavedStateHandle()
            val originalCoordinator =
                TransferValidationDialogCoordinator(originalHandle)
            assertTrue(
                originalCoordinator.enqueue(
                    contract,
                    payload,
                    ValidationWarning(
                        "Saved warning",
                        "Must survive coordinator disposal",
                        "Approve",
                        "Cancel",
                        null
                    )
                )
            )
            val originalPending =
                requireNotNull(originalCoordinator.pendingDialog.value)

            // This Bundle is the exact provider payload Android persists for a
            // SavedStateHandle. Parceling it removes all object identity from
            // the original coordinator/ViewModel graph.
            val serializedState = parcelRoundTrip(
                originalHandle.savedStateProvider().saveState()
            )

            val recreatedHandle =
                SavedStateHandle.createHandle(serializedState, null)
            val recreatedCoordinator =
                TransferValidationDialogCoordinator(recreatedHandle)
            val recreatedPending =
                requireNotNull(recreatedCoordinator.pendingDialog.value)
            assertEquals(originalPending, recreatedPending)
            assertEquals(
                originalPending.correlationId,
                recreatedPending.correlationId
            )

            val stale = TransferValidationDialogResult(
                "stale-${recreatedPending.correlationId}",
                recreatedPending.validationResult
            )
            assertNull(
                recreatedCoordinator.consumeIfMatches(
                    contract,
                    ErrorDialog.Action.POSITIVE,
                    stale
                )
            )
            assertEquals(
                recreatedPending,
                recreatedCoordinator.pendingDialog.value
            )

            assertEquals(
                recreatedPending,
                recreatedCoordinator.consumeIfMatches(
                    contract,
                    ErrorDialog.Action.POSITIVE,
                    recreatedPending.asResult()
                )
            )
            assertNull(recreatedCoordinator.pendingDialog.value)
        }
    }

    @Test
    fun malformedMissingAndUnsupportedResultsFailClosedWithoutConsumption() {
        val contract = TransferValidationDialogContract.CONFIRM_SEND
        val payload =
            TransferValidationResult.ExistentialDepositWarning("2 DOT")

        launchHost().use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(activity.showRestorableDialog(contract, payload))
            }
            waitForIdle()
            val pending = requirePending(scenario)
            val validEnvelope = pending.asResult()
            val malformedResults = listOf(
                bundleOf(
                    ErrorDialog.RESULT_ACTION to "TRANSFER_WITHOUT_REVIEW",
                    ErrorDialog.RESULT_PAYLOAD to validEnvelope
                ),
                bundleOf(ErrorDialog.RESULT_PAYLOAD to validEnvelope),
                bundleOf(
                    ErrorDialog.RESULT_ACTION to 42,
                    ErrorDialog.RESULT_PAYLOAD to validEnvelope
                ),
                bundleOf(
                    ErrorDialog.RESULT_ACTION to
                        ErrorDialog.Action.POSITIVE.name
                ),
                bundleOf(
                    ErrorDialog.RESULT_ACTION to
                        ErrorDialog.Action.POSITIVE.name,
                    ErrorDialog.RESULT_PAYLOAD to
                        Intent("type-confused-result")
                ),
                resultBundle(ErrorDialog.Action.SECOND_POSITIVE, validEnvelope),
                resultBundle(ErrorDialog.Action.BACK, validEnvelope)
            )

            scenario.onActivity { activity ->
                malformedResults.forEach {
                    activity.publishRawResult(contract, it)
                }
                assertEquals(pending, activity.pendingDialog())
            }
            waitForIdle()
            assertTrue(ErrorDialogRestorationTestController.results.isEmpty())

            scenario.onActivity {
                it.publishRawResult(
                    contract,
                    resultBundle(ErrorDialog.Action.POSITIVE, validEnvelope)
                )
            }
            waitForIdle()
            assertEquals(
                listOf(
                    ObservedTransferDialogResult(
                        contract,
                        ErrorDialog.Action.POSITIVE,
                        payload
                    )
                ),
                ErrorDialogRestorationTestController.results.toList()
            )
        }
    }

    @Test
    fun wrongValidationSubtypeFailsClosedAndKeepsPendingWarning() {
        val contract = TransferValidationDialogContract.CONFIRM_SEND
        val payload =
            TransferValidationResult.ExistentialDepositWarning("3 DOT")
        launchHost().use { scenario ->
            scenario.onActivity {
                assertTrue(it.showRestorableDialog(contract, payload))
            }
            waitForIdle()
            val pending = requirePending(scenario)

            nonApprovableResults().forEach { wrongResult ->
                scenario.onActivity {
                    it.publishRawResult(
                        contract,
                        resultBundle(
                            ErrorDialog.Action.POSITIVE,
                            TransferValidationDialogResult(
                                pending.correlationId,
                                wrongResult
                            )
                        )
                    )
                    assertEquals(pending, it.pendingDialog())
                }
            }
            waitForIdle()
            assertTrue(ErrorDialogRestorationTestController.results.isEmpty())
        }
    }

    @Test
    fun staleCorrelationFailsClosedAndCannotClearCurrentWarning() {
        val contract = TransferValidationDialogContract.CROSS_CHAIN_CONFIRM
        val payload =
            TransferValidationResult.ExistentialDepositWarning("4 DOT")
        launchHost().use { scenario ->
            scenario.onActivity {
                assertTrue(it.showRestorableDialog(contract, payload))
            }
            waitForIdle()
            val pending = requirePending(scenario)

            scenario.onActivity {
                it.publishRawResult(
                    contract,
                    resultBundle(
                        ErrorDialog.Action.POSITIVE,
                        TransferValidationDialogResult(
                            "stale-${pending.correlationId}",
                            payload
                        )
                    )
                )
                it.publishRawResult(
                    contract,
                    resultBundle(
                        ErrorDialog.Action.POSITIVE,
                        TransferValidationDialogResult(
                            pending.correlationId,
                            TransferValidationResult
                                .UtilityExistentialDepositWarning("stale")
                        )
                    )
                )
                it.publishRawResult(
                    TransferValidationDialogContract.CONFIRM_SEND,
                    resultBundle(
                        ErrorDialog.Action.POSITIVE,
                        pending.asResult()
                    )
                )
                assertEquals(pending, it.pendingDialog())
            }
            waitForIdle()
            assertTrue(ErrorDialogRestorationTestController.results.isEmpty())
        }
    }

    @Test
    fun duplicateValidResultsAndRapidClicksDispatchOnlyOnce() {
        val contract = TransferValidationDialogContract.CONFIRM_SEND
        val payload =
            TransferValidationResult.ExistentialDepositWarning("5 DOT")
        launchHost().use { scenario ->
            scenario.onActivity {
                assertTrue(it.showRestorableDialog(contract, payload))
            }
            waitForIdle()
            val validEnvelope = requirePending(scenario).asResult()

            scenario.onActivity { activity ->
                repeat(2) {
                    activity.publishRawResult(
                        contract,
                        resultBundle(
                            ErrorDialog.Action.POSITIVE,
                            validEnvelope
                        )
                    )
                }
            }
            clickCurrentButtonTwice(
                scenario,
                ErrorDialogRestorationActivityTestHost.POSITIVE_BUTTON
            )
            waitForIdle()

            assertEquals(
                listOf(
                    ObservedTransferDialogResult(
                        contract,
                        ErrorDialog.Action.POSITIVE,
                        payload
                    )
                ),
                ErrorDialogRestorationTestController.results.toList()
            )
        }
    }

    @Test
    fun stateSavedWarningIsQueuedAndShownWhenScreenResumes() {
        val contract = TransferValidationDialogContract.CONFIRM_SEND
        val payload =
            TransferValidationResult.ExistentialDepositWarning("6 DOT")
        launchHost().use { scenario ->
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.onActivity { activity ->
                assertTrue(activity.showRestorableDialog(contract, payload))
                assertNotNull(activity.pendingDialog())
                assertNull(activity.currentDialog())
            }

            scenario.moveToState(Lifecycle.State.RESUMED)
            waitForButton(
                ErrorDialogRestorationActivityTestHost.POSITIVE_BUTTON
            )
            scenario.onActivity {
                assertEquals(1, it.activeErrorDialogCount())
            }
        }
    }

    @Test
    fun unrelatedDialogBlocksTemporarilyThenQueuedWarningRetries() {
        val contract = TransferValidationDialogContract.CONFIRM_SEND
        val payload =
            TransferValidationResult.ExistentialDepositWarning("7 DOT")
        launchHost().use { scenario ->
            scenario.onActivity { activity ->
                activity.showUnrelatedDialog()
                assertTrue(activity.showRestorableDialog(contract, payload))
                assertNotNull(activity.pendingDialog())
                assertEquals(1, activity.activeErrorDialogCount())
            }

            scenario.onActivity { it.dismissCurrentDialog() }
            waitForButton(
                ErrorDialogRestorationActivityTestHost.POSITIVE_BUTTON
            )
            scenario.onActivity {
                assertNotNull(it.pendingDialog())
                assertEquals(1, it.activeErrorDialogCount())
            }

            clickCurrentButtonTwice(
                scenario,
                ErrorDialogRestorationActivityTestHost.POSITIVE_BUTTON
            )
            waitForIdle()
            assertEquals(1, ErrorDialogRestorationTestController.results.size)
        }
    }

    @Test
    fun duplicateIsSuppressedAndNewerWarningSupersedesStaleDialog() {
        val contract = TransferValidationDialogContract.CONFIRM_SEND
        val original =
            TransferValidationResult.ExistentialDepositWarning("8 DOT")
        val duplicate =
            TransferValidationResult.ExistentialDepositWarning("999 DOT")
        launchHost().use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(activity.showRestorableDialog(contract, original))
                val firstPending = activity.pendingDialog()
                assertFalse(activity.showRestorableDialog(contract, original))
                assertTrue(activity.showRestorableDialog(contract, duplicate))
                assertNotEquals(firstPending, activity.pendingDialog())
                assertEquals(
                    duplicate,
                    activity.pendingDialog()?.validationResult
                )
                assertEquals(1, activity.activeErrorDialogCount())
            }

            // The visible dialog still carries the first correlation. It must
            // close without dispatch, then the queued newer warning is retried.
            clickCurrentButtonTwice(
                scenario,
                ErrorDialogRestorationActivityTestHost.POSITIVE_BUTTON
            )
            waitForIdle()
            assertTrue(ErrorDialogRestorationTestController.results.isEmpty())
            waitForButton(
                ErrorDialogRestorationActivityTestHost.POSITIVE_BUTTON
            )
            scenario.onActivity {
                assertEquals(duplicate, it.pendingDialog()?.validationResult)
                assertEquals(1, it.activeErrorDialogCount())
            }
            clickCurrentButtonTwice(
                scenario,
                ErrorDialogRestorationActivityTestHost.POSITIVE_BUTTON
            )
            waitForIdle()
            assertEquals(
                duplicate,
                ErrorDialogRestorationTestController.results.single().payload
            )
        }
    }

    @Test
    fun restoredLambdaBackedDialogDismissesWithoutInvokingLostCallback() {
        launchHost().use { scenario ->
            scenario.onActivity { activity ->
                activity.showEphemeralCallbackDialog()
                assertNotNull(activity.currentDialog())
            }
            waitForIdle()

            scenario.recreate()
            waitForIdle()

            scenario.onActivity { activity ->
                activity.executePendingDialogTransactions()
                assertNull(
                    "A restored dialog cannot safely retain an instance lambda",
                    activity.currentDialog()
                )
            }
            assertEquals(
                "Restoration must never invoke a stale in-memory callback",
                0,
                ErrorDialogRestorationTestController
                    .ephemeralCallbackCount
                    .get()
            )
        }
    }

    @Test
    fun malformedOrMissingDialogActionDecoderFailsClosed() {
        assertNull(
            ErrorDialog.resultAction(
                bundleOf(ErrorDialog.RESULT_ACTION to "TRANSFER_NOW")
            )
        )
        assertNull(
            ErrorDialog.resultAction(
                bundleOf(ErrorDialog.RESULT_ACTION to 7)
            )
        )
        assertNull(ErrorDialog.resultAction(Bundle.EMPTY))
    }

    private fun restoreDialogAndAssertPayload(
        scenario: ActivityScenario<ErrorDialogRestorationActivityTestHost>,
        contract: TransferValidationDialogContract,
        payload: TransferValidationResult
    ) {
        var originalParentIdentity = 0
        var originalViewLifecycleOwnerIdentity = 0
        var originalDialogIdentity = 0
        scenario.onActivity { activity ->
            val originalParent = activity.currentParent()
            originalParentIdentity = System.identityHashCode(originalParent)
            originalViewLifecycleOwnerIdentity =
                System.identityHashCode(originalParent.viewLifecycleOwner)
            assertTrue(activity.showRestorableDialog(contract, payload))
            originalDialogIdentity =
                System.identityHashCode(requireNotNull(activity.currentDialog()))
        }
        waitForIdle()

        scenario.recreate()
        waitForIdle()

        assertEquals(
            "The host did not traverse Android's saved-state recreation path",
            1,
            ErrorDialogRestorationTestController.restoredActivityCount.get()
        )
        scenario.onActivity { activity ->
            val restoredParent = activity.currentParent()
            val restoredDialog = activity.currentDialog()
            val restoredEnvelope =
                BundleCompat.getParcelable(
                    requireNotNull(restoredDialog).requireArguments(),
                    ErrorDialog.RESULT_PAYLOAD,
                    TransferValidationDialogResult::class.java
                )
            assertNotNull(restoredEnvelope)
            assertEquals(payload, restoredEnvelope?.validationResult)
            assertEquals(
                activity.pendingDialog()?.correlationId,
                restoredEnvelope?.correlationId
            )
            assertNotEquals(
                originalParentIdentity,
                System.identityHashCode(restoredParent)
            )
            assertNotEquals(
                originalViewLifecycleOwnerIdentity,
                System.identityHashCode(restoredParent.viewLifecycleOwner)
            )
            assertNotEquals(
                originalDialogIdentity,
                System.identityHashCode(restoredDialog)
            )
        }
    }

    private fun requirePending(
        scenario: ActivityScenario<ErrorDialogRestorationActivityTestHost>
    ): PendingTransferValidationDialog {
        var result: PendingTransferValidationDialog? = null
        scenario.onActivity { result = it.pendingDialog() }
        return requireNotNull(result)
    }

    private fun PendingTransferValidationDialog.asResult() =
        TransferValidationDialogResult(correlationId, validationResult)

    private fun resultBundle(
        action: ErrorDialog.Action,
        payload: Parcelable
    ) = bundleOf(
        ErrorDialog.RESULT_ACTION to action.name,
        ErrorDialog.RESULT_PAYLOAD to payload
    )

    private fun clickCurrentButtonTwice(
        scenario: ActivityScenario<ErrorDialogRestorationActivityTestHost>,
        text: String
    ) {
        scenario.onActivity {
            assertNotNull(it.currentDialog())
        }
        waitForButton(text)
        composeRule.onNodeWithText(text).performTouchInput {
            doubleClick()
        }
    }

    private fun waitForButton(text: String) {
        composeRule.waitUntil(timeoutMillis = UI_TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText(text)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun launchHost(): ActivityScenario<ErrorDialogRestorationActivityTestHost> {
        val context =
            ApplicationProvider.getApplicationContext<android.content.Context>()
        return ActivityScenario.launch(
            Intent(
                context,
                ErrorDialogRestorationActivityTestHost::class.java
            )
        )
    }

    private fun waitForIdle() {
        instrumentation.waitForIdleSync()
        composeRule.waitForIdle()
    }

    private fun parcelRoundTrip(original: Bundle): Bundle {
        val parcel = Parcel.obtain()
        return try {
            parcel.writeBundle(original)
            parcel.setDataPosition(0)
            requireNotNull(parcel.readBundle(javaClass.classLoader)).also {
                it.classLoader =
                    TransferValidationResult::class.java.classLoader
            }
        } finally {
            parcel.recycle()
        }
    }

    private fun validationResults(): List<TransferValidationResult> = listOf(
        TransferValidationResult.Valid,
        TransferValidationResult.InsufficientBalance,
        TransferValidationResult.InsufficientUtilityAssetBalance,
        TransferValidationResult.SubstrateBridgeMinimumAmountRequired("1"),
        TransferValidationResult.SubstrateBridgeAmountLessThenFeeWarning(
            "Polkadot"
        ),
        TransferValidationResult.ExistentialDepositWarning("2"),
        TransferValidationResult.ExistentialDepositError("3"),
        TransferValidationResult.UtilityExistentialDepositWarning("4"),
        TransferValidationResult.UtilityExistentialDepositError("5"),
        TransferValidationResult.DeadRecipient("6", "7", "8"),
        TransferValidationResult.InvalidAddress,
        TransferValidationResult.TransferToTheSameAddress,
        TransferValidationResult.WaitForFee
    )

    private fun nonApprovableResults(): List<TransferValidationResult> =
        validationResults().filterNot {
            it.isApprovableTransferWarning()
        }

    private fun positiveOnlyContracts() =
        TransferValidationDialogContract.entries.filter {
            it != TransferValidationDialogContract.SEND_SETUP
        }

    private companion object {
        const val UI_TIMEOUT_MILLIS = 10_000L
    }
}
