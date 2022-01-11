package jp.co.soramitsu.feature_account_api.domain.model

import jp.co.soramitsu.core.model.CryptoType

class ImportJsonData(
    val name: String?,
    val encryptionType: CryptoType
)
