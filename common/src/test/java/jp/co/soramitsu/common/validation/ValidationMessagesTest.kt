package jp.co.soramitsu.common.validation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.errors.ValidationException
import jp.co.soramitsu.common.base.errors.ValidationWarning
import jp.co.soramitsu.common.resources.ContextManager
import jp.co.soramitsu.common.resources.ResourceManagerImpl
import jp.co.soramitsu.core.extrinsic.MetadataHashUnavailableException
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.CallMovedToAssetHubException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ValidationMessagesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val resources = ResourceManagerImpl(mock<ContextManager>().also { whenever(it.getContext()).thenReturn(context) })

    @Test
    fun shouldMapBalanceAddressAndNetworkErrorsToTheirLocalizedMessages() {
        val cases = listOf(
            Triple(StakeInsufficientBalanceException(resources), R.string.common_not_enough_funds_title, R.string.staking_setup_too_big_error),
            Triple(SpendInsufficientBalanceException(resources), R.string.common_not_enough_funds_title, R.string.choose_amount_error_too_big),
            Triple(CrossChainDestinationUnavailableException(resources), R.string.common_error_general_title, R.string.cross_chain_destination_unavailable_message),
            Triple(InsufficientStakeBalanceException(resources), R.string.common_not_enough_funds_title, R.string.pool_staking_unstake_error),
            Triple(TransferAddressNotValidException(resources), R.string.address_not_valid_error_title, R.string.transfer_address_not_valid_error_message),
            Triple(AddressNotValidException(resources), R.string.address_not_valid_error_title, R.string.address_not_valid_error_message),
            Triple(WaitForFeeCalculationException(resources), R.string.fee_not_yet_loaded_title, R.string.fee_not_yet_loaded_message),
            Triple(FeeInsufficientBalanceException(resources), R.string.common_not_enough_funds_title, R.string.common_not_enough_funds_message),
            Triple(UnableToPayFeeException(resources), R.string.common_error_general_title, R.string.common_not_enough_funds_message),
            Triple(NotEnoughResultedAmountToPayFeeException(resources), R.string.polkaswap_not_enough_result_to_pay_fee_title, R.string.polkaswap_not_enough_result_to_pay_fee_message),
            Triple(MovedToAssetHubException(resources), R.string.relay_moved_to_asset_hub_title, R.string.relay_moved_to_asset_hub_message),
            Triple(FeeCalculationFailedException(resources), R.string.choose_amount_network_error, R.string.choose_amount_error_fee),
            Triple(MetadataHashUnverifiedException(resources), R.string.network_metadata_unverified_title, R.string.network_metadata_unverified_message)
        )
        cases.forEach { (exception, title, message) ->
            assertEquals(context.getString(title), exception.message)
            assertEquals(context.getString(message), exception.explanation)
            val (extractedTitle, extractedMessage) = exception
            assertEquals(exception.message, extractedTitle)
            assertEquals(exception.explanation, extractedMessage)
        }
    }

    @Test
    fun shouldInterpolateThresholdsAndRecipientAmounts() {
        assertEquals(context.getString(R.string.sora_bridge_low_amount_format_alert, "1 DOT"),
            SubstrateBridgeMinimumAmountRequired(resources, "1 DOT").explanation)
        assertEquals(context.getString(R.string.common_existential_error_message, "0.1 DOT"),
            ExistentialDepositCrossedException(resources, "0.1 DOT").explanation)
        assertEquals(context.getString(R.string.common_existential_unknown_retry_message, "DOT"),
            ExistentialDepositUnknownException(resources, "DOT").explanation)
        assertEquals(context.getString(R.string.wallet_send_dead_recipient_message_v2, "0.1", "1", "0.9"),
            DeadRecipientException(resources, "0.1", "1", "0.9").explanation)
        assertEquals(context.getString(R.string.min_to_create_pool_threshold_error_message, "500 DOT"),
            MinPoolCreationThresholdException(resources, "500 DOT").explanation)
        assertEquals(context.getString(R.string.staking_setup_amount_too_low, "5 DOT"),
            AmountTooLowToStakeException(resources, "5 DOT").explanation)
    }

    @Test
    fun shouldOfferProceedCancelAndMaximumOnlyWhereApplicable() {
        val bridge = SubstrateBridgeAmountLessThenFeeException(resources, "Polkadot")
        assertEquals(context.getString(R.string.sora_bridge_amount_less_fee, "Polkadot"), bridge.explanation)
        val deposit = ExistentialDepositCrossedWarning(resources, "1 DOT")
        assertEquals(context.getString(R.string.common_existential_warning_message, "1 DOT"), deposit.explanation)
        val unknown = ExistentialDepositUnknownWarning(resources, "DOT, KSM")
        assertEquals(context.getString(R.string.common_existential_unknown_message, "DOT, KSM"), unknown.explanation)
        val same = TransferToTheSameAddressException(resources)
        assertEquals(context.getString(R.string.same_address_transfer_warning_message), same.explanation)
        listOf<ValidationWarning>(bridge, deposit, unknown, same).forEach {
            val (_, _, positive, negative, second) = it
            assertEquals(context.getString(R.string.common_proceed), positive)
            assertEquals(context.getString(R.string.common_cancel), negative)
            if (it === deposit) assertEquals(context.getString(R.string.set_max_amount), second) else assertNull(second)
        }
    }

    @Test
    fun shouldRecognizeNestedRuntimeFailuresAndPrioritizeMovedCalls() {
        val moved = CallMovedToAssetHubException("Staking", "bond")
        val nested = IllegalStateException("outer", IllegalArgumentException("inner", moved))
        assertTrue(moved.isCallMovedToAssetHub())
        assertTrue(nested.isCallMovedToAssetHub())
        assertFalse(IllegalStateException("ordinary").isCallMovedToAssetHub())
        assertTrue(nested.toFeeError(resources) is MovedToAssetHubException)
        val metadata = MetadataHashUnavailableException("unverified")
        assertTrue(metadata.isMetadataHashUnavailable())
        assertTrue(IllegalStateException(metadata).isMetadataHashUnavailable())
        assertFalse(nested.isMetadataHashUnavailable())
        val validation = ValidationException("Title", "Detail")
        assertSame(validation, validation.toFeeError(resources))
        assertTrue(IllegalStateException("offline").toFeeError(resources) is FeeCalculationFailedException)
        validation.initCause(moved)
        assertTrue(validation.toFeeError(resources) is MovedToAssetHubException)
    }
}
