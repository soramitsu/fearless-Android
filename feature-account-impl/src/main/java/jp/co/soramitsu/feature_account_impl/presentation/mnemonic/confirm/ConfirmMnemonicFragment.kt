package jp.co.soramitsu.feature_account_impl.presentation.mnemonic.confirm

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.lifecycle.Observer
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.EventObserver
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_account_api.domain.model.CryptoType
import jp.co.soramitsu.feature_account_api.domain.model.NetworkType
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.di.AccountFeatureComponent
import jp.co.soramitsu.feature_account_impl.presentation.mnemonic.confirm.view.MnemonicWordView
import kotlinx.android.synthetic.main.fragment_backup_mnemonic.toolbar
import kotlinx.android.synthetic.main.fragment_confirm_mnemonic.confirmationMnemonicView
import kotlinx.android.synthetic.main.fragment_confirm_mnemonic.mnemonicViewsContainer
import kotlinx.android.synthetic.main.fragment_confirm_mnemonic.nextBtn
import kotlinx.android.synthetic.main.fragment_confirm_mnemonic.wordsMnemonicView

class ConfirmMnemonicFragment : BaseFragment<ConfirmMnemonicViewModel>() {

    companion object {
        private const val KEY_ACCOUNT_NAME = "account_name"
        private const val KEY_MNEMONIC = "mnemonic"
        private const val KEY_CRYPTO_TYPE = "crypto_type"
        private const val KEY_NETWORK_TYPE = "network_type"
        private const val KEY_DERIVATION_PATH = "derivation_path"

        fun getBundle(
            accountName: String,
            mnemonic: List<String>,
            cryptoType: CryptoType,
            networkType: NetworkType,
            derivationPath: String
        ): Bundle {

            return Bundle().apply {
                putString(KEY_ACCOUNT_NAME, accountName)
                putStringArray(KEY_MNEMONIC, mnemonic.toTypedArray())
                putSerializable(KEY_CRYPTO_TYPE, cryptoType)
                putSerializable(KEY_NETWORK_TYPE, networkType)
                putString(KEY_DERIVATION_PATH, derivationPath)
            }
        }
    }

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
        val mnemonic = arguments!!.getStringArray(KEY_MNEMONIC)!!.toList()
        val accountName = arguments!!.getString(KEY_ACCOUNT_NAME)!!
        val cryptoType = arguments!!.getSerializable(KEY_CRYPTO_TYPE) as CryptoType
        val networkType = arguments!!.getSerializable(KEY_NETWORK_TYPE) as NetworkType
        val derivationPath = arguments!!.getString(KEY_DERIVATION_PATH)!!

        FeatureUtils.getFeature<AccountFeatureComponent>(context!!, AccountFeatureApi::class.java)
            .confirmMnemonicComponentFactory()
            .create(this, mnemonic, accountName, cryptoType, networkType, derivationPath)
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

        observe(viewModel.matchingMnemonicErrorAnimationEvent, EventObserver {
            playMatchingMnemonicErrorAnimation()
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

    private fun playMatchingMnemonicErrorAnimation() {
        val animation = AnimationUtils.loadAnimation(activity!!, R.anim.shake)
        animation.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationRepeat(animation: Animation?) {
            }

            override fun onAnimationStart(animation: Animation?) {
            }

            override fun onAnimationEnd(animation: Animation?) {
                viewModel.matchingErrorAnimationCompleted()
            }
        })
        mnemonicViewsContainer.startAnimation(animation)
    }
}