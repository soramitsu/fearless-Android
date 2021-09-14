package jp.co.soramitsu.feature_wallet_impl.presentation.transaction.detail.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_wallet_impl.presentation.model.OperationParcelizeModel
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.detail.extrinsic.ExtrinsicDetailFragment
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.detail.reward.RewardDetailFragment
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.detail.transfer.TransferDetailFragment

@Subcomponent(
    modules = [
        TransactionDetailModule::class,
    ]
)
@ScreenScope
interface TransactionDetailComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(
            @BindsInstance fragment: Fragment,
            @BindsInstance operation: OperationParcelizeModel.Transfer
        ): TransactionDetailComponent
    }

    fun inject(fragment: TransferDetailFragment)
}

@Subcomponent(
    modules = [
        RewardDetailModule::class
    ]
)
@ScreenScope
interface RewardDetailComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(
            @BindsInstance fragment: Fragment,
            @BindsInstance operation: OperationParcelizeModel.Reward
        ): RewardDetailComponent
    }

    fun inject(fragment: RewardDetailFragment)
}

@Subcomponent(
    modules = [
        ExtrinsicDetailModule::class
    ]
)
@ScreenScope
interface ExtrinsicDetailComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(
            @BindsInstance fragment: Fragment,
            @BindsInstance extrinsic: OperationParcelizeModel.Extrinsic
        ): ExtrinsicDetailComponent
    }

    fun inject(fragment: ExtrinsicDetailFragment)
}
