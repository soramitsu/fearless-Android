package jp.co.soramitsu.staking.impl.presentation.mappers

import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.common.address.createAddressModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.formatAsPercentage
import jp.co.soramitsu.common.utils.formatCryptoDetail
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.utils.fractionToPercentage
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.runtime.ext.addressOf
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.shared_utils.extensions.fromHex
import jp.co.soramitsu.staking.api.domain.model.NominatedValidator
import jp.co.soramitsu.staking.api.domain.model.Validator
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.sortings.BlockProducersSorting
import jp.co.soramitsu.staking.impl.presentation.validators.change.ValidatorModel
import jp.co.soramitsu.staking.impl.presentation.validators.details.model.ValidatorDetailsModel
import jp.co.soramitsu.staking.impl.presentation.validators.details.model.ValidatorStakeModel
import jp.co.soramitsu.staking.impl.presentation.validators.details.model.ValidatorStakeModel.ActiveStakeModel
import jp.co.soramitsu.staking.impl.presentation.validators.details.view.Error
import jp.co.soramitsu.staking.impl.presentation.validators.parcel.ValidatorDetailsParcelModel
import jp.co.soramitsu.staking.impl.presentation.validators.parcel.ValidatorStakeParcelModel
import jp.co.soramitsu.staking.impl.presentation.validators.parcel.ValidatorStakeParcelModel.Active.NominatorInfo
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.Token
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks

val PERCENT_MULTIPLIER = 100.toBigDecimal()

private const val ICON_SIZE_DP = 24
private const val ICON_DETAILS_SIZE_DP = 32

suspend fun mapValidatorToValidatorModel(
    chain: Chain,
    validator: Validator,
    iconGenerator: AddressIconGenerator,
    token: Token,
    isChecked: Boolean? = null,
    sorting: BlockProducersSorting<Validator> = BlockProducersSorting.ValidatorSorting.APYSorting
) = mapValidatorToValidatorModel(
    chain,
    validator,
    { iconGenerator.createAddressModel(it, ICON_SIZE_DP, validator.identity?.display) },
    token,
    isChecked,
    sorting
)

suspend fun mapValidatorToValidatorModel(
    chain: Chain,
    validator: Validator,
    createIcon: suspend (address: String) -> AddressModel,
    token: Token,
    isChecked: Boolean? = null,
    sorting: BlockProducersSorting<Validator> = BlockProducersSorting.ValidatorSorting.APYSorting
): ValidatorModel {
    val address = chain.addressOf(validator.accountIdHex.fromHex())
    val addressModel = createIcon(address)

    return with(validator) {
        val scoring = sorting.toScoring(this, token)

        ValidatorModel(
            accountIdHex = accountIdHex,
            slashed = slashed,
            image = addressModel.image,
            address = addressModel.address,
            scoring = scoring,
            title = addressModel.nameOrAddress,
            isChecked = isChecked,
            validator = validator
        )
    }
}

fun mapValidatorToValidatorDetailsParcelModel(
    validator: Validator
): ValidatorDetailsParcelModel {
    return mapValidatorToValidatorDetailsParcelModel(validator, nominationStatus = null)
}

fun mapValidatorToValidatorDetailsWithStakeFlagParcelModel(
    nominatedValidator: NominatedValidator
): ValidatorDetailsParcelModel = mapValidatorToValidatorDetailsParcelModel(nominatedValidator.validator, nominatedValidator.status)

private fun mapValidatorToValidatorDetailsParcelModel(
    validator: Validator,
    nominationStatus: NominatedValidator.Status?
): ValidatorDetailsParcelModel {
    return with(validator) {
        val identityModel = identity?.let(::mapIdentityToIdentityParcelModel)

        val stakeModel = electedInfo?.let {
            val nominators = it.nominatorStakes.map(::mapNominatorToNominatorParcelModel)

            val nominatorInfo = (nominationStatus as? NominatedValidator.Status.Active)?.let { activeStatus ->
                NominatorInfo(willBeRewarded = activeStatus.willUserBeRewarded)
            }

            ValidatorStakeParcelModel.Active(
                totalStake = it.totalStake,
                ownStake = it.ownStake,
                nominators = nominators,
                apy = it.apy,
                isSlashed = slashed,
                isOversubscribed = it.isOversubscribed,
                nominatorInfo = nominatorInfo
            )
        } ?: ValidatorStakeParcelModel.Inactive

        ValidatorDetailsParcelModel(accountIdHex, stakeModel, identityModel)
    }
}

// FIXME Wrong logic for isOversubscribed & isSlashed - should not require elected state/nominator info
fun mapValidatorDetailsToErrors(
    validator: ValidatorDetailsParcelModel
): List<Error>? {
    return when (val stake = validator.stake) {
        ValidatorStakeParcelModel.Inactive -> null
        is ValidatorStakeParcelModel.Active -> {
            val nominatorInfo = stake.nominatorInfo ?: return null

            return mutableListOf<Error>().apply {
                if (stake.isOversubscribed) {
                    if (nominatorInfo.willBeRewarded) {
                        add(Error.OversubscribedPaid)
                    } else {
                        add(Error.OversubscribedUnpaid)
                    }
                }
                if (stake.isSlashed) add(Error.Slashed)
            }
        }
    }
}

suspend fun mapValidatorDetailsParcelToValidatorDetailsModel(
    chain: Chain,
    validator: ValidatorDetailsParcelModel,
    asset: Asset,
    maxNominators: Int,
    iconGenerator: AddressIconGenerator,
    resourceManager: ResourceManager
): ValidatorDetailsModel {
    return with(validator) {
        val token = asset.token

        val address = chain.addressOf(validator.accountIdHex.fromHex())

        val addressImage = iconGenerator.createAddressModel(address, ICON_DETAILS_SIZE_DP)

        val identity = identity?.let(::mapIdentityParcelModelToIdentityModel)

        val stake = when (val stake = validator.stake) {
            ValidatorStakeParcelModel.Inactive -> ValidatorStakeModel(
                statusText = resourceManager.getString(R.string.staking_nominator_status_inactive),
                statusColorRes = R.color.gray2,
                activeStakeModel = null
            )

            is ValidatorStakeParcelModel.Active -> {
                val totalStake = token.amountFromPlanks(stake.totalStake)
                val totalStakeFormatted = totalStake.formatCryptoDetail(asset.token.configuration.symbol)
                val totalStakeFiatFormatted = token.fiatAmount(totalStake)?.formatFiat(token.fiatSymbol)
                val nominatorsCount = stake.nominators.size
                val apyPercentageFormatted = (PERCENT_MULTIPLIER * stake.apy).formatAsPercentage()

                ValidatorStakeModel(
                    statusText = resourceManager.getString(R.string.staking_nominator_status_active),
                    statusColorRes = R.color.green,
                    activeStakeModel = ActiveStakeModel(
                        totalStake = totalStakeFormatted,
                        totalStakeFiat = totalStakeFiatFormatted,
                        nominatorsCount = nominatorsCount.toString(),
                        apy = apyPercentageFormatted,
                        maxNominations = maxNominators.toString()
                    )
                )
            }
        }

        ValidatorDetailsModel(
            stake,
            address,
            addressImage.image,
            identity
        )
    }
}

fun BlockProducersSorting<Validator>.toScoring(validator: Validator, token: Token): ValidatorModel.Scoring {
    return when (this) {
        BlockProducersSorting.ValidatorSorting.APYSorting -> {
            val apy = validator.electedInfo?.apy ?: BigDecimal.ZERO
            ValidatorModel.Scoring.OneField(apy.fractionToPercentage().formatAsPercentage())
        }
        BlockProducersSorting.ValidatorSorting.TotalStakeSorting -> {
            val totalCountedFormatted = token.amountFromPlanks(validator.electedInfo?.totalStake ?: BigInteger.ZERO)
            ValidatorModel.Scoring.TwoFields(
                totalCountedFormatted.formatCryptoDetail(token.configuration.symbol),
                token.fiatAmount(totalCountedFormatted)?.formatFiat(token.fiatSymbol)
            )
        }
        BlockProducersSorting.ValidatorSorting.ValidatorOwnStakeSorting -> {
            val totalCountedFormatted = token.amountFromPlanks(validator.electedInfo?.ownStake ?: BigInteger.ZERO)
            ValidatorModel.Scoring.TwoFields(
                totalCountedFormatted.formatCryptoDetail(token.configuration.symbol),
                token.fiatAmount(totalCountedFormatted)?.formatFiat(token.fiatSymbol)
            )
        }
        else -> error("Wrong sorting type")
    }
}
