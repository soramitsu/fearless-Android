package jp.co.soramitsu.feature_account_impl.presentation.mnemonic.confirm

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.EventObserver
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.di.AccountFeatureComponent
import jp.co.soramitsu.feature_account_impl.presentation.mnemonic.confirm.view.MnemonicWordView
import kotlinx.android.synthetic.main.fragment_backup_mnemonic.toolbar
import kotlinx.android.synthetic.main.fragment_confirm_mnemonic.confirmationMnemonicView
import kotlinx.android.synthetic.main.fragment_confirm_mnemonic.nextBtn
import kotlinx.android.synthetic.main.fragment_confirm_mnemonic.wordsMnemonicView

class ConfirmMnemonicFragment : BaseFragment<ConfirmMnemonicViewModel>() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_confirm_mnemonic, container, false)
    }

    override fun initViews() {
        toolbar.setHomeButtonListener {
            viewModel.homeButtonClicked()
        }

        toolbar.setRightIconClickListener {
            viewModel.resetConfirmationClicked()
        }

        confirmationMnemonicView.setOnClickListener {
            viewModel.removeLastWordFromConfirmation()
        }

        confirmationMnemonicView.disableWordDisappearAnimation()

        nextBtn.setOnClickListener {
            viewModel.nextButtonClicked()
        }
    }

    override fun inject() {
        FeatureUtils.getFeature<AccountFeatureComponent>(context!!, AccountFeatureApi::class.java)
            .confirmMnemonicComponentFactory()
            .create(this)
            .inject(this)
    }

    override fun subscribe(viewModel: ConfirmMnemonicViewModel) {
        observe(viewModel.mnemonicLiveData, Observer {
            populateMnemonicContainer(it)
        })

        observe(viewModel.resetConfirmationEvent, EventObserver {
            confirmationMnemonicView.resetView()
            wordsMnemonicView.restoreAllWords()
        })

        observe(viewModel.removeLastWordFromConfirmationEvent, EventObserver {
            confirmationMnemonicView.removeLastWord()
            wordsMnemonicView.restoreLastWord()
        })

        observe(viewModel.nextButtonEnableLiveData, Observer {
            nextBtn.isEnabled = it
        })
    }

    private fun populateMnemonicContainer(mnemonicWords: List<String>) {
        val words = mnemonicWords.map { mnemonicWord ->
            MnemonicWordView(activity!!).apply {
                setWord(mnemonicWord)
                setColorMode(MnemonicWordView.ColorMode.LIGHT)
                setOnClickListener { wordClickListener(this, mnemonicWord) }
                measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
            }
        }

        wordsMnemonicView.populateWithMnemonic(words)

        val containerHeight = wordsMnemonicView.getMinimumMeasuredHeight()
        wordsMnemonicView.minimumHeight = containerHeight
        confirmationMnemonicView.minimumHeight = containerHeight
    }

    private val wordClickListener: (MnemonicWordView, String) -> Unit = { mnemonicWordView, word ->
        viewModel.addWordToConfirmMnemonic(word)

        wordsMnemonicView.removeWordView(mnemonicWordView)

        val wordView = MnemonicWordView(activity!!).apply {
            setWord(word)
            setColorMode(MnemonicWordView.ColorMode.DARK)
            measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        }
        confirmationMnemonicView.populateWord(wordView)
    }
}