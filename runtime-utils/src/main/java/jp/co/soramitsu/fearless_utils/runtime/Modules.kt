package jp.co.soramitsu.fearless_utils.runtime

typealias AccountId = ByteArray

@Deprecated("Use new dynamic runtime system instead")
abstract class Service<STORAGE_ARGS>(val module: Module, val id: String) {
    abstract fun storageKey(storageArgs: STORAGE_ARGS): String
}

fun Service<Unit>.storageKey() = storageKey(Unit)

@Deprecated("Use new dynamic runtime system instead")
open class AccountIdService(
    module: Module,
    id: String,
    private val identifierHasher: IdentifierHasher
) : Service<AccountId>(module, id) {

    override fun storageKey(storageArgs: AccountId): String {
        return StorageUtils.createStorageKey(
            this,
            Identifier(
                storageArgs,
                identifierHasher
            )
        )
    }
}

@Deprecated("Use new dynamic runtime system instead")
abstract class Module(val id: String) {

    object System : Module("System") {

        object Account : AccountIdService(
            System,
            "Account",
            IdentifierHasher.Blake2b128concat
        )
    }

    object Staking : Module("Staking") {

        object Ledger : AccountIdService(
            Staking,
            "Ledger",
            IdentifierHasher.Blake2b128concat
        )

        object ActiveEra : Service<Unit>(
            Staking,
            "ActiveEra"
        ) {

            override fun storageKey(storageArgs: Unit): String {
                return StorageUtils.createStorageKey(
                    this,
                    null
                )
            }
        }

        object Bonded : AccountIdService(
            Staking,
            "Bonded",
            IdentifierHasher.TwoX64Concat
        )
    }
}
