package jp.co.soramitsu.splash.presentation

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.EventObserver
import jp.co.soramitsu.splash.di.SplashFeatureApi
import jp.co.soramitsu.splash.di.SplashFeatureComponent
import javax.inject.Inject

class SplashActivity : AppCompatActivity() {

    @Inject lateinit var splashViewModel: SplashViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        FeatureUtils.getFeature<SplashFeatureComponent>(this, SplashFeatureApi::class.java)
            .splashComponentFactory()
            .create(this)
            .inject(this)

        splashViewModel.openUsersEvent.observe(this, EventObserver {
            splashViewModel.openScanner(this)
            finish()
        })
    }
}