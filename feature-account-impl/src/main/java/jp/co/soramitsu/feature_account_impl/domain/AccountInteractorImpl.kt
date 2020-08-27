package jp.co.soramitsu.feature_account_impl.domain

import io.reactivex.Completable
import io.reactivex.Single
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.model.CryptoType
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_account_api.domain.model.NetworkType
import jp.co.soramitsu.feature_account_api.domain.model.SourceType

class AccountInteractorImpl(
    private val accountRepository: AccountRepository
) : AccountInteractor {

    override fun getMnemonic(): Single<List<String>> {
        return accountRepository.generateMnemonic()
    }

    override fun getSourceTypesWithSelected(): Single<Pair<List<SourceType>, SourceType>> {
        return accountRepository.getSourceTypes()
            .flatMap {
                Single.fromCallable {
                    Pair(it, it.first())
                }
            }
    }

    override fun getEncryptionTypesWithSelected(): Single<Pair<List<CryptoType>, CryptoType>> {
        return accountRepository.getEncryptionTypes()
            .flatMap { encryptionTypes ->
                accountRepository.getSelectedEncryptionType()
                    .map { Pair(encryptionTypes, it) }
            }
    }

    override fun getNodesWithSelected(): Single<Pair<List<Node>, Node>> {
        return accountRepository.getNodes()
            .flatMap { nodes ->
                accountRepository.getSelectedNode()
                    .map { Pair(nodes, it) }
            }
    }

    override fun createAccount(accountName: String, mnemonic: String, encryptionType: CryptoType, derivationPath: String, node: Node): Completable {
        return accountRepository.createAccount(accountName, mnemonic, encryptionType, derivationPath, node)
    }

    override fun importFromMnemonic(keyString: String, username: String, derivationPath: String, selectedEncryptionType: CryptoType, node: Node): Completable {
        return accountRepository.importFromMnemonic(keyString, username, derivationPath, selectedEncryptionType, node)
    }

    override fun importFromSeed(keyString: String, username: String, derivationPath: String, selectedEncryptionType: CryptoType, node: Node): Completable {
        return accountRepository.importFromSeed(keyString, username, derivationPath, selectedEncryptionType, node)
    }

    override fun importFromJson(json: String, password: String, node: NetworkType): Completable {
        return accountRepository.importFromJson(json, password, node)
    }

    override fun getAddressId(): Single<ByteArray> {
        return accountRepository.getAddressId()
    }

    override fun getSelectedLanguage(): Single<String> {
        return Single.just("English")
    }

    override fun getAddress(): Single<String> {
        return accountRepository.getAddress()
    }

    override fun getUsername(): Single<String> {
        return accountRepository.getUsername()
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
}