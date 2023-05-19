package jp.co.soramitsu.account.impl.presentation.language.mapper

import jp.co.soramitsu.account.impl.presentation.language.model.LanguageModel
import jp.co.soramitsu.core.model.Language
import java.util.Locale

fun mapLanguageToLanguageModel(language: Language): LanguageModel {
    val languageLocale = Locale(language.iso)
    return LanguageModel(
        language.iso,
        languageLocale.displayLanguage.replaceFirstChar { it.uppercase() },
        languageLocale.getDisplayLanguage(languageLocale).replaceFirstChar { it.uppercase() }
    )
}
