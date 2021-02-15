package jp.co.soramitsu.feature_account_api.domain.model

import jp.co.soramitsu.domain.model.CryptoType
import jp.co.soramitsu.domain.model.Node

class ImportJsonData(
    val name: String?,
    val networkType: Node.NetworkType?,
    val encryptionType: CryptoType
)