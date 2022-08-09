package jp.co.soramitsu.featureaccountimpl.presentation.language.mapper

import java.util.Locale
import jp.co.soramitsu.core.model.Language
import jp.co.soramitsu.featureaccountimpl.presentation.language.model.LanguageModel

fun mapLanguageToLanguageModel(language: Language): LanguageModel {
    val languageLocale = Locale(language.iso)
    return LanguageModel(
        language.iso,
        languageLocale.displayLanguage.capitalize(),
        languageLocale.getDisplayLanguage(languageLocale).capitalize()
    )
}
