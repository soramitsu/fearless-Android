package jp.co.soramitsu.feature_account_impl.presentation.language

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.plusAssign
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_api.domain.model.Language
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_account_impl.presentation.language.mapper.mapLanguageToLanguageModel
import jp.co.soramitsu.feature_account_impl.presentation.language.model.LanguageModel

class LanguagesViewModel(
    private val interactor: AccountInteractor,
    private val router: AccountRouter
) : BaseViewModel() {

    val languagesLiveData = getLanguages()
        .asLiveData()

    val selectedLanguageLiveData = getSelectedLanguageModel()
        .asMutableLiveData()

    private val _languageChangedEvent = MutableLiveData<Event<Unit>>()
    val languageChangedEvent: LiveData<Event<Unit>> = _languageChangedEvent

    fun backClicked() {
        router.back()
    }

    private fun getSelectedLanguageModel() = interactor.getSelectedLanguage()
        .subscribeOn(Schedulers.computation())
        .map(::mapLanguageToLanguageModel)
        .observeOn(AndroidSchedulers.mainThread())

    private fun getLanguages() = interactor.observeLanguages()
        .subscribeOn(Schedulers.computation())
        .map { it.map(::mapLanguageToLanguageModel) }
        .observeOn(AndroidSchedulers.mainThread())

    fun selectLanguageClicked(languageModel: LanguageModel) {
        disposables += interactor.changeSelectedLanguage(Language(languageModel.iso))
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                _languageChangedEvent.value = Event(Unit)
            }, {
                it.message?.let { showError(it) }
            })
    }
}