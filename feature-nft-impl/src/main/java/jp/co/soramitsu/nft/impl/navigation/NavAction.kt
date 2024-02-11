package jp.co.soramitsu.nft.impl.navigation

sealed interface NavAction {

    object BackPressed : NavAction

    object QRCodeScanner : NavAction

    class ShowError(
        val errorTitle: String?,
        val errorText: String
    ) : NavAction

    @JvmInline
    value class ShowToast(
        val toastMessage: String
    ) : NavAction

    @JvmInline
    value class ShareText(
        val text: String
    ) : NavAction
}
