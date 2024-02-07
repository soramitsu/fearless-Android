package jp.co.soramitsu.nft.impl.navigation

sealed interface NavAction {

    object BackPressed : NavAction

    object QRCodeScanner : NavAction

    @JvmInline
    value class ShowError(
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
