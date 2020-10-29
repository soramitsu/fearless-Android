package jp.co.soramitsu.feature_wallet_impl.presentation.receive.model

import java.io.File

data class QrSharingPayload(
    val qrFile: File,
    val shareMessage: String
)