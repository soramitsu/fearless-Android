package jp.co.soramitsu.feature_account_impl.presentation.view.mnemonic

data class MnemonicWordModel(
    val numberToShow: String,
    val word: String
)

fun mapMnemonicToMnemonicWords(mnemonic: List<String>): List<MnemonicWordModel> {
    return mnemonic.mapIndexed { index: Int, word: String ->
        MnemonicWordModel(
            (index + 1).toString(),
            word
        )
    }
}