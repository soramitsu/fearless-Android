package jp.co.soramitsu.staking.impl.presentation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import java.math.BigInteger
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.wallet.api.presentation.BaseConfirmViewModel
import jp.co.soramitsu.wallet.impl.domain.model.Asset

abstract class StakingConfirmViewModel(
    private val router: StakingRouter,
    resourceManager: ResourceManager,
    asset: Asset,
    address: String,
    amountInPlanks: BigInteger? = null,
    @StringRes titleRes: Int,
    @StringRes additionalMessageRes: Int? = null,
    @DrawableRes customIcon: Int? = null,
    feeEstimator: suspend (BigInteger?) -> BigInteger,
    executeOperation: suspend (String, BigInteger?) -> Result<String>,
    accountNameProvider: suspend (String) -> String?,
    private val onOperationSuccess: () -> Unit
) : BaseConfirmViewModel(
    resourceManager,
    asset,
    address,
    amountInPlanks,
    titleRes,
    additionalMessageRes,
    customIcon,
    feeEstimator,
    executeOperation,
    accountNameProvider,
    {
        onOperationSuccess()
        router.openOperationSuccess(it, asset.token.configuration.chainId)
    }
)
