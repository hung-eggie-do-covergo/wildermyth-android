package wmcloud

import `in`.dragonbra.javasteam.depotdownloader.DepotDownloader
import `in`.dragonbra.javasteam.depotdownloader.IDownloadListener
import `in`.dragonbra.javasteam.depotdownloader.data.AppItem
import `in`.dragonbra.javasteam.depotdownloader.data.DownloadItem
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Downloads the game from Steam into [dest] with the signed-in account; Steam only serves depots the
 * account owns. [onProgress] gets 0..100. The Linux depot is used: its jars carry every platform's natives
 * and the launcher swaps in Android ones.
 */
fun downloadGame(dest: File, onProgress: (Float) -> Unit) = download(dest, emptySet(), onProgress)

/** End-to-end check of sign-in, ownership, manifests and chunk download that fetches only [files]. */
fun testDownload(dest: File, files: Set<String>) {
    download(dest, files) {}
    val missing = files.filterNot { File(dest, it).isFile }
    if (missing.isNotEmpty()) die("test download finished but $missing did not arrive")
}

private fun download(dest: File, only: Set<String>, onProgress: (Float) -> Unit) {
    Session(false).use { s ->
        val licenses = try { s.licenses.get(60, TimeUnit.SECONDS) } catch (e: Exception) { die("Steam did not send the account's licenses: $e") }
        val failure = java.util.concurrent.atomic.AtomicReference<Throwable?>(null)
        // Tests run the downloader in debug mode so a hang shows where it stopped.
        DepotDownloader(s.client, licenses, only.isNotEmpty()).use { dd ->
            dd.addListener(object : IDownloadListener {
                override fun onStatusUpdate(message: String) = log(message)
                override fun onChunkCompleted(depotId: Int, depotPercentComplete: Float, compressedBytes: Long, uncompressedBytes: Long) =
                    onProgress(depotPercentComplete)
                override fun onDownloadFailed(item: DownloadItem, error: Throwable) { failure.set(error) }
            })
            if (only.isNotEmpty()) restrictToFiles(dd, only)
            dd.add(AppItem(APP_ID, false, dest.path, "public", "", false, "linux", false, "64", false, "english",
                false, emptyList(), emptyList(), false, false))
            dd.finishAdding()
            // A failed item does not always complete the downloader; stop waiting as soon as one fails.
            val done = dd.getCompletion()
            while (failure.get() == null) {
                try { done.get(1, TimeUnit.SECONDS); break } catch (_: java.util.concurrent.TimeoutException) { }
            }
        }
        failure.get()?.let { die("download failed: ${it.message ?: it}") }
        if (only.isEmpty() && !File(dest, "wildermyth.jar").isFile) die("download finished but wildermyth.jar is missing; does this account own Wildermyth?")
    }
}

/** JavaSteam 1.8.0 has a file filter but no way to set it ("not used yet"); tests set it reflectively. */
private fun restrictToFiles(dd: DepotDownloader, files: Set<String>) {
    val cfgField = DepotDownloader::class.java.getDeclaredField("config").apply { isAccessible = true }
    val cfg = cfgField.get(dd)
    cfg.javaClass.getDeclaredField("usingFileList").apply { isAccessible = true }.setBoolean(cfg, true)
    @Suppress("UNCHECKED_CAST")
    (cfg.javaClass.getDeclaredField("filesToDownload").apply { isAccessible = true }.get(cfg) as HashSet<String>).addAll(files)
}
