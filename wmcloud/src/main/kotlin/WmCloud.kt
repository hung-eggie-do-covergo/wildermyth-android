package wmcloud

import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder
import java.io.File
import java.util.function.Consumer

/** Bundled BouncyCastle with chunk AES moved to Android's native OpenSSL: pure-Java AES was ~10% of download CPU. */
private fun steamCrypto(): java.security.Provider {
    val bc = org.bouncycastle.jce.provider.BouncyCastleProvider()
    val native = java.security.Security.getProvider("AndroidOpenSSL") ?: return bc
    // putService is protected; BC is final, so it cannot be subclassed. Service entries win over BC's own.
    val put = java.security.Provider::class.java.getDeclaredMethod("putService", java.security.Provider.Service::class.java).apply { isAccessible = true }
    for ((ours, theirs) in listOf("AES/ECB/NoPadding" to "AES/ECB/NoPadding", "AES/CBC/PKCS7Padding" to "AES/CBC/PKCS5Padding")) {
        val target = native.getService("Cipher", theirs) ?: continue
        put.invoke(bc, object : java.security.Provider.Service(bc, "Cipher", ours, target.className, null, null) {
            override fun newInstance(param: Any?) = target.newInstance(param)
        })
    }
    return bc
}

/** The sync operations for Java callers (the Android app), with checked exceptions declared. */
object WmCloud {
    @JvmStatic fun configure(dir: File, logger: Consumer<String>) {
        // JavaSteam keeps an 8 MB LZMA window per thread that decompresses a chunk, and chunks decompress
        // on Dispatchers.IO, which grows to 64 threads: 512 MB. Must be set before Dispatchers.IO is first used.
        System.setProperty("kotlinx.coroutines.io.parallelism", ioThreadsFor(Runtime.getRuntime().maxMemory()).toString())
        // Android registers its own cut-down provider as "BC" (no SHA-1 among others); JavaSteam asks
        // for "BC" by name, so put the full bundled BouncyCastle in its place.
        java.security.Security.removeProvider("BC")
        java.security.Security.insertProviderAt(steamCrypto(), 1)
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

    /** Steam account name from the saved sign-in, or null. */
    @JvmStatic fun accountName(): String? =
        readPrivate("token.json")?.let { gson.fromJson(it, Token::class.java).account }

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

    /**
     * Before a session, over one Steam connection: pulls saves, then asks which DLC the account owns.
     * Returns the owned DLC app IDs, or null if only that check failed; the caller keeps its last answer.
     */
    @JvmStatic @Throws(WmCloudException::class)
    fun beforePlay(game: File): List<Int>? = Session(false).use { s ->
        wmcloud.pull(s, game, false)
        try { ownedApps(s, DLC_APP_IDS) } catch (e: Exception) { log("DLC check failed: $e"); null }
    }

    /** After a session, over one Steam connection: pushes saves, then achievements, which can wait a session. */
    @JvmStatic @Throws(WmCloudException::class)
    fun afterPlay(game: File) {
        val store = StoreStatsHandler()
        Session(false, listOf(store)).use { s ->
            wmcloud.push(s, game, false)
            try { achievements(s, store, game, true, false) } catch (e: Exception) { log("achievements: $e") }
        }
    }

    @JvmStatic @Throws(WmCloudException::class)
    fun pull(game: File, force: Boolean) = wmcloud.pull(game, force)

    @JvmStatic @Throws(WmCloudException::class)
    fun push(game: File, force: Boolean) = wmcloud.push(game, force)

    /** QR modules for [text], true = dark, so the app can draw it without its own QR library. */
    @JvmStatic fun qrMatrix(text: String): Array<BooleanArray> {
        val m = Encoder.encode(text, ErrorCorrectionLevel.L).matrix
        return Array(m.height) { y -> BooleanArray(m.width) { x -> m.get(x, y).toInt() == 1 } }
    }
}
