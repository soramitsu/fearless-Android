package jp.co.soramitsu.feature_wallet_impl.presentation.balance.manageAssets.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_wallet_impl.presentation.balance.manageAssets.ManageAssetsFragment

@Subcomponent(
    modules = [
        ManageAssetsModule::class
    ]
)
@ScreenScope
interface ManageAssetsComponent {
    @Subcomponent.Factory
    interface Factory {

        fun create(
            @BindsInstance fragment: Fragment
        ): ManageAssetsComponent
    }

    fun inject(fragment: ManageAssetsFragment)
}
