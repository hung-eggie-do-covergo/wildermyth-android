package wmcloud

import com.google.gson.GsonBuilder
import `in`.dragonbra.javasteam.protobufs.steamclient.Enums.ECloudStoragePersistState
import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesCloudSteamclient.CCloud_ClientDeleteFile_Request
import `in`.dragonbra.javasteam.rpc.service.Cloud
import `in`.dragonbra.javasteam.steam.handlers.steamunifiedmessages.SteamUnifiedMessages
import `in`.dragonbra.javasteam.steam.authentication.AuthSessionDetails
import `in`.dragonbra.javasteam.steam.authentication.IChallengeUrlChanged
import `in`.dragonbra.javasteam.steam.authentication.QrAuthSession
import `in`.dragonbra.javasteam.steam.handlers.ClientMsgHandler
import `in`.dragonbra.javasteam.steam.handlers.steamcloud.AppFileChangeList
import `in`.dragonbra.javasteam.steam.handlers.steamcloud.SteamCloud
import `in`.dragonbra.javasteam.steam.handlers.steamuser.LogOnDetails
import `in`.dragonbra.javasteam.steam.handlers.steamuser.SteamUser
import `in`.dragonbra.javasteam.steam.handlers.steamuser.callback.LoggedOnCallback
import `in`.dragonbra.javasteam.steam.steamclient.SteamClient
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackManager
import `in`.dragonbra.javasteam.steam.steamclient.callbacks.ConnectedCallback
import `in`.dragonbra.javasteam.steam.steamclient.callbacks.DisconnectedCallback
import `in`.dragonbra.javasteam.util.log.DefaultLogListener
import `in`.dragonbra.javasteam.util.log.LogManager
import okhttp3.Headers
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.security.MessageDigest
import java.util.Date
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.system.exitProcess

const val APP_ID = 763890 // Wildermyth
const val ROOT = "%GameInstall%" // Auto-Cloud root "1": the game install dir

val gson = GsonBuilder().setPrettyPrinting().create()
val http = OkHttpClient()
/** Where the token and sync state live; the app points this at its private files dir. */
var configDir = File(System.getProperty("user.home"), ".config/wmcloud")
/** Progress lines; the CLI prints them, the app shows them. */
var log: (String) -> Unit = { println(it) }
/** Receives each Steam QR challenge URL during login; the CLI draws it in the terminal. */
var onQrChallenge: (String) -> Unit = {
    println("\nScan with the Steam mobile app (Steam Guard > scan QR), or open: $it")
    printQr(it)
}

data class Token(val account: String, val refreshToken: String)
data class State(var changeNumber: Long = 0, var clientId: Long = 0, val files: MutableMap<String, String> = mutableMapOf())

/** A cloud file: full cloud name (with root token) and the path relative to the game dir. */
data class CloudFile(val cloudName: String, val rel: String, val sha: String, val size: Int, val deleted: Boolean)

open class WmCloudException(msg: String) : Exception(msg)
/** Pulling would overwrite local saves that were never uploaded. */
class ConflictException(val files: List<String>) :
    WmCloudException("local changes not pushed yet, would be overwritten: $files (keep this device: push --force, keep the cloud: pull --force)")
/** Another device synced since this one last pulled. */
class CloudChangedException(msg: String) : WmCloudException(msg)

fun die(msg: String): Nothing = throw WmCloudException(msg)

fun sha1(f: File): String = hex(MessageDigest.getInstance("SHA-1").digest(f.readBytes()))
fun sha1(b: ByteArray): String = hex(MessageDigest.getInstance("SHA-1").digest(b))
fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
fun unhex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

fun readPrivate(name: String): String? = File(configDir, name).takeIf { it.exists() }?.readText()
fun writePrivate(name: String, text: String) {
    configDir.mkdirs()
    val f = File(configDir, name)
    val tmp = File(configDir, "$name.tmp")
    tmp.writeText(text)
    tmp.setReadable(false, false); tmp.setReadable(true, true)
    tmp.setWritable(false, false); tmp.setWritable(true, true)
    if (!tmp.renameTo(f)) die("cannot write ${f.path}")
}

/** Prints a QR code two modules per line with half blocks, so it fits a short terminal. */
fun printQr(text: String) {
    val m = Encoder.encode(text, ErrorCorrectionLevel.L).matrix
    val q = 2 // quiet zone
    // Light modules are drawn as glyphs: terminals are light-on-dark.
    fun light(x: Int, y: Int) = x < 0 || y < 0 || x >= m.width || y >= m.height || m.get(x, y).toInt() == 0
    val sb = StringBuilder()
    for (y in -q until m.height + q step 2) {
        for (x in -q until m.width + q) sb.append(when {
            light(x, y) && light(x, y + 1) -> '█'
            light(x, y) -> '▀'
            light(x, y + 1) -> '▄'
            else -> ' '
        })
        sb.append('\n')
    }
    print(sb)
}

fun loadState(): State = readPrivate("state.json")?.let { gson.fromJson(it, State::class.java) } ?: State()
fun saveState(s: State) = writePrivate("state.json", gson.toJson(s))

/** Connects and logs on; with no token, runs the interactive QR-code flow. */
class Session(private val interactive: Boolean, handlers: List<ClientMsgHandler> = emptyList()) : AutoCloseable {
    val client = SteamClient()
    private val manager = CallbackManager(client)
    val cloud: SteamCloud = client.getHandler(SteamCloud::class.java)!!
    private val loggedOn = CompletableFuture<Unit>()
    @Volatile private var running = true

    init {
        val token = readPrivate("token.json")?.let { gson.fromJson(it, Token::class.java) }
        if (token == null && !interactive) die("not logged in; run `wmcloud login` first")
        manager.subscribe(ConnectedCallback::class.java) { onConnected(token) }
        manager.subscribe(DisconnectedCallback::class.java) {
            loggedOn.completeExceptionally(IllegalStateException("disconnected from Steam"))
        }
        manager.subscribe(LoggedOnCallback::class.java) {
            if (it.result == EResult.OK) loggedOn.complete(Unit)
            else loggedOn.completeExceptionally(IllegalStateException("logon failed: ${it.result} / ${it.extendedResult}"))
        }
        // Handlers must be added before connecting: the receive loop iterates them unguarded.
        handlers.forEach { client.addHandler(it) }
        Thread { while (running) manager.runWaitCallbacks(500L) }.apply { isDaemon = true }.start()
        client.connect()
        try { loggedOn.get(if (interactive) 600 else 120, TimeUnit.SECONDS) } catch (e: Exception) {
            close()
            die(e.cause?.message ?: e.toString())
        }
    }

    private fun onConnected(token: Token?) {
        val (account, refresh) = token?.let { it.account to it.refreshToken } ?: run {
            val session = client.authentication.beginAuthSessionViaQR(AuthSessionDetails().apply { persistentSession = true }).get()
            // Steam rotates the challenge every ~30s; redraw each time.
            val draw = { q: QrAuthSession -> onQrChallenge(q.challengeUrl) }
            session.challengeUrlChanged = IChallengeUrlChanged { it?.let(draw) }
            draw(session)
            val poll = session.pollingWaitForResult().get()
            writePrivate("token.json", gson.toJson(Token(poll.accountName, poll.refreshToken)))
            poll.accountName to poll.refreshToken
        }
        client.getHandler(SteamUser::class.java)!!.logOn(LogOnDetails().apply {
            username = account; accessToken = refresh; shouldRememberPassword = true
        })
    }

    fun listFiles(): Pair<AppFileChangeList, List<CloudFile>> {
        val list = cloud.getAppFileListChange(APP_ID).get()
        val files = list.files.map { f ->
            val prefix = if (f.pathPrefixIndex < list.pathPrefixes.size) list.pathPrefixes[f.pathPrefixIndex] else ""
            val name = prefix + f.filename
            if (!name.startsWith(ROOT)) die("unexpected cloud root in '$name'")
            CloudFile(name, name.removePrefix(ROOT), hex(f.shaFile), f.rawFileSize,
                f.persistState == ECloudStoragePersistState.k_ECloudStoragePersistStateDeleted)
        }
        return list to files
    }

    override fun close() {
        running = false
        client.getHandler(SteamUser::class.java)?.logOff()
        client.disconnect()
    }
}

fun gameDir(args: List<String>): File {
    val i = args.indexOf("--game")
    val dir = File(if (i >= 0) args[i + 1] else ".").absoluteFile
    if (!File(dir, "wildermyth.jar").exists()) die("${dir.path} is not a Wildermyth install (pass --game <dir>)")
    return dir
}

/** Steam's own Auto-Cloud marker; it lives among the saves but is never synced. */
fun synced(rel: String) = !rel.endsWith("/steam_autocloud.vdf")

/** Local save files, keyed by path relative to the game dir. All of players/ is cloud-synced. */
fun localFiles(game: File): Map<String, File> =
    File(game, "players").walkTopDown().filter { it.isFile }
        .associateBy { it.relativeTo(game).invariantSeparatorsPath }.filterKeys(::synced)

fun download(s: Session, f: CloudFile): ByteArray {
    val info = s.cloud.clientFileDownload(APP_ID, f.cloudName).get()
    if (info.urlHost.isEmpty()) die("no download URL for ${f.rel}")
    val req = Request.Builder().url((if (info.useHttps) "https://" else "http://") + info.urlHost + info.urlPath)
        .headers(Headers.headersOf(*info.requestHeaders.flatMap { listOf(it.name, it.value) }.toTypedArray())).build()
    val body = http.newCall(req).execute().use { r ->
        if (!r.isSuccessful) die("download ${f.rel}: HTTP ${r.code}")
        r.body.bytes()
    }
    // Compressed files arrive as a single-entry zip.
    val bytes = if (info.fileSize != info.rawFileSize) ZipInputStream(body.inputStream()).use { it.nextEntry; it.readBytes() } else body
    if (sha1(bytes) != f.sha) die("sha mismatch after downloading ${f.rel}")
    return bytes
}

fun backup(game: File) {
    val players = File(game, "players")
    if (!players.exists()) return
    val dir = File(game, "players-backups").apply { mkdirs() }
    val out = File(dir, "players-${System.currentTimeMillis() / 1000}.zip")
    try {
        ZipOutputStream(out.outputStream().buffered()).use { z ->
            players.walkTopDown().filter { it.isFile }.forEach { f ->
                z.putNextEntry(ZipEntry(f.relativeTo(game).invariantSeparatorsPath)); f.inputStream().use { it.copyTo(z) }; z.closeEntry()
            }
        }
    } catch (e: Exception) { out.delete(); die("backup of players/ failed, not touching saves: $e") }
    // ponytail: keeps the newest 10 backups, count-based not age-based
    dir.listFiles()!!.sortedByDescending { it.name }.drop(10).forEach { it.delete() }
}

fun pull(game: File, force: Boolean) {
    val state = loadState()
    Session(false).use { s ->
        val (list, files) = s.listFiles()
        val local = localFiles(game)
        // A local file edited since the last sync would be clobbered; stop unless forced.
        // With no sync history yet, every existing local file counts as possibly edited.
        val firstSync = state.changeNumber == 0L
        val dirty = local.filter { (rel, f) -> firstSync || state.files[rel]?.let { it != sha1(f) } == true }.keys
        val active = files.filter { !it.deleted && synced(it.rel) }
        val incoming = active.filter { local[it.rel]?.let { l -> sha1(l) } != it.sha }
        // Gone from the cloud since last sync: delete locally, unless edited here since.
        val activeRels = active.map { it.rel }.toSet()
        val removed = if (firstSync) emptyList() else state.files.filter { (rel, sha) ->
            rel !in activeRels && local[rel]?.let { sha1(it) == sha } == true }.keys.toList()
        val clobbered = dirty.intersect(incoming.map { it.rel }.toSet())
        if (clobbered.isNotEmpty() && !force) throw ConflictException(clobbered.sorted())
        if (incoming.isNotEmpty() || removed.isNotEmpty()) backup(game)
        for (rel in removed) { File(game, rel).delete(); log("deleted $rel") }
        for (f in incoming) {
            val bytes = download(s, f)
            val dest = File(game, f.rel).apply { parentFile.mkdirs() }
            val tmp = File(dest.path + ".wmcloud")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(dest)) die("cannot write ${dest.path}")
            log("pulled ${f.rel}")
        }
        state.changeNumber = list.currentChangeNumber
        state.files.clear()
        active.forEach { state.files[it.rel] = it.sha }
        saveState(state)
        log("pull done: ${incoming.size} updated, ${removed.size} deleted, ${active.size} in cloud, change ${list.currentChangeNumber}")
    }
}

fun push(game: File, force: Boolean) {
    val state = loadState()
    if (state.changeNumber == 0L && !force) die("never synced on this device; run pull, or push --force to make this device's saves win")
    Session(false).use { s ->
        val (list, files) = s.listFiles()
        if (list.currentChangeNumber != state.changeNumber && !force)
            throw CloudChangedException("cloud changed since last pull (${state.changeNumber} -> ${list.currentChangeNumber}), another device synced; pull first")
        val cloudSha = files.filter { !it.deleted }.associate { it.rel to it.sha }
        val local = localFiles(game).mapValues { sha1(it.value) }
        val changed = local.filter { (rel, sha) -> cloudSha[rel] != sha }
        // Synced before and deleted here since (the game rotates story/<n> on save), or never meant to sync.
        val toDelete = cloudSha.keys.filter { (it in state.files && it !in local) || !synced(it) }
        if (changed.isEmpty() && toDelete.isEmpty()) { log("push: nothing changed"); return }
        if (state.clientId == 0L) state.clientId = System.nanoTime()
        val batch = s.cloud.beginAppUploadBatch(APP_ID, filesToUpload = changed.keys.map { ROOT + it },
            filesToDelete = toDelete.map { ROOT + it },
            clientId = state.clientId, appBuildId = 0).get()
        var ok = true
        // The batch only announces deletes; each one still needs its own call.
        val rpc = s.client.getHandler(SteamUnifiedMessages::class.java)!!.createService<Cloud>()
        for (rel in toDelete) {
            val req = CCloud_ClientDeleteFile_Request.newBuilder().setAppid(APP_ID).setFilename(ROOT + rel)
                .setIsExplicitDelete(true).setUploadBatchId(batch.batchID).build()
            val res = rpc.clientDeleteFile(req).toFuture().get()
            if (res.result != EResult.OK) { ok = false; log("delete $rel: ${res.result}") }
        }
        for ((rel, sha) in changed) {
            val file = File(game, rel)
            val bytes = file.readBytes()
            val info = s.cloud.beginFileUpload(APP_ID, bytes.size, bytes.size, unhex(sha), Date(file.lastModified()),
                ROOT + rel, canEncrypt = false, uploadBatchId = batch.batchID).get()
            var fileOk = true
            for (b in info.blockRequests) {
                val body = if (b.explicitBodyData.isNotEmpty()) b.explicitBodyData
                    else bytes.copyOfRange(b.blockOffset.toInt(), b.blockOffset.toInt() + b.blockLength)
                val req = Request.Builder().url((if (b.useHttps) "https://" else "http://") + b.urlHost + b.urlPath)
                    .headers(Headers.headersOf(*b.requestHeaders.flatMap { listOf(it.name, it.value) }.toTypedArray()))
                    .put(body.toRequestBody()).build()
                http.newCall(req).execute().use { r -> if (!r.isSuccessful) { fileOk = false; log("upload $rel: HTTP ${r.code}") } }
            }
            val committed = s.cloud.commitFileUpload(fileOk, APP_ID, unhex(sha), ROOT + rel).get()
            if (!fileOk || !committed) ok = false else log("pushed $rel")
        }
        s.cloud.completeAppUploadBatch(APP_ID, batch.batchID, if (ok) EResult.OK else EResult.Fail).get()
        if (!ok) die("some uploads failed; cloud batch marked failed, local saves untouched")
        state.changeNumber = batch.appChangeNumber
        state.files.clear()
        state.files.putAll(cloudSha - toDelete.toSet() + changed)
        toDelete.forEach { log("deleted from cloud $it") }
        saveState(state)
        log("push done: ${changed.size} uploaded, ${toDelete.size} deleted, change ${batch.appChangeNumber}")
    }
}

fun main(argv: Array<String>) {
    if (System.getenv("WMCLOUD_DEBUG") != null) LogManager.addListener(DefaultLogListener())
    val args = argv.toList()
    val force = "--force" in args
    try { run(args, force) } catch (e: WmCloudException) {
        System.err.println("wmcloud: ${e.message}")
        exitProcess(1)
    }
    exitProcess(0)
}

private fun run(args: List<String>, force: Boolean) {
    when (args.firstOrNull()) {
        "login" -> Session(true).use { println("logged in; token saved to ${configDir.path}/token.json") }
        "list" -> Session(false).use { s ->
            val (list, files) = s.listFiles()
            files.forEach { println("${if (it.deleted) "D" else " "} ${it.size.toString().padStart(9)} ${it.sha.take(8)} ${it.rel}") }
            println("${files.size} files, change ${list.currentChangeNumber}")
        }
        "pull" -> pull(gameDir(args), force)
        "push" -> push(gameDir(args), force)
        "achievements" -> achievements(gameDir(args), "--submit" in args, "-v" in args)
        else -> die("usage: wmcloud login | list | pull --game <dir> [--force] | push --game <dir> [--force] | achievements --game <dir> [-v] [--submit]")
    }
}
