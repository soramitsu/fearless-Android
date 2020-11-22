package jp.co.soramitsu.feature_account_impl.data.mappers

import jp.co.soramitsu.feature_account_api.domain.model.SecuritySource
import jp.co.soramitsu.feature_account_impl.data.repository.datasource.SourceType

fun getSourceType(securitySource: SecuritySource): SourceType {
    return when (securitySource) {
        is SecuritySource.Specified.Create -> SourceType.CREATE
        is SecuritySource.Specified.Mnemonic -> SourceType.MNEMONIC
        is SecuritySource.Specified.Json -> SourceType.JSON
        is SecuritySource.Specified.Seed -> SourceType.SEED
        else -> SourceType.UNSPECIFIED
    }
}