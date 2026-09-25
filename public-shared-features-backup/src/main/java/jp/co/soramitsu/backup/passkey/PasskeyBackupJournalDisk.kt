package jp.co.soramitsu.backup.passkey

import android.system.Os
import android.system.OsConstants
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import java.nio.file.attribute.PosixFilePermissions

/** Production uses explicit directory fsync; host tests provide their own platform implementation. */
internal interface PasskeyBackupJournalDurability {
    fun syncDirectory(path: Path)
    fun checkpoint(point: JournalDurabilityPoint) = Unit
}

internal enum class JournalDurabilityPoint {
    PREPARED_CREATED,
    PREPARED_PARTIAL,
    PREPARED_FILE_SYNCED,
    PREPARED_DIRECTORY_SYNCED,
    ATTEMPT_CREATED,
    ATTEMPT_PARTIAL,
    ATTEMPT_FILE_SYNCED,
    ATTEMPT_DIRECTORY_SYNCED,
    COMMIT_CREATED,
    COMMIT_PARTIAL,
    COMMIT_FILE_SYNCED,
    COMMIT_DIRECTORY_SYNCED
}

internal object AndroidBackupJournalDurability : PasskeyBackupJournalDurability {
    override fun syncDirectory(path: Path) {
        val descriptor = Os.open(path.toString(), OsConstants.O_RDONLY or OsConstants.O_NONBLOCK or OsConstants.O_NOFOLLOW, 0)
        try {
            require(OsConstants.S_ISDIR(Os.fstat(descriptor).st_mode)) { "Backup journal sync target is not a directory" }
            Os.fsync(descriptor)
        } finally {
            Os.close(descriptor)
        }
    }
}

/** All paths are children of a trusted app-private parent; no caller-controlled path components are used. */
internal class PasskeyBackupJournalDisk(directory: Path, private val durability: PasskeyBackupJournalDurability) {
    private val root = directory.parent.toRealPath().resolve(directory.fileName)

    fun <T> locked(action: () -> T): T = synchronized(processLock) {
        initialize()
        FileChannel.open(root.resolve(LOCK_NAME), READ, WRITE, NOFOLLOW_LINKS).use { channel ->
            channel.lock().use { action() }
        }
    }

    fun inventory(): Set<String> {
        val prepared = mutableSetOf<String>()
        val attempted = mutableSetOf<String>()
        val committed = mutableSetOf<String>()
        Files.newDirectoryStream(root).use { stream ->
            var count = 0
            for (path in stream) {
                require(++count <= MAX_FILES) { "Backup journal file count exceeded" }
                val name = path.fileName.toString()
                requirePrivateFile(path)
                collectOperation(name, prepared, attempted, committed)
            }
        }
        require(prepared.size <= MAX_ENTRIES && prepared.containsAll(attempted) && attempted.containsAll(committed)) {
            "Orphaned or excessive backup journal entries"
        }
        return prepared
    }

    private fun collectOperation(
        name: String,
        prepared: MutableSet<String>,
        attempted: MutableSet<String>,
        committed: MutableSet<String>
    ) {
        if (name == LOCK_NAME) return
        val suffix = when {
            name.endsWith(PREPARED_SUFFIX) -> PREPARED_SUFFIX
            name.endsWith(ATTEMPT_SUFFIX) -> ATTEMPT_SUFFIX
            name.endsWith(COMMIT_SUFFIX) -> COMMIT_SUFFIX
            else -> error("Unexpected backup journal file")
        }
        val operation = name.removeSuffix(suffix)
        PasskeyBackupGenerationFormat.requireIdentifier(operation)
        when (suffix) {
            PREPARED_SUFFIX -> prepared.add(operation)
            ATTEMPT_SUFFIX -> attempted.add(operation)
            else -> committed.add(operation)
        }
    }

    fun exists(
        operation: String,
        attempt: Boolean = false,
        commit: Boolean = false
    ): Boolean {
        return Files.exists(path(operation, attempt, commit), NOFOLLOW_LINKS)
    }

    fun read(
        operation: String,
        attempt: Boolean = false,
        commit: Boolean = false
    ): ByteArray {
        val file = path(operation, attempt, commit)
        requirePrivateFile(file)
        val maximum = if (attempt || commit) PasskeyBackupJournalRecord.MAX_ATTEMPT_BYTES else PasskeyBackupJournalRecord.MAX_BYTES
        return FileChannel.open(file, READ, NOFOLLOW_LINKS).use { channel ->
            val size = channel.size()
            require(size in 1..maximum.toLong()) { "Invalid backup journal file size" }
            val buffer = ByteBuffer.allocate(size.toInt() + 1)
            while (buffer.hasRemaining() && channel.read(buffer) >= 0) { /* Bounded regular-file read. */ }
            require(buffer.position().toLong() == size) { "Backup journal changed while reading" }
            buffer.array().copyOf(size.toInt())
        }
    }

    fun create(
        operation: String,
        bytes: ByteArray,
        attempt: Boolean = false,
        commit: Boolean = false
    ) {
        val maximum = if (attempt || commit) PasskeyBackupJournalRecord.MAX_ATTEMPT_BYTES else PasskeyBackupJournalRecord.MAX_BYTES
        require(bytes.size in 1..maximum) { "Invalid backup journal record size" }
        FileChannel.open(path(operation, attempt, commit), setOf(CREATE_NEW, WRITE, NOFOLLOW_LINKS), filePermissions).use { channel ->
            durability.checkpoint(
                checkpoint(
                    attempt, commit, JournalDurabilityPoint.PREPARED_CREATED,
                    JournalDurabilityPoint.ATTEMPT_CREATED, JournalDurabilityPoint.COMMIT_CREATED
                )
            )
            val half = bytes.size / 2
            write(channel, ByteBuffer.wrap(bytes, 0, half))
            durability.checkpoint(
                checkpoint(
                    attempt, commit, JournalDurabilityPoint.PREPARED_PARTIAL,
                    JournalDurabilityPoint.ATTEMPT_PARTIAL, JournalDurabilityPoint.COMMIT_PARTIAL
                )
            )
            write(channel, ByteBuffer.wrap(bytes, half, bytes.size - half))
            channel.force(true)
            durability.checkpoint(
                checkpoint(
                    attempt, commit, JournalDurabilityPoint.PREPARED_FILE_SYNCED,
                    JournalDurabilityPoint.ATTEMPT_FILE_SYNCED, JournalDurabilityPoint.COMMIT_FILE_SYNCED
                )
            )
        }
        durability.syncDirectory(root)
        durability.checkpoint(
            checkpoint(
                attempt, commit, JournalDurabilityPoint.PREPARED_DIRECTORY_SYNCED,
                JournalDurabilityPoint.ATTEMPT_DIRECTORY_SYNCED, JournalDurabilityPoint.COMMIT_DIRECTORY_SYNCED
            )
        )
    }

    /** Re-establish durability before returning a surviving record after an earlier ambiguous sync failure. */
    fun confirmPreparedDurable(operation: String) {
        val file = path(operation, false)
        requirePrivateFile(file)
        FileChannel.open(file, WRITE, NOFOLLOW_LINKS).use { it.force(true) }
        durability.syncDirectory(root)
    }

    private fun initialize() {
        try {
            Files.createDirectory(root, directoryPermissions)
        } catch (_: FileAlreadyExistsException) {
            // Existing directories are validated, never replaced or repaired implicitly.
        }
        require(Files.isDirectory(root, NOFOLLOW_LINKS) && Files.getPosixFilePermissions(root, NOFOLLOW_LINKS) == directoryPermissions.value()) {
            "Backup journal directory is not private"
        }
        durability.syncDirectory(root.parent)
        val lock = root.resolve(LOCK_NAME)
        try {
            FileChannel.open(lock, setOf(CREATE_NEW, WRITE, NOFOLLOW_LINKS), filePermissions).use { it.force(true) }
        } catch (_: FileAlreadyExistsException) {
            // Never truncate a lock file belonging to another process.
        }
        requirePrivateFile(lock)
        require(Files.size(lock) == 0L) { "Invalid backup journal lock file" }
        durability.syncDirectory(root)
    }

    private fun requirePrivateFile(path: Path) {
        require(Files.isRegularFile(path, NOFOLLOW_LINKS) && Files.getPosixFilePermissions(path, NOFOLLOW_LINKS) == filePermissions.value()) {
            "Backup journal file is not private and regular"
        }
        require(Files.getOwner(path, NOFOLLOW_LINKS) == Files.getOwner(root, NOFOLLOW_LINKS)) {
            "Backup journal file owner mismatch"
        }
        require((Files.getAttribute(path, "unix:nlink", NOFOLLOW_LINKS) as? Number)?.toInt() == 1) {
            "Linked backup journal file"
        }
    }

    private fun path(
        operation: String,
        attempt: Boolean,
        commit: Boolean = false
    ): Path {
        PasskeyBackupGenerationFormat.requireIdentifier(operation)
        require(!attempt || !commit) { "Invalid backup journal marker type" }
        val suffix = when {
            commit -> COMMIT_SUFFIX
            attempt -> ATTEMPT_SUFFIX
            else -> PREPARED_SUFFIX
        }
        return root.resolve(operation + suffix)
    }

    private fun checkpoint(
        attempt: Boolean,
        commit: Boolean,
        prepared: JournalDurabilityPoint,
        create: JournalDurabilityPoint,
        promotion: JournalDurabilityPoint
    ): JournalDurabilityPoint = when {
        commit -> promotion
        attempt -> create
        else -> prepared
    }

    private fun write(channel: FileChannel, buffer: ByteBuffer) {
        while (buffer.hasRemaining()) require(channel.write(buffer) > 0) { "Backup journal write made no progress" }
    }

    companion object {
        const val MAX_ENTRIES = 64
        const val PREPARED_SUFFIX = ".prepared.json"
        const val ATTEMPT_SUFFIX = ".attempt.json"
        const val COMMIT_SUFFIX = ".commit.json"
        private const val MAX_FILES = MAX_ENTRIES * 3 + 1
        private const val LOCK_NAME = ".lock"
        private val processLock = Any()
        private val filePermissions = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))
        private val directoryPermissions = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))
    }
}
