package jp.co.soramitsu.app.root.presentation

internal const val FOCUS_SEARCH_ERROR_MESSAGE =
    "focus search returned a view that wasn't able to take focus!"

internal fun IllegalStateException.isFocusSearchFailure(): Boolean {
    val errorMessage = message ?: return false

    return errorMessage.contains(FOCUS_SEARCH_ERROR_MESSAGE, ignoreCase = true)
}
