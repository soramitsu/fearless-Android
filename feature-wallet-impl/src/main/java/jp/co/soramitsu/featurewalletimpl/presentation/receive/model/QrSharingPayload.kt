package jp.co.soramitsu.featurewalletimpl.presentation.receive.model

import java.io.File

data class QrSharingPayload(
    val qrFile: File,
    val shareMessage: String
)
