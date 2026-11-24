package jp.co.soramitsu.app.root.domain

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.common.data.FileProviderImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Deletes keystore JSON exports that a share left behind. The export screen deletes its file when
 * the share finishes or the screen goes away, but a process that dies while the share sheet is
 * open never gets there. At process start no share of ours can still be running.
 *
 * Only regular files directly inside the FileProvider's `shared_exports` cache directory are
 * removed; nothing else in the cache, and no wallet data, is touched.
 */
@Singleton
class SharedExportCleanup(private val cacheDir: () -> File?) {

    @Inject
    constructor(@ApplicationContext context: Context) : this({ context.cacheDir })

    fun start(scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)): Job =
        scope.launch { runCatching { runOnce() } }

    /** Returns how many leftover exports were deleted. */
    fun runOnce(): Int {
        val directory = File(cacheDir() ?: return 0, FileProviderImpl.INTERNAL_SHARE_DIRECTORY)
        if (!Files.isDirectory(directory.toPath(), LinkOption.NOFOLLOW_LINKS)) return 0
        return directory.listFiles().orEmpty().count { file ->
            Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS) && runCatching { Files.deleteIfExists(file.toPath()) }.getOrDefault(false)
        }
    }
}
