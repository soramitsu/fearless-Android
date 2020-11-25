package jp.co.soramitsu.common.account.mnemonicViewer

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