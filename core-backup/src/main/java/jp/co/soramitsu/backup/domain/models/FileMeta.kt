package jp.co.soramitsu.backup.domain.models

data class FileMeta(
    val fileName: String,
    val description: String,
    val fileId: String,
    // Epoch milliseconds; orders several backups of one wallet newest first.
    val modifiedTime: Long? = null
)
