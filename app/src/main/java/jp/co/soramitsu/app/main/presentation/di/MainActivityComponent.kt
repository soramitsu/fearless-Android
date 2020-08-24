package jp.co.soramitsu.app.main.presentation.di

import androidx.appcompat.app.AppCompatActivity
import dagger.BindsInstance
import dagger.Subcomponent
import jp.co.soramitsu.app.main.presentation.MainActivity
import jp.co.soramitsu.common.di.scope.ScreenScope

@Subcomponent(
    modules = [
        MainActivityModule::class
    ]
)
@ScreenScope
interface MainActivityComponent {

    @Subcomponent.Factory
    interface Factory {

        fun create(
            @BindsInstance activity: AppCompatActivity
        ): MainActivityComponent
    }

    fun inject(mainActivity: MainActivity)
}