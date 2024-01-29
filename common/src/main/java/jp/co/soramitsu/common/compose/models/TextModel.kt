package jp.co.soramitsu.common.compose.models

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

sealed interface TextModel {

    @JvmInline
    value class SimpleString(
        val value: String
    ): TextModel

    @JvmInline
    value class ResId(
        val id: Int
    ): TextModel

    class ResIdWithArgs(
        val id: Int,
        val args: Array<Any>
    ): TextModel

}

@Composable
fun TextModel.retrieveString(): String {
    return when(this) {
        is TextModel.SimpleString -> value

        is TextModel.ResId ->
            stringResource(id = id)

        is TextModel.ResIdWithArgs ->
            stringResource(id = id, formatArgs =  args)
    }
}