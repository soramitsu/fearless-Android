package jp.co.soramitsu.feature_account_impl.presentation.exporting.json.password.di

import androidx.fragment.app.Fragment
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.common.di.scope.ScreenScope
import jp.co.soramitsu.feature_account_impl.presentation.exporting.json.password.ExportJsonPasswordFragment

@Subcomponent(
    modules = [
        ExportJsonPasswordModule::class
    ]
)
@ScreenScope
interface ExportJsonPasswordComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(
            @BindsInstance fragment: Fragment,
            @BindsInstance accountAddress: String
        ): ExportJsonPasswordComponent
    }

    fun inject(fragment: ExportJsonPasswordFragment)
}