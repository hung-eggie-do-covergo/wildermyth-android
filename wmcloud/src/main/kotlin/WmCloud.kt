package wmcloud

import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder
import java.io.File
import java.util.function.Consumer

/** The sync operations for Java callers (the Android app), with checked exceptions declared. */
object WmCloud {
    @JvmStatic fun configure(dir: File, logger: Consumer<String>) {
        // Android registers its own cut-down provider as "BC" (no SHA-1 among others); JavaSteam asks
        // for "BC" by name, so put the full bundled BouncyCastle in its place.
        java.security.Security.removeProvider("BC")
        java.security.Security.insertProviderAt(org.bouncycastle.jce.provider.BouncyCastleProvider(), 1)
        configDir = dir
        log = { logger.accept(it) }
    }

    /** Routes JavaSteam's own debug log to [sink] (the app sends it to logcat). */
    @JvmStatic fun debugLog(sink: Consumer<String>) {
        `in`.dragonbra.javasteam.util.log.LogManager.addListener(object : `in`.dragonbra.javasteam.util.log.LogListener {
            override fun onLog(clazz: Class<*>, message: String?, throwable: Throwable?) = sink.accept("${clazz.simpleName}: $message")
            override fun onError(clazz: Class<*>, message: String?, throwable: Throwable?) = sink.accept("ERROR ${clazz.simpleName}: $message $throwable")
        })
    }

    @JvmStatic fun isLoggedIn() = File(configDir, "token.json").isFile

    /** Blocks until the user approves the QR sign-in in the Steam app; each new challenge URL goes to [onQr]. */
    @JvmStatic @Throws(WmCloudException::class)
    fun login(onQr: Consumer<String>) {
        onQrChallenge = { onQr.accept(it) }
        // Steam drops a not-yet-signed-in connection after a minute or so; start over with a fresh
        // code rather than failing while the user is still reaching for their phone.
        val deadline = System.currentTimeMillis() + 10 * 60_000
        while (true) {
            try { Session(true).use { }; return } catch (e: WmCloudException) {
                if (isLoggedIn() || System.currentTimeMillis() > deadline) throw e
                log("Steam dropped the connection; showing a new code")
            }
        }
    }

    /** Downloads only [files] into [dest]: proves sign-in, ownership and chunk download without the full game. */
    @JvmStatic @Throws(WmCloudException::class)
    fun testDownload(dest: File, files: Set<String>) = wmcloud.testDownload(dest, files)

    /** Downloads the game with the signed-in account into [dest]; progress 0..100 goes to [onProgress]. */
    @JvmStatic @Throws(WmCloudException::class)
    fun downloadGame(dest: File, onProgress: Consumer<Float>) = wmcloud.downloadGame(dest) { onProgress.accept(it) }

    /** Wildermyth DLC app IDs the account owns, straight from Steam. */
    @JvmStatic @Throws(WmCloudException::class)
    fun ownedDlc(): List<Int> = ownedApps(DLC_APP_IDS)

    @JvmStatic @Throws(WmCloudException::class)
    fun pull(game: File, force: Boolean) = wmcloud.pull(game, force)

    @JvmStatic @Throws(WmCloudException::class)
    fun push(game: File, force: Boolean) = wmcloud.push(game, force)

    @JvmStatic @Throws(WmCloudException::class)
    fun syncAchievements(game: File) = achievements(game, true, false)

    /** QR modules for [text], true = dark, so the app can draw it without its own QR library. */
    @JvmStatic fun qrMatrix(text: String): Array<BooleanArray> {
        val m = Encoder.encode(text, ErrorCorrectionLevel.L).matrix
        return Array(m.height) { y -> BooleanArray(m.width) { x -> m.get(x, y).toInt() == 1 } }
    }
}
