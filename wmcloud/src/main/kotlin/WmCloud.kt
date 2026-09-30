package wmcloud

import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder
import java.io.File
import java.util.function.Consumer

/** The sync operations for Java callers (the Android app), with checked exceptions declared. */
object WmCloud {
    @JvmStatic fun configure(dir: File, logger: Consumer<String>) {
        configDir = dir
        log = { logger.accept(it) }
    }

    @JvmStatic fun isLoggedIn() = File(configDir, "token.json").isFile

    /** Blocks until the user approves the QR sign-in in the Steam app; each new challenge URL goes to [onQr]. */
    @JvmStatic @Throws(WmCloudException::class)
    fun login(onQr: Consumer<String>) {
        onQrChallenge = { onQr.accept(it) }
        Session(true).use { }
    }

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
