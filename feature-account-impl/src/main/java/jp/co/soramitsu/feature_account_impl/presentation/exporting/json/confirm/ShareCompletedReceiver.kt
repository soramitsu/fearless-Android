package jp.co.soramitsu.feature_account_impl.presentation.exporting.json.confirm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_account_impl.di.AccountFeatureComponent
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import javax.inject.Inject

class ShareCompletedReceiver : BroadcastReceiver() {

    @Inject
    lateinit var router: AccountRouter

    override fun onReceive(context: Context, intent: Intent) {
        FeatureUtils.getFeature<AccountFeatureComponent>(context, AccountFeatureApi::class.java)
                .inject(this)

        router.finishExportFlow()
    }
}