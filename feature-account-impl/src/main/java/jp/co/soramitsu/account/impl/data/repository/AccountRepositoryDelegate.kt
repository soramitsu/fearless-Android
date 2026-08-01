package jp.co.soramitsu.account.impl.data.repository

import jp.co.soramitsu.account.api.domain.interfaces.AccountAlreadyExistsException
import jp.co.soramitsu.account.api.domain.model.AddAccountPayload
import jp.co.soramitsu.common.data.Keypair
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecrets
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.secrets.v3.TonSecrets
import jp.co.soramitsu.common.utils.deriveSeed32
import jp.co.soramitsu.common.utils.ethereumAddressFromPublicKey
import jp.co.soramitsu.common.utils.nullIfEmpty
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.core.crypto.mapCryptoTypeToEncryption
import jp.co.soramitsu.coredb.dao.MetaAccountDao
import jp.co.soramitsu.coredb.model.MetaAccountLocal
import jp.co.soramitsu.fearless_utils.encrypt.junction.BIP32JunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.junction.SubstrateJunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.keypair.ethereum.EthereumKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.seed.ethereum.EthereumSeedFactory
import jp.co.soramitsu.fearless_utils.encrypt.seed.substrate.SubstrateSeedFactory
import jp.co.soramitsu.fearless_utils.scale.toHexString
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
    private val walletSecretMutationCoordinator: WalletSecretMutationCoordinator
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
            tonPublicKey = localMetaAccount.tonPublicKey,
            name = localMetaAccount.name,
            isSelected = localMetaAccount.isSelected,
            position = localMetaAccount.position,
            isBackedUp = payload.isBackedUp,
            googleBackupAddress = localMetaAccount.googleBackupAddress,
            initialized = localMetaAccount.initialized,
        )

        metaAccount.id = payload.walletId

        val ethereumSecrets = EthereumSecrets(
            entropy = ethereumSeedResult.mnemonic.entropy,
            seed = ethereumKeypair.privateKey,
            ethereumKeypair = ethereumKeypair,
            ethereumDerivationPath = payload.ethereumDerivationPath
        )

        return try {
            walletSecretMutationCoordinator.addEvm(
                existing = localMetaAccount,
                after = metaAccount,
                ethereumSecretPlaintext = ethereumSecrets.toHexString()
            )
        } catch (failure: WalletSecretMutationCoordinatorException) {
            if (failure.reason == WalletMutationFailureReason.IDENTITY_CONFLICT) {
                throw AccountAlreadyExistsException()
            }
            throw failure
        }
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

        val metaAccount = MetaAccountLocal(
            substratePublicKey = keys.publicKey,
            substrateAccountId = keys.publicKey.substrateAccountId(),
            substrateCryptoType = payload.encryptionType,
            ethereumPublicKey = ethereumKeypair.publicKey,
            ethereumAddress = ethereumKeypair.publicKey.ethereumAddressFromPublicKey(),
            tonPublicKey = null,
            name = payload.accountName,
            isSelected = true,
            position = 0,
            isBackedUp = payload.isBackedUp,
            googleBackupAddress = payload.googleBackupAddress,
            initialized = false,
        )

        val substrateSecrets = SubstrateSecrets(
            substrateKeyPair = keys,
            substrateDerivationPath = payload.substrateDerivationPath,
            seed = derivationResult.seed,
            entropy = derivationResult.mnemonic.entropy
        )

        val ethereumSecrets = EthereumSecrets(
            entropy = derivationResult.mnemonic.entropy,
            seed = ethereumKeypair.privateKey,
            ethereumKeypair = ethereumKeypair,
            ethereumDerivationPath = payload.ethereumDerivationPath
        )

        return try {
            walletSecretMutationCoordinator.create(
                prototype = metaAccount,
                substrateSecretPlaintext = substrateSecrets.toHexString(),
                ethereumSecretPlaintext = ethereumSecrets.toHexString(),
                tonSecretPlaintext = null
            )
        } catch (failure: WalletSecretMutationCoordinatorException) {
            if (failure.reason == WalletMutationFailureReason.IDENTITY_CONFLICT) {
                throw AccountAlreadyExistsException()
            }
            throw failure
        }
    }
}

class TonAccountRepository(
    private val walletSecretMutationCoordinator: WalletSecretMutationCoordinator
) {
    suspend fun create(payload: AddAccountPayload.Ton): Long {
        val tonSeed = org.ton.mnemonic.Mnemonic.toSeed(payload.mnemonic.split(" "))
        val tonPrivateKey = PrivateKeyEd25519(tonSeed)
        val tonPublicKey = tonPrivateKey.publicKey()

        val metaAccount = MetaAccountLocal(
            substratePublicKey = null,
            substrateAccountId = null,
            substrateCryptoType = null,
            ethereumPublicKey = null,
            ethereumAddress = null,
            tonPublicKey = tonPublicKey.key.toByteArray(),
            name = payload.accountName,
            isSelected = true,
            position = 0,
            isBackedUp = payload.isBackedUp,
            googleBackupAddress = null,
            initialized = false,
        )

        val tonSecrets = TonSecrets(
            seed = payload.mnemonic.encodeToByteArray(),
            tonKeypair = Keypair(
                tonPublicKey.key.toByteArray(),
                tonPrivateKey.key.toByteArray()
            )
        )

        return try {
            walletSecretMutationCoordinator.create(
                prototype = metaAccount,
                substrateSecretPlaintext = null,
                ethereumSecretPlaintext = null,
                tonSecretPlaintext = tonSecrets.toHexString()
            )
        } catch (failure: WalletSecretMutationCoordinatorException) {
            if (failure.reason == WalletMutationFailureReason.IDENTITY_CONFLICT) {
                throw AccountAlreadyExistsException()
            }
            throw failure
        }
    }
}
