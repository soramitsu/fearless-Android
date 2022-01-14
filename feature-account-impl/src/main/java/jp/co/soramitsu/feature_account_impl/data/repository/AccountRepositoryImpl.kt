package jp.co.soramitsu.feature_account_impl.data.repository

import android.database.sqlite.SQLiteConstraintException
import jp.co.soramitsu.common.data.mappers.mapCryptoTypeToEncryption
import jp.co.soramitsu.common.data.mappers.mapEncryptionToCryptoType
import jp.co.soramitsu.common.data.secrets.v2.MetaAccountSecrets
import jp.co.soramitsu.common.data.secrets.v2.SecretStoreV2
import jp.co.soramitsu.common.resources.LanguagesHolder
import jp.co.soramitsu.common.utils.DEFAULT_DERIVATION_PATH
import jp.co.soramitsu.common.utils.deriveSeed32
import jp.co.soramitsu.common.utils.ethereumAddressFromPublicKey
import jp.co.soramitsu.common.utils.nullIfEmpty
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.core.model.CryptoType
import jp.co.soramitsu.core.model.JsonFormer
import jp.co.soramitsu.core.model.Language
import jp.co.soramitsu.core.model.Network
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.core.model.SecuritySource
import jp.co.soramitsu.core.model.WithJson
import jp.co.soramitsu.core.model.chainId
import jp.co.soramitsu.core_db.dao.AccountDao
import jp.co.soramitsu.core_db.dao.MetaAccountDao
import jp.co.soramitsu.core_db.model.AccountLocal
import jp.co.soramitsu.core_db.model.chain.MetaAccountLocal
import jp.co.soramitsu.fearless_utils.encrypt.MultiChainEncryption
import jp.co.soramitsu.fearless_utils.encrypt.json.JsonSeedDecoder
import jp.co.soramitsu.fearless_utils.encrypt.json.JsonSeedEncoder
import jp.co.soramitsu.fearless_utils.encrypt.junction.BIP32JunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.junction.SubstrateJunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.keypair.ethereum.EthereumKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.Mnemonic
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.MnemonicCreator
import jp.co.soramitsu.fearless_utils.encrypt.qr.QrSharing
import jp.co.soramitsu.fearless_utils.encrypt.seed.ethereum.EthereumSeedFactory
import jp.co.soramitsu.fearless_utils.encrypt.seed.substrate.SubstrateSeedFactory
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.addressByte
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountAlreadyExistsException
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_account_api.domain.model.AuthType
import jp.co.soramitsu.feature_account_api.domain.model.ImportJsonData
import jp.co.soramitsu.feature_account_api.domain.model.LightMetaAccount
import jp.co.soramitsu.feature_account_api.domain.model.MetaAccount
import jp.co.soramitsu.feature_account_api.domain.model.MetaAccountOrdering
import jp.co.soramitsu.feature_account_impl.data.repository.datasource.AccountDataSource
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.bouncycastle.util.encoders.Hex

class AccountRepositoryImpl(
    private val accountDataSource: AccountDataSource,
    private val accountDao: AccountDao,
    private val metaAccountDao: MetaAccountDao,
    private val storeV2: SecretStoreV2,
    private val jsonSeedDecoder: JsonSeedDecoder,
    private val jsonSeedEncoder: JsonSeedEncoder,
    private val languagesHolder: LanguagesHolder,
) : AccountRepository {

    override fun getEncryptionTypes(): List<CryptoType> {
        return CryptoType.values().toList()
    }

    override suspend fun selectAccount(metaAccountId: Long) {
        metaAccountDao.selectMetaAccount(metaAccountId)
    }

    // TODO remove
    override fun selectedAccountFlow(): Flow<Account> {
        return accountDataSource.selectedAccountMapping.map {
            it.getValue(Node.NetworkType.POLKADOT.chainId)!!
        }
    }

    // TODO remove
    override suspend fun getSelectedAccount(): Account {
        return getSelectedAccount(Node.NetworkType.POLKADOT.chainId)
    }

    override suspend fun getSelectedAccount(chainId: String): Account {
        return accountDataSource.selectedAccountMapping.first().getValue(chainId)!!
    }

    override suspend fun getSelectedMetaAccount(): MetaAccount {
        return accountDataSource.getSelectedMetaAccount()
    }

    override suspend fun getMetaAccount(metaId: Long): MetaAccount {
        return accountDataSource.getMetaAccount(metaId)
    }

    override fun selectedMetaAccountFlow(): Flow<MetaAccount> {
        return accountDataSource.selectedMetaAccountFlow()
    }

    override suspend fun findMetaAccount(accountId: ByteArray): MetaAccount? {
        return accountDataSource.findMetaAccount(accountId)
    }

    override suspend fun allMetaAccounts(): List<MetaAccount> {
        return accountDataSource.allMetaAccounts()
    }

    override fun lightMetaAccountsFlow(): Flow<List<LightMetaAccount>> {
        return accountDataSource.lightMetaAccountsFlow()
    }

    override suspend fun selectMetaAccount(metaId: Long) {
        return accountDataSource.selectMetaAccount(metaId)
    }

    override suspend fun updateMetaAccountName(metaId: Long, newName: String) {
        return accountDataSource.updateMetaAccountName(metaId, newName)
    }

    override suspend fun getPreferredCryptoType(): CryptoType {
        return accountDataSource.getPreferredCryptoType()
    }

    override suspend fun isAccountSelected(): Boolean {
        return accountDataSource.anyAccountSelected()
    }

    override suspend fun createAccount(
        accountName: String,
        mnemonic: String,
        encryptionType: CryptoType,
        derivationPath: String
    ) {
        val metaAccountId = saveFromMnemonic(
            accountName,
            mnemonic,
            derivationPath,
            encryptionType
        )

        selectAccount(metaAccountId)
    }

    override suspend fun deleteAccount(metaId: Long) {
        accountDataSource.deleteMetaAccount(metaId)
    }

    override suspend fun getAccounts(): List<Account> {
        return accountDao.getAccounts()
            .map { mapAccountLocalToAccount(it) }
    }

    override suspend fun getAccount(address: String): Account {
        val account = accountDao.getAccount(address) ?: throw NoSuchElementException("No account found for address $address")
        return mapAccountLocalToAccount(account)
    }

    override suspend fun getAccountOrNull(address: String): Account? {
        return accountDao.getAccount(address)?.let { mapAccountLocalToAccount(it) }
    }

    override suspend fun getMyAccounts(query: String, chainId: String): Set<Account> {
//        return withContext(Dispatchers.Default) {
//            accountDao.getAccounts(query, networkType)
//                .map { mapAccountLocalToAccount(it) }
//                .toSet()
//        }

        return emptySet() // TODO wallet
    }

    override suspend fun importFromMnemonic(
        keyString: String,
        username: String,
        derivationPath: String,
        selectedEncryptionType: CryptoType,
    ) {
        val metaAccountId = saveFromMnemonic(
            username,
            keyString,
            derivationPath,
            selectedEncryptionType
        )

        selectAccount(metaAccountId)
    }

    // todo add etherium support
    override suspend fun importFromSeed(
        seed: String,
        username: String,
        derivationPath: String,
        selectedEncryptionType: CryptoType,
    ) {
        return withContext(Dispatchers.Default) {
            val seedBytes = Hex.decode(seed.removePrefix("0x"))

            val derivationPathOrNull = derivationPath.nullIfEmpty()
            val decodedDerivationPath = derivationPathOrNull?.let {
                SubstrateJunctionDecoder.decode(it)
            }

            val keys = SubstrateKeypairFactory.generate(
                mapCryptoTypeToEncryption(selectedEncryptionType),
                seedBytes,
                decodedDerivationPath?.junctions.orEmpty()
            )

            val position = metaAccountDao.getNextPosition()

            val secretsV2 = MetaAccountSecrets(
                substrateKeyPair = keys,
                substrateDerivationPath = derivationPath,
                seed = seedBytes
            )

            val metaAccount = MetaAccountLocal(
                substratePublicKey = keys.publicKey,
                substrateAccountId = keys.publicKey.substrateAccountId(),
                substrateCryptoType = selectedEncryptionType,
                name = username,
                isSelected = true,
                position = position,
                ethereumAddress = null,
                ethereumPublicKey = null
            )

            val metaAccountId = insertAccount(metaAccount)
            storeV2.putMetaAccountSecrets(metaAccountId, secretsV2)
            selectAccount(metaAccountId)
        }
    }

    // todo add etherium support
    override suspend fun importFromJson(
        json: String,
        password: String,
        name: String,
    ) {
        return withContext(Dispatchers.Default) {
            val importData = jsonSeedDecoder.decode(json, password)

            val keys = importData.keypair

            val position = metaAccountDao.getNextPosition()

            val secretsV2 = MetaAccountSecrets(
                substrateKeyPair = importData.keypair,
                substrateDerivationPath = null,
                seed = importData.seed
            )

            val metaAccount = MetaAccountLocal(
                substratePublicKey = keys.publicKey,
                substrateAccountId = keys.publicKey.substrateAccountId(),
                substrateCryptoType = mapEncryptionToCryptoType(importData.multiChainEncryption.encryptionType),
                name = name,
                isSelected = true,
                position = position,
                ethereumAddress = null,
                ethereumPublicKey = null
            )

            val metaAccountId = insertAccount(metaAccount)
            storeV2.putMetaAccountSecrets(metaAccountId, secretsV2)

            selectAccount(metaAccountId)
        }
    }

    override suspend fun isCodeSet(): Boolean {
        return accountDataSource.getPinCode() != null
    }

    override suspend fun savePinCode(code: String) {
        return accountDataSource.savePinCode(code)
    }

    override suspend fun getPinCode(): String? {
        return accountDataSource.getPinCode()
    }

    override suspend fun generateMnemonic(): List<String> {
        return withContext(Dispatchers.Default) {
            val generationResult = MnemonicCreator.randomMnemonic(Mnemonic.Length.TWELVE)

            generationResult.wordList
        }
    }

    override suspend fun isBiometricEnabled(): Boolean {
        return accountDataSource.getAuthType() == AuthType.BIOMETRY
    }

    override suspend fun setBiometricOn() {
        return accountDataSource.saveAuthType(AuthType.BIOMETRY)
    }

    override suspend fun setBiometricOff() {
        return accountDataSource.saveAuthType(AuthType.PINCODE)
    }

    override suspend fun updateAccountsOrdering(accountOrdering: List<MetaAccountOrdering>) {
        return accountDataSource.updateAccountPositions(accountOrdering)
    }

    override suspend fun processAccountJson(json: String): ImportJsonData {
        return withContext(Dispatchers.Default) {
            val importAccountMeta = jsonSeedDecoder.extractImportMetaData(json)

            with(importAccountMeta) {
                val cryptoType = mapEncryptionToCryptoType(encryption.encryptionType)

                ImportJsonData(name, cryptoType)
            }
        }
    }

    override suspend fun getSecuritySource(accountAddress: String): SecuritySource {
        return accountDataSource.getSecuritySource(accountAddress)!!
    }

    override suspend fun generateRestoreJson(account: Account, password: String): String {
        return withContext(Dispatchers.Default) {
            val securitySource = getSecuritySource(account.address)
            require(securitySource is WithJson)

            val seed = (securitySource.jsonFormer() as? JsonFormer.Seed)?.seed

            val cryptoType = mapCryptoTypeToEncryption(account.cryptoType)
            val runtimeConfiguration = account.network.type.runtimeConfiguration

            jsonSeedEncoder.generate(
                keypair = securitySource.keypair,
                seed = seed,
                password = password,
                name = account.name.orEmpty(),
                multiChainEncryption = MultiChainEncryption.Substrate(cryptoType),
                genesisHash = runtimeConfiguration.genesisHash,
                address = securitySource.keypair.publicKey.toAddress(runtimeConfiguration.addressByte)
            )
        }
    }

    override suspend fun isAccountExists(accountId: AccountId): Boolean {
        return accountDataSource.accountExists(accountId)
    }

    override suspend fun isInCurrentNetwork(address: String, chainId: ChainId): Boolean {
        val currentAccount = getSelectedAccount(chainId)

        return try {
            val otherAddressByte = address.addressByte()
            val currentAddressByte = currentAccount.address.addressByte()

            address.toAccountId() // decoded without exception

            otherAddressByte == currentAddressByte
        } catch (_: Exception) {
            false
        }
    }

    override fun getLanguages(): List<Language> {
        return languagesHolder.getLanguages()
    }

    override suspend fun selectedLanguage(): Language {
        return accountDataSource.getSelectedLanguage()
    }

    override suspend fun changeLanguage(language: Language) {
        return accountDataSource.changeSelectedLanguage(language)
    }

    override fun createQrAccountContent(payload: QrSharing.Payload): String {
        return QrSharing.encode(payload)
    }

    private suspend fun saveFromMnemonic(
        accountName: String,
        mnemonicWords: String,
        derivationPath: String,
        cryptoType: CryptoType,
    ): Long {
        return withContext(Dispatchers.Default) {
            val derivationPathOrNull = derivationPath.nullIfEmpty()
            val decodedDerivationPath = derivationPathOrNull?.let {
                SubstrateJunctionDecoder.decode(it)
            }

            val derivationResult = SubstrateSeedFactory.deriveSeed32(mnemonicWords, decodedDerivationPath?.password)

            val keys = SubstrateKeypairFactory.generate(
                encryptionType = mapCryptoTypeToEncryption(cryptoType),
                seed = derivationResult.seed,
                decodedDerivationPath?.junctions.orEmpty()
            )

            val mnemonic = MnemonicCreator.fromWords(mnemonicWords)

            val ethereumDerivationPath = BIP32JunctionDecoder.DEFAULT_DERIVATION_PATH
            val decodedEthereumDerivationPath = BIP32JunctionDecoder.decode(ethereumDerivationPath)
            val ethereumSeed = EthereumSeedFactory.deriveSeed32(mnemonicWords, password = decodedEthereumDerivationPath.password).seed
            val ethereumKeypair = EthereumKeypairFactory.generate(ethereumSeed, junctions = decodedEthereumDerivationPath.junctions)
            val position = metaAccountDao.getNextPosition()

            val secretsV2 = MetaAccountSecrets(
                substrateKeyPair = keys,
                entropy = mnemonic.entropy,
                seed = null,
                substrateDerivationPath = derivationPath,
                ethereumKeypair = ethereumKeypair,
                ethereumDerivationPath = ethereumDerivationPath
            )

            val metaAccount = MetaAccountLocal(
                substratePublicKey = keys.publicKey,
                substrateAccountId = keys.publicKey.substrateAccountId(),
                substrateCryptoType = cryptoType,
                ethereumPublicKey = ethereumKeypair.publicKey,
                ethereumAddress = ethereumKeypair.publicKey.ethereumAddressFromPublicKey(),
                name = accountName,
                isSelected = true,
                position = position
            )

            val metaAccountId = insertAccount(metaAccount)
            storeV2.putMetaAccountSecrets(metaAccountId, secretsV2)

            metaAccountId
        }
    }

    private fun mapAccountLocalToAccount(accountLocal: AccountLocal): Account {
        val network = getNetworkForType(accountLocal.networkType)

        return with(accountLocal) {
            Account(
                address = address,
                name = username,
                accountIdHex = publicKey,
                cryptoType = CryptoType.values()[accountLocal.cryptoType],
                network = network,
                position = position
            )
        }
    }

    private suspend fun insertAccount(
        metaAccount: MetaAccountLocal
    ) = try {
        metaAccountDao.insertMetaAccount(metaAccount)
    } catch (e: SQLiteConstraintException) {
        throw AccountAlreadyExistsException()
    }

    private fun getNetworkForType(networkType: Node.NetworkType): Network {
        return Network(networkType)
    }
}
