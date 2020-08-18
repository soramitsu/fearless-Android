package jp.co.soramitsu.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.navigation.fragment.NavHostFragment
import jp.co.soramitsu.app.di.deps.findComponentDependencies
import jp.co.soramitsu.app.di.main.MainComponent
import jp.co.soramitsu.app.navigation.Navigator
import jp.co.soramitsu.common.base.BaseActivity
import jp.co.soramitsu.common.interfaces.BackButtonListener
import javax.inject.Inject

class MainActivity : BaseActivity<MainViewModel>() {

    companion object {

        fun start(context: Context) {
            val intent = Intent(context, MainActivity::class.java)
            context.startActivity(intent)
        }
    }

    @Inject lateinit var navigator: Navigator

    private var navController: NavController? = null

    override fun inject() {
        MainComponent
            .init(this, findComponentDependencies())
            .inject(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        processJsonOpenIntent()
    }

    private fun processJsonOpenIntent() {
        if (Intent.ACTION_VIEW == intent.action && intent.type != null) {
            if ("application/json" == intent.type) {
                val file = this.contentResolver.openInputStream(intent.data!!)
                val content = file?.reader(Charsets.UTF_8)?.readText()
                viewModel.jsonFileOpened(content)
            }
        }
    }

    override fun layoutResource(): Int {
        return R.layout.activity_main
    }

    override fun initViews() {
        navController = Navigation.findNavController(this, R.id.nav_host_fragment)
        navigator.attachNavController(navController!!, R.navigation.main_nav_graph)
    }

    override fun subscribe(viewModel: MainViewModel) {
    }

    override fun onDestroy() {
        super.onDestroy()
        navController?.let {
            navigator.detachNavController(it)
        }
    }

    override fun onBackPressed() {
        val navHostFragment = supportFragmentManager.fragments[0] as NavHostFragment?

        navHostFragment?.childFragmentManager?.let {
            if (it.fragments.isNotEmpty()) {
                with(it.fragments.last()) {
                    if (this is BackButtonListener) {
                        onBackButtonPressed()
                    } else {
                        super.onBackPressed()
                    }
                }
            }
        }
    }
}