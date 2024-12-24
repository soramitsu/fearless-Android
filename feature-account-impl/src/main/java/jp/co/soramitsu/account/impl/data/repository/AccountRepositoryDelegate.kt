package jp.co.soramitsu.account.impl.data.repository

import jp.co.soramitsu.account.api.domain.interfaces.AccountAlreadyExistsException
import jp.co.soramitsu.account.api.domain.model.AddAccountPayload
import jp.co.soramitsu.common.data.Keypair
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecretStore
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecrets
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecretStore
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.secrets.v3.TonSecretStore
import jp.co.soramitsu.common.data.secrets.v3.TonSecrets
import jp.co.soramitsu.common.utils.deriveSeed32
import jp.co.soramitsu.common.utils.ethereumAddressFromPublicKey
import jp.co.soramitsu.common.utils.nullIfEmpty
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.core.crypto.mapCryptoTypeToEncryption
import jp.co.soramitsu.coredb.dao.MetaAccountDao
import jp.co.soramitsu.coredb.model.MetaAccountLocal
import jp.co.soramitsu.shared_utils.encrypt.junction.BIP32JunctionDecoder
import jp.co.soramitsu.shared_utils.encrypt.junction.SubstrateJunctionDecoder
import jp.co.soramitsu.shared_utils.encrypt.keypair.ethereum.EthereumKeypairFactory
import jp.co.soramitsu.shared_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.shared_utils.encrypt.seed.ethereum.EthereumSeedFactory
import jp.co.soramitsu.shared_utils.encrypt.seed.substrate.SubstrateSeedFactory
import org.ton.api.pk.PrivateKeyEd25519

class AccountRepositoryDelegate(
    private val substrateOrEvmAccountRepository: SubstrateOrEvmAccountRepository,
    private val tonAccountRepository: TonAccountRepository
) {
    suspend fun create(payload: AddAccountPayload): Long {
        return when (payload) {
            is AddAccountPayload.SubstrateOrEvm -> substrateOrEvmAccountRepository.create(payload)
            is AddAccountPayload.Ton -> tonAccountRepository.create(payload)
            is AddAccountPayload.AdditionalEvm -> substrateOrEvmAccountRepository.createAdditional(payload)
        }
    }
}

class SubstrateOrEvmAccountRepository(
    private val metaAccountDao: MetaAccountDao,
    private val substrateSecretStore: SubstrateSecretStore,
    private val ethereumSecretStore: EthereumSecretStore
) {
    suspend fun createAdditional(payload: AddAccountPayload.AdditionalEvm): Long {
        val decodedEthereumDerivationPath =
            BIP32JunctionDecoder.decode(payload.ethereumDerivationPath)
        val ethereumSeedResult = EthereumSeedFactory.deriveSeed32(
            payload.mnemonic,
            password = decodedEthereumDerivationPath.password
        )
        val ethereumKeypair = EthereumKeypairFactory.generate(
            ethereumSeedResult.seed,
            junctions = decodedEthereumDerivationPath.junctions
        )
        val localMetaAccount = metaAccountDao.getMetaAccount(payload.walletId) ?: error("Account not exist")

        val metaAccount = MetaAccountLocal(
            substratePublicKey = localMetaAccount.substratePublicKey,
            substrateAccountId = localMetaAccount.substrateAccountId,
            substrateCryptoType = localMetaAccount.substrateCryptoType,
            ethereumPublicKey = ethereumKeypair.publicKey,
            ethereumAddress = ethereumKeypair.publicKey.ethereumAddressFromPublicKey(),
            tonPublicKey = null,
            name = localMetaAccount.name,
            isSelected = localMetaAccount.isSelected,
            position = localMetaAccount.position,
            isBackedUp = payload.isBackedUp,
            googleBackupAddress = localMetaAccount.googleBackupAddress,
            initialized = false,
        )

        metaAccount.id = payload.walletId

        metaAccountDao.updateMetaAccount(metaAccount)

        val ethereumSecrets = EthereumSecrets(
            entropy = ethereumSeedResult.mnemonic.entropy,
            seed = ethereumKeypair.privateKey,
            ethereumKeypair = ethereumKeypair,
            ethereumDerivationPath = payload.ethereumDerivationPath
        )

        ethereumSecretStore.put(payload.walletId, ethereumSecrets)

        return payload.walletId
    }

    suspend fun create(payload: AddAccountPayload.SubstrateOrEvm): Long {
        val substrateDerivationPathOrNull = payload.substrateDerivationPath.nullIfEmpty()
        val decodedDerivationPath = substrateDerivationPathOrNull?.let {
            SubstrateJunctionDecoder.decode(it)
        }

        val derivationResult = SubstrateSeedFactory.deriveSeed32(
            payload.mnemonic,
            decodedDerivationPath?.password
        )

        val keys = SubstrateKeypairFactory.generate(
            encryptionType = mapCryptoTypeToEncryption(payload.encryptionType),
            seed = derivationResult.seed,
            junctions = decodedDerivationPath?.junctions.orEmpty()
        )

        val decodedEthereumDerivationPath =
            BIP32JunctionDecoder.decode(payload.ethereumDerivationPath)
        val ethereumSeed = EthereumSeedFactory.deriveSeed32(
            payload.mnemonic,
            password = decodedEthereumDerivationPath.password
        ).seed
        val ethereumKeypair = EthereumKeypairFactory.generate(
            ethereumSeed,
            junctions = decodedEthereumDerivationPath.junctions
        )

        val position = metaAccountDao.getNextPosition()

        val metaAccount = MetaAccountLocal(
            substratePublicKey = keys.publicKey,
            substrateAccountId = keys.publicKey.substrateAccountId(),
            substrateCryptoType = payload.encryptionType,
            ethereumPublicKey = ethereumKeypair.publicKey,
            ethereumAddress = ethereumKeypair.publicKey.ethereumAddressFromPublicKey(),
            tonPublicKey = null,
            name = payload.accountName,
            isSelected = true,
            position = position,
            isBackedUp = payload.isBackedUp,
            googleBackupAddress = payload.googleBackupAddress,
            initialized = false,
        )

        val metaAccountId = try {
            metaAccountDao.insertMetaAccount(metaAccount)
        } catch (e: Throwable) {
            throw AccountAlreadyExistsException()
        }

        val substrateSecrets = SubstrateSecrets(
            substrateKeyPair = keys,
            substrateDerivationPath = payload.substrateDerivationPath,
            seed = derivationResult.seed,
            entropy = derivationResult.mnemonic.entropy
        )
        substrateSecretStore.put(metaAccountId, substrateSecrets)

        val ethereumSecrets = EthereumSecrets(
            entropy = derivationResult.mnemonic.entropy,
            seed = ethereumKeypair.privateKey,
            ethereumKeypair = ethereumKeypair,
            ethereumDerivationPath = payload.ethereumDerivationPath
        )

        ethereumSecretStore.put(metaAccountId, ethereumSecrets)

        return metaAccountId
    }
}

class TonAccountRepository(
    private val metaAccountDao: MetaAccountDao,
    private val tonSecretStore: TonSecretStore
) {
    suspend fun create(payload: AddAccountPayload.Ton): Long {
        val tonSeed = org.ton.mnemonic.Mnemonic.toSeed(payload.mnemonic.split(" "))
        val tonPrivateKey = PrivateKeyEd25519(tonSeed)
        val tonPublicKey = tonPrivateKey.publicKey()

        val position = metaAccountDao.getNextPosition()

        val metaAccount = MetaAccountLocal(
            substratePublicKey = null,
            substrateAccountId = null,
            substrateCryptoType = null,
            ethereumPublicKey = null,
            ethereumAddress = null,
            tonPublicKey = tonPublicKey.key.toByteArray(),
            name = payload.accountName,
            isSelected = true,
            position = position,
            isBackedUp = payload.isBackedUp,
            googleBackupAddress = null,
            initialized = false,
        )

        val metaAccountId = try {
            metaAccountDao.insertMetaAccount(metaAccount)
        } catch (e: Throwable) {
            throw AccountAlreadyExistsException()
        }

        tonSecretStore.put(
            metaAccountId,
            TonSecrets(
                seed = payload.mnemonic.encodeToByteArray(),
                tonKeypair = Keypair(
                    tonPublicKey.key.toByteArray(),
                    tonPrivateKey.key.toByteArray()
                )
            )
        )

        return metaAccountId
    }
}