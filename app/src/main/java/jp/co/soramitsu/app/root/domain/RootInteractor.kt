package jp.co.soramitsu.app.root.domain

import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository

class RootInteractor(
    private val accountRepository: AccountRepository
) {
    fun observeSelectedNode() = accountRepository.observeSelectedNode()
}