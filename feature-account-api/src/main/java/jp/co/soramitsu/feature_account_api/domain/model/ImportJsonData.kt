package jp.co.soramitsu.feature_account_api.domain.model

class ImportJsonData(
    val name: String?,
    val networkType: Node.NetworkType?,
    val encryptionType: CryptoType
)