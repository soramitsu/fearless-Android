package jp.co.soramitsu.liquiditypools.navigation

sealed interface NavAction {
    object BackPressed : NavAction

    class ShowError(
        val errorTitle: String?,
        val errorText: String
    ) : NavAction

    class ShowInfo(
        val title: String,
        val message: String
    ) : NavAction
}