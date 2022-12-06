package jp.co.soramitsu.staking.impl.presentation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import java.math.BigInteger
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.api.domain.ExistentialDepositUseCase
import jp.co.soramitsu.wallet.api.presentation.BaseConfirmViewModel
import jp.co.soramitsu.wallet.impl.domain.model.Asset

abstract class StakingConfirmViewModel(
    private val existentialDepositUseCase: ExistentialDepositUseCase,
    private val router: StakingRouter,
    resourceManager: ResourceManager,
    asset: Asset,
    chain: Chain,
    address: String,
    amountInPlanks: BigInteger? = null,
    @StringRes titleRes: Int,
    @StringRes additionalMessageRes: Int? = null,
    @DrawableRes customIcon: Int? = null,
    feeEstimator: suspend (BigInteger?) -> BigInteger,
    executeOperation: suspend (String, BigInteger?) -> Result<String>,
    accountNameProvider: suspend (String) -> String?,
    private val onOperationSuccess: () -> Unit,
    private val customSuccessMessage: String? = null
) : BaseConfirmViewModel(
    existentialDepositUseCase,
    resourceManager,
    asset,
    chain,
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
        router.openOperationSuccess(it, asset.token.configuration.chainId, customSuccessMessage)
    },
    {
        router.openAlert(it)
    }
)
