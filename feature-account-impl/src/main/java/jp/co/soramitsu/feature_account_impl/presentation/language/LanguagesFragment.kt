package jp.co.soramitsu.feature_account_impl.presentation.language

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import jp.co.soramitsu.common.base.BaseActivity
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.di.AccountFeatureComponent
import jp.co.soramitsu.feature_account_impl.presentation.language.model.LanguageModel
import kotlinx.android.synthetic.main.fragment_accounts.fearlessToolbar
import kotlinx.android.synthetic.main.fragment_languages.languagesList

class LanguagesFragment : BaseFragment<LanguagesViewModel>(), LanguagesAdapter.LanguagesItemHandler {

    private lateinit var adapter: LanguagesAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = layoutInflater.inflate(R.layout.fragment_languages, container, false)

    override fun initViews() {
        adapter = LanguagesAdapter(this)

        languagesList.setHasFixedSize(true)
        languagesList.adapter = adapter

        fearlessToolbar.setHomeButtonListener {
            viewModel.backClicked()
        }
    }

    override fun inject() {
        FeatureUtils.getFeature<AccountFeatureComponent>(
            requireContext(),
            AccountFeatureApi::class.java
        )
            .languagesComponentFactory()
            .create(this)
            .inject(this)
    }

    override fun subscribe(viewModel: LanguagesViewModel) {
        viewModel.languagesLiveData.observe(adapter::submitList)

        viewModel.selectedLanguageLiveData.observe(adapter::updateSelectedLanguage)

        viewModel.languageChangedEvent.observeEvent {
            (activity as BaseActivity<*>).changeLanguage()
        }
    }

    override fun checkClicked(languageModel: LanguageModel) {
        viewModel.selectLanguageClicked(languageModel)
    }
}