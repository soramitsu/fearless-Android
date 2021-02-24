package jp.co.soramitsu.feature_account_impl.domain

import jp.co.soramitsu.core.model.CryptoType
import jp.co.soramitsu.core.model.Network
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_account_api.domain.model.ImportJsonData
import jp.co.soramitsu.feature_account_api.domain.model.Language
import jp.co.soramitsu.core.model.SecuritySource
import jp.co.soramitsu.feature_account_impl.domain.errors.NodeAlreadyExistsException
import jp.co.soramitsu.feature_account_impl.domain.errors.UnsupportedNetworkException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class AccountInteractorImpl(
    private val accountRepository: AccountRepository
) : AccountInteractor {
    override suspend fun getSecuritySource(accountAddress: String): SecuritySource {
        return accountRepository.getSecuritySource(accountAddress)
    }

    override suspend fun generateMnemonic(): List<String> {
        return accountRepository.generateMnemonic()
    }

    override fun getCryptoTypes(): List<CryptoType> {
        return accountRepository.getEncryptionTypes()
    }

    override suspend fun getPreferredCryptoType(): CryptoType {
        return accountRepository.getPreferredCryptoType()
    }

    override suspend fun createAccount(
        accountName: String,
        mnemonic: String,
        encryptionType: CryptoType,
        derivationPath: String,
        networkType: Node.NetworkType
    ): Result<Unit> {
        return runCatching {
            accountRepository.createAccount(
                accountName,
                mnemonic,
                encryptionType,
                derivationPath,
                networkType
            )
        }
    }

    override suspend fun importFromMnemonic(
        keyString: String,
        username: String,
        derivationPath: String,
        selectedEncryptionType: CryptoType,
        networkType: Node.NetworkType
    ): Result<Unit> {
        return runCatching {
            accountRepository.importFromMnemonic(
                keyString,
                username,
                derivationPath,
                selectedEncryptionType,
                networkType
            )
        }
    }

    override suspend fun importFromSeed(
        keyString: String,
        username: String,
        derivationPath: String,
        selectedEncryptionType: CryptoType,
        networkType: Node.NetworkType
    ): Result<Unit> {
        return runCatching {
            accountRepository.importFromSeed(
                keyString,
                username,
                derivationPath,
                selectedEncryptionType,
                networkType
            )
        }
    }

    override suspend fun importFromJson(
        json: String,
        password: String,
        networkType: Node.NetworkType,
        name: String
    ): Result<Unit> {
        return runCatching {
            accountRepository.importFromJson(json, password, networkType, name)
        }
    }

    override suspend fun isCodeSet(): Boolean {
        return accountRepository.isCodeSet()
    }

    override suspend fun savePin(code: String) {
        return accountRepository.savePinCode(code)
    }

    override suspend fun isPinCorrect(code: String): Boolean {
        val pinCode = accountRepository.getPinCode()

        return pinCode == code
    }

    override suspend fun isBiometricEnabled(): Boolean {
        return accountRepository.isBiometricEnabled()
    }

    override suspend fun setBiometricOn() {
        return accountRepository.setBiometricOn()
    }

    override suspend fun setBiometricOff() {
        return accountRepository.setBiometricOff()
    }

    override suspend fun getAccount(address: String): Account {
        return accountRepository.getAccount(address)
    }

    override fun selectedAccountFlow() = accountRepository.selectedAccountFlow()

    override suspend fun getSelectedAccount() = accountRepository.getSelectedAccount()

    override suspend fun getNetworks(): List<Network> {
        return accountRepository.getNetworks()
    }

    override suspend fun getSelectedNode() = accountRepository.getSelectedNode()

    override fun groupedAccountsFlow(): Flow<List<Any>> {
        return accountRepository.accountsFlow()
            .map(::mergeAccountsWithNetworks)
    }

    override suspend fun selectAccount(address: String) {
        val account = accountRepository.getAccount(address)

        selectAccount(account)
    }

    override suspend fun updateAccountName(account: Account, newName: String) {
        val newAccount = account.copy(name = newName)

        accountRepository.updateAccount(newAccount)

        maybeUpdateSelectedAccount(newAccount)
    }

    override suspend fun deleteAccount(address: String) {
        return accountRepository.deleteAccount(address)
    }

    override suspend fun updateAccountPositionsInNetwork(newOrdering: List<Account>) {
        val updatedAccounts = withContext(Dispatchers.Default) {
            newOrdering.mapIndexed { index, account -> account.copy(position = index) }
        }

        accountRepository.updateAccounts(updatedAccounts)
    }

    // TODO refactor - now logic relies on the implementation of AccountRepository
    //  (that after selecting account its info will be updated)
    private suspend fun maybeUpdateSelectedAccount(newAccount: Account) {
        val account = accountRepository.getSelectedAccount()

        if (account.address == newAccount.address) {
            accountRepository.selectAccount(newAccount)
        }
    }

    private suspend fun selectAccount(account: Account) {
        val node = accountRepository.getDefaultNode(account.network.type)

        accountRepository.selectNode(node)
        accountRepository.selectAccount(account)
    }

    private suspend fun mergeAccountsWithNetworks(accounts: List<Account>): List<Any> {
        return withContext(Dispatchers.Default) {
            accounts.groupBy { it.network.type }
                .map { (network, accounts) -> listOf(network, *accounts.toTypedArray()) }
                .flatten()
        }
    }

    override fun nodesFlow(): Flow<List<Node>> {
        return accountRepository.nodesFlow()
    }

    override fun selectedNodeFlow(): Flow<Node> {
        return accountRepository.selectedNodeFlow()
    }

    override suspend fun getNode(nodeId: Int): Node {
        return accountRepository.getNode(nodeId)
    }

    override suspend fun processAccountJson(json: String): Result<ImportJsonData> {
        return runCatching {
            accountRepository.processAccountJson(json)
        }
    }

    override fun getLanguages(): List<Language> {
        return accountRepository.getLanguages()
    }

    override suspend fun getSelectedLanguage(): Language {
        return accountRepository.selectedLanguage()
    }

    override suspend fun changeSelectedLanguage(language: Language) {
        return accountRepository.changeLanguage(language)
    }

    override suspend fun addNode(nodeName: String, nodeHost: String): Result<Unit> {
        return ensureUniqueNode(nodeHost) {
            val networkType = getNetworkTypeByNodeHost(nodeHost)

            accountRepository.addNode(nodeName, nodeHost, networkType)
        }
    }

    override suspend fun updateNode(nodeId: Int, newName: String, newHost: String): Result<Unit> {
        return ensureUniqueNode(newHost) {
            val networkType = getNetworkTypeByNodeHost(newHost)

            accountRepository.updateNode(nodeId, newName, newHost, networkType)
        }
    }

    private suspend fun ensureUniqueNode(nodeHost: String, action: suspend () -> Unit): Result<Unit> {
        val nodeExists = accountRepository.checkNodeExists(nodeHost)

        return runCatching {
            if (nodeExists) {
                throw NodeAlreadyExistsException()
            } else {
                action()
            }
        }
    }

    /**
     * @throws UnsupportedNetworkException, if node network is not supported
     * @throws FearlessException - in case of network issues
     */
    private suspend fun getNetworkTypeByNodeHost(nodeHost: String): Node.NetworkType {
        val networkName = accountRepository.getNetworkName(nodeHost)

        val supportedNetworks = Node.NetworkType.values()
        val networkType = supportedNetworks.firstOrNull { networkName == it.readableName }

        return networkType ?: throw UnsupportedNetworkException()
    }

    override suspend fun getAccountsByNetworkTypeWithSelectedNode(networkType: Node.NetworkType): Pair<List<Account>, Node> {
        val accounts = accountRepository.getAccountsByNetworkType(networkType)
        val node = accountRepository.getSelectedNode()
        return Pair(accounts, node)
    }

    override suspend fun selectNodeAndAccount(nodeId: Int, accountAddress: String) {
        val account = accountRepository.getAccount(accountAddress)
        val node = accountRepository.getNode(nodeId)

        accountRepository.selectNode(node)
        accountRepository.selectAccount(account)
    }

    override suspend fun selectNode(nodeId: Int) {
        val node = accountRepository.getNode(nodeId)

        accountRepository.selectNode(node)
    }

    override suspend fun deleteNode(nodeId: Int) {
        return accountRepository.deleteNode(nodeId)
    }

    override suspend fun generateRestoreJson(accountAddress: String, password: String): Result<String> {
        val account = accountRepository.getAccount(accountAddress)

        return runCatching {
            accountRepository.generateRestoreJson(account, password)
        }
    }
}