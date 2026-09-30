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
fun downloadGame(dest: File, onProgress: (Float) -> Unit) {
    Session(false).use { s ->
        val licenses = try { s.licenses.get(60, TimeUnit.SECONDS) } catch (e: Exception) { die("Steam did not send the account's licenses: $e") }
        var failure: Throwable? = null
        DepotDownloader(s.client, licenses).use { dd ->
            dd.addListener(object : IDownloadListener {
                override fun onStatusUpdate(message: String) = log(message)
                override fun onChunkCompleted(depotId: Int, depotPercentComplete: Float, compressedBytes: Long, uncompressedBytes: Long) =
                    onProgress(depotPercentComplete)
                override fun onDownloadFailed(item: DownloadItem, error: Throwable) { failure = error }
            })
            dd.add(AppItem(APP_ID, false, dest.path, "public", "", false, "linux", false, "64", false, "english",
                false, emptyList(), emptyList(), false, false))
            dd.finishAdding()
            dd.awaitCompletion()
        }
        failure?.let { die("download failed: ${it.message ?: it}") }
        if (!File(dest, "wildermyth.jar").isFile) die("download finished but wildermyth.jar is missing; does this account own Wildermyth?")
    }
}
