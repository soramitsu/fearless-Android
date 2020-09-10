package jp.co.soramitsu.app.main.presentation

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import jp.co.soramitsu.app.R
import jp.co.soramitsu.app.main.di.MainApi
import jp.co.soramitsu.app.main.di.MainComponent
import jp.co.soramitsu.app.main.navigation.Navigator
import jp.co.soramitsu.common.base.BaseActivity
import jp.co.soramitsu.common.di.FeatureUtils
import kotlinx.android.synthetic.main.activity_main.mainView
import kotlinx.android.synthetic.main.activity_main.navHost
import javax.inject.Inject

class MainActivity : BaseActivity<MainViewModel>() {

    @Inject
    lateinit var navigator: Navigator

    companion object {

        fun start(context: Context) {
            val intent = Intent(context, MainActivity::class.java)
            context.startActivity(intent)
        }
    }

    override fun inject() {
        FeatureUtils.getFeature<MainComponent>(this, MainApi::class.java)
            .mainActivityComponentFactory()
            .create(this)
            .inject(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        navigator.attachNavController(navController)

//        processJsonOpenIntent()
    }

    override fun onDestroy() {
        navigator.detachNavController(navController)
        super.onDestroy()
    }

    override fun layoutResource(): Int {
        return R.layout.activity_main
    }

    override fun initViews() {
    }

    override fun subscribe(viewModel: MainViewModel) {
    }

    private fun removeBackgroundImage() {
        mainView.setBackgroundResource(R.color.black)
    }

//    private fun processJsonOpenIntent() {
//        if (Intent.ACTION_VIEW == intent.action && intent.type != null) {
//            if ("application/json" == intent.type) {
//                val file = this.contentResolver.openInputStream(intent.data!!)
//                val content = file?.reader(Charsets.UTF_8)?.readText()
//                viewModel.jsonFileOpened(content)
//            }
//        }
//    }


    private val navController: NavController
        get() = NavHostFragment.findNavController(navHost)
}