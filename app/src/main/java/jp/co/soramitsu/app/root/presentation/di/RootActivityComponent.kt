package jp.co.soramitsu.app.root.presentation.di

import androidx.appcompat.app.AppCompatActivity
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.app.root.presentation.RootActivity
import jp.co.soramitsu.common.di.scope.ScreenScope

@Subcomponent(
    modules = [
        RootActivityModule::class
    ]
)
@ScreenScope
interface RootActivityComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(
            @BindsInstance activity: AppCompatActivity
        ): RootActivityComponent
    }

    fun inject(rootActivity: RootActivity)
}