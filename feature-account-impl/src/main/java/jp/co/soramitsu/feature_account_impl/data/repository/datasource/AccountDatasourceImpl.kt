package jp.co.soramitsu.feature_account_impl.data.repository.datasource

import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences

class AccountDatasourceImpl(
    private val preferences: Preferences,
    private val encryptedPreferences: EncryptedPreferences
) : AccountDatasource