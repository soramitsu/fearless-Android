package jp.co.soramitsu.feature_account_impl.domain

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.BiFunction
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_account_api.domain.model.CryptoType
import jp.co.soramitsu.feature_account_api.domain.model.ImportJsonData
import jp.co.soramitsu.feature_account_api.domain.model.Network
import jp.co.soramitsu.feature_account_api.domain.model.Node

class AccountInteractorImpl(
    private val accountRepository: AccountRepository
) : AccountInteractor {
    override fun getMnemonic(): Single<List<String>> {
        return accountRepository.generateMnemonic()
    }

    override fun getCryptoTypes(): Single<List<CryptoType>> {
        return accountRepository.getEncryptionTypes()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
    }

    override fun getPreferredCryptoType(): Single<CryptoType> {
        return accountRepository.getPreferredCryptoType()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
    }

    override fun createAccount(
        accountName: String,
        mnemonic: String,
        encryptionType: CryptoType,
        derivationPath: String,
        node: Node
    ): Completable {
        return accountRepository.createAccount(
            accountName,
            mnemonic,
            encryptionType,
            derivationPath,
            node
        )
    }

    override fun importFromMnemonic(
        keyString: String,
        username: String,
        derivationPath: String,
        selectedEncryptionType: CryptoType,
        node: Node
    ): Completable {
        return accountRepository.importFromMnemonic(
            keyString,
            username,
            derivationPath,
            selectedEncryptionType,
            node
        )
    }

    override fun importFromSeed(
        keyString: String,
        username: String,
        derivationPath: String,
        selectedEncryptionType: CryptoType,
        node: Node
    ): Completable {
        return accountRepository.importFromSeed(
            keyString,
            username,
            derivationPath,
            selectedEncryptionType,
            node
        )
    }

    override fun importFromJson(
        json: String,
        password: String,
        name: String
    ): Completable {
        return accountRepository.importFromJson(json, password, name)
    }

    override fun getAddressId(account: Account): Single<ByteArray> {
        return accountRepository.getAddressId(account)
    }

    override fun getSelectedLanguage(): Single<String> {
        return Single.just("English")
    }

    override fun isCodeSet(): Single<Boolean> {
        return accountRepository.isCodeSet()
    }

    override fun savePin(code: String): Completable {
        return accountRepository.savePinCode(code)
    }

    override fun isPinCorrect(code: String): Single<Boolean> {
        return Single.fromCallable {
            val pinCode = accountRepository.getPinCode()
            pinCode == code
        }
    }

    override fun isBiometricEnabled(): Single<Boolean> {
        return accountRepository.isBiometricEnabled()
    }

    override fun setBiometricOn(): Completable {
        return accountRepository.setBiometricOn()
    }

    override fun setBiometricOff(): Completable {
        return accountRepository.setBiometricOff()
    }

    override fun getAccount(address: String): Single<Account> {
        return accountRepository.getAccount(address)
    }

    override fun observeSelectedAccount() = accountRepository.observeSelectedAccount()

    override fun getNetworks(): Single<List<Network>> {
        return accountRepository.getNetworks()
    }

    override fun getSelectedNode() = accountRepository.getSelectedNode()
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())

    override fun getSelectedNetwork(): Single<Network> {
        return getNetworks()
            .subscribeOn(Schedulers.io())
            .zipWith<Node, Network>(
                getSelectedNode(),
                BiFunction { networks, selectedNode ->
                    networks.first { it.type == selectedNode.networkType }
                })
            .observeOn(AndroidSchedulers.mainThread())
    }

    override fun shouldOpenOnboarding(): Single<Boolean> {
        return accountRepository.isAccountSelected()
            .map(Boolean::not)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
    }

    override fun observeGroupedAccounts(): Observable<List<Any>> {
        return accountRepository.observeAccounts()
            .map(::mergeAccountsWithNetworks)
    }

    override fun selectAccount(address: String): Completable {
        return accountRepository.getAccount(address)
            .subscribeOn(Schedulers.io())
            .flatMapCompletable(::selectAccount)
            .observeOn(AndroidSchedulers.mainThread())
    }

    override fun updateAccountName(account: Account, newName: String): Completable {
        val newAccount = account.copy(name = newName)

        return accountRepository.updateAccount(newAccount)
            .andThen(maybeUpdateSelectedAccount(newAccount))
    }

    override fun deleteAccount(address: String): Completable {
        return accountRepository.deleteAccount(address)
    }

    override fun updateAccountPositionsInNetwork(newOrdering: List<Account>): Completable {
        return Single.fromCallable {
            newOrdering.mapIndexed { index: Int, account: Account ->
                account.copy(position = index)
            }
        }.flatMapCompletable(accountRepository::updateAccounts)
    }

    private fun maybeUpdateSelectedAccount(newAccount: Account): Completable {
        return accountRepository.observeSelectedAccount()
            .firstOrError()
            .flatMapCompletable {
                if (it.address == newAccount.address) {
                    accountRepository.selectAccount(newAccount)
                } else {
                    Completable.complete()
                }
            }
    }

    private fun selectAccount(account: Account): Completable {
        return accountRepository.getDefaultNode(account.network.type)
            .flatMapCompletable(accountRepository::selectNode)
            .andThen(accountRepository.selectAccount(account))
    }

    private fun mergeAccountsWithNetworks(accounts: List<Account>): List<Any> {
        return accounts.groupBy(Account::network)
            .map { (network, accounts) -> listOf(network, *accounts.toTypedArray()) }
            .flatten()
    }

    override fun observeNodes(): Observable<List<Node>> {
        return accountRepository.observeNodes()
    }

    override fun observeSelectedNode(): Observable<Node> {
        return accountRepository.observeSelectedNode()
    }

    override fun getNode(nodeId: Int): Single<Node> {
        return accountRepository.getNode(nodeId)
    }

    override fun processAccountJson(json: String): Single<ImportJsonData> {
        return accountRepository.processAccountJson(json)
    }
}