package jp.co.soramitsu.common.validation

import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.errors.ValidationException
import jp.co.soramitsu.common.base.errors.ValidationWarning
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.CallMovedToAssetHubException

class StakeInsufficientBalanceException(resourceManager: ResourceManager) : ValidationException(
    resourceManager.getString(R.string.common_not_enough_funds_title),
    resourceManager.getString(R.string.staking_setup_too_big_error)
)

class SpendInsufficientBalanceException(resourceManager: ResourceManager) : ValidationException(
    resourceManager.getString(R.string.common_not_enough_funds_title),
    resourceManager.getString(R.string.choose_amount_error_too_big)
)

class SubstrateBridgeMinimumAmountRequired(resourceManager: ResourceManager, amount: String) : ValidationException(
    resourceManager.getString(R.string.common_warning),
    resourceManager.getString(R.string.sora_bridge_low_amount_format_alert, amount)
)

class SubstrateBridgeAmountLessThenFeeException(resourceManager: ResourceManager, chainName: String) : ValidationWarning(
    resourceManager.getString(R.string.common_warning),
    resourceManager.getString(R.string.sora_bridge_amount_less_fee, chainName),
    resourceManager.getString(R.string.common_proceed),
    resourceManager.getString(R.string.common_cancel),
    null
)

class ExistentialDepositCrossedWarning(resourceManager: ResourceManager, edAmount: String) : ValidationWarning(
    resourceManager.getString(R.string.common_existential_warning_title),
    resourceManager.getString(R.string.common_existential_warning_message, edAmount),
    resourceManager.getString(R.string.common_proceed),
    resourceManager.getString(R.string.common_cancel),
    resourceManager.getString(R.string.set_max_amount),
)

class ExistentialDepositCrossedException(resourceManager: ResourceManager, edAmount: String) : ValidationException(
    resourceManager.getString(R.string.common_existential_warning_title),
    resourceManager.getString(R.string.common_existential_error_message, edAmount)
)

class ExistentialDepositUnknownWarning(resourceManager: ResourceManager, symbols: String) : ValidationWarning(
    resourceManager.getString(R.string.common_existential_unknown_title),
    resourceManager.getString(R.string.common_existential_unknown_message, symbols),
    resourceManager.getString(R.string.common_proceed),
    resourceManager.getString(R.string.common_cancel),
    null
)

class ExistentialDepositUnknownException(resourceManager: ResourceManager, symbol: String) : ValidationException(
    resourceManager.getString(R.string.common_existential_unknown_title),
    resourceManager.getString(R.string.common_existential_unknown_retry_message, symbol)
)

class CrossChainDestinationUnavailableException(resourceManager: ResourceManager) : ValidationException(
    resourceManager.getString(R.string.common_error_general_title),
    resourceManager.getString(R.string.cross_chain_destination_unavailable_message)
)

class TransferToTheSameAddressException(resourceManager: ResourceManager) : ValidationWarning(
    resourceManager.getString(R.string.common_warning),
    resourceManager.getString(R.string.same_address_transfer_warning_message),
    resourceManager.getString(R.string.common_proceed),
    resourceManager.getString(R.string.common_cancel),
    null
)

class DeadRecipientException(resourceManager: ResourceManager, resultAmount: String, edAmount: String, topUpAmount: String) : ValidationException(
    resourceManager.getString(R.string.common_amount_low),
    resourceManager.getString(R.string.wallet_send_dead_recipient_message_v2, resultAmount, edAmount, topUpAmount)
)

class InsufficientStakeBalanceException(resourceManager: ResourceManager) : ValidationException(
    resourceManager.getString(R.string.common_not_enough_funds_title),
    resourceManager.getString(R.string.pool_staking_unstake_error)
)

class MinPoolCreationThresholdException(resourceManager: ResourceManager, minToCreateAmount: String) : ValidationException(
    resourceManager.getString(R.string.min_to_create_pool_threshold_error_title),
    resourceManager.getString(R.string.min_to_create_pool_threshold_error_message, minToCreateAmount)
)

class TransferAddressNotValidException(resourceManager: ResourceManager) : ValidationException(
    resourceManager.getString(R.string.address_not_valid_error_title),
    resourceManager.getString(R.string.transfer_address_not_valid_error_message)
)

class AddressNotValidException(resourceManager: ResourceManager) : ValidationException(
    resourceManager.getString(R.string.address_not_valid_error_title),
    resourceManager.getString(R.string.address_not_valid_error_message)
)

class WaitForFeeCalculationException(resourceManager: ResourceManager) : ValidationException(
    resourceManager.getString(R.string.fee_not_yet_loaded_title),
    resourceManager.getString(R.string.fee_not_yet_loaded_message)
)

class FeeInsufficientBalanceException(resourceManager: ResourceManager) : ValidationException(
    resourceManager.getString(R.string.common_not_enough_funds_title),
    resourceManager.getString(R.string.common_not_enough_funds_message)
)

class AmountTooLowToStakeException(resourceManager: ResourceManager, minimumAmount: String) : ValidationException(
    resourceManager.getString(R.string.common_error_general_title),
    resourceManager.getString(R.string.staking_setup_amount_too_low, minimumAmount)
)

class UnableToPayFeeException(resourceManager: ResourceManager) : ValidationException(
    message = resourceManager.getString(R.string.common_error_general_title),
    explanation = resourceManager.getString(R.string.common_not_enough_funds_message)
)

class NotEnoughResultedAmountToPayFeeException(resourceManager: ResourceManager) : ValidationException(
    message = resourceManager.getString(R.string.polkaswap_not_enough_result_to_pay_fee_title),
    explanation = resourceManager.getString(R.string.polkaswap_not_enough_result_to_pay_fee_message)
)

/**
 * The relay chain still decodes the call but rejects it at dispatch, after charging the fee, since its pallet moved
 * to Asset Hub. fearless-utils refuses to build such a call ([CallMovedToAssetHubException]), so nothing was signed.
 */
class MovedToAssetHubException(resourceManager: ResourceManager) : ValidationException(
    resourceManager.getString(R.string.relay_moved_to_asset_hub_title),
    resourceManager.getString(R.string.relay_moved_to_asset_hub_message)
)

class FeeCalculationFailedException(resourceManager: ResourceManager) : ValidationException(
    resourceManager.getString(R.string.choose_amount_network_error),
    resourceManager.getString(R.string.choose_amount_error_fee)
)

fun Throwable.isCallMovedToAssetHub(): Boolean = generateSequence(this) { it.cause }.any { it is CallMovedToAssetHubException }

/** A failed fee estimation as the user reads it: why a call that moved to Asset Hub can't be sent, otherwise the generic fee error. */
fun Throwable.toFeeError(resourceManager: ResourceManager): ValidationException = when {
    isCallMovedToAssetHub() -> MovedToAssetHubException(resourceManager)
    this is ValidationException -> this
    else -> FeeCalculationFailedException(resourceManager)
}
