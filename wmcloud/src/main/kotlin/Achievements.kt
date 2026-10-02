package wmcloud

import `in`.dragonbra.javasteam.base.ClientMsgProtobuf
import `in`.dragonbra.javasteam.base.IPacketMsg
import `in`.dragonbra.javasteam.enums.EMsg
import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.steam.handlers.ClientMsgHandler
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverUserstats.CMsgClientStoreUserStats2
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverUserstats.CMsgClientStoreUserStatsResponse
import `in`.dragonbra.javasteam.steam.handlers.steamuserstats.SteamUserStats
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

/** JavaSteam 1.8.0 can read stats but not store them; this sends and awaits ClientStoreUserStats2. */
class StoreStatsHandler : ClientMsgHandler() {
    @Volatile var pending: CompletableFuture<CMsgClientStoreUserStatsResponse>? = null

    override fun handleMsg(packetMsg: IPacketMsg) {
        if (packetMsg.msgType != EMsg.ClientStoreUserStatsResponse) return
        val msg = ClientMsgProtobuf<CMsgClientStoreUserStatsResponse.Builder>(CMsgClientStoreUserStatsResponse::class.java, packetMsg)
        pending?.complete(msg.body.build())
    }
}

/** Aspect ids the game recorded in each local profile's legacy, i.e. what it believes was earned. */
fun localAspects(game: File): Set<String> {
    val out = HashSet<String>()
    File(game, "players").listFiles()!!.map { File(it, "legacy.json.zip") }.filter { it.isFile }.forEach { f ->
        ZipFile(f).use { z ->
            val json = z.getInputStream(z.entries().nextElement()).reader().readText()
            // Unlock records look like {"aspectId":"achievement_maimedHero","firstEarned":...}.
            Regex("\"aspectId\":\"([^\"]+)\",\"firstEarned\":[1-9]").findAll(json).forEach { out.add(it.groupValues[1]) }
        }
    }
    return out
}

/** One achievement: its stat block, bit, and whether Steam has that bit set. */
data class Ach(val name: String, val title: String, val desc: String, val block: Int, val bit: Int, val unlockedAt: Int)

/** Parses the schema ourselves: JavaSteam 1.8.0 reads a missing "bit" field and puts every achievement on bit 0. */
fun parseSchema(snap: `in`.dragonbra.javasteam.steam.handlers.steamuserstats.callback.UserStatsCallback): List<Ach> {
    val times = snap.achievementBlocks.associate { it.achievementId to it.unlockTime }
    val out = snap.schemaKeyValues["stats"].children.flatMap { stat ->
        val block = stat.name?.toIntOrNull() ?: return@flatMap emptyList()
        stat["bits"].children.map { b ->
            // The child's key is the bit index.
            val bit = b.name?.toIntOrNull() ?: die("unreadable bit key in block $block")
            Ach(b["name"].value ?: die("unnamed achievement in block $block bit $bit"), b["display"]["name"]["english"].value ?: "", b["display"]["desc"]["english"].value ?: "",
                block, bit, times[block]?.getOrNull(bit) ?: 0)
        }
    }
    val dup = out.groupBy { it.block to it.bit }.filterValues { it.size > 1 }
    if (dup.isNotEmpty() || out.any { it.bit !in 0..31 }) die("schema maps several achievements to one bit: ${dup.keys}; refusing")
    return out
}

fun achievements(game: File, submit: Boolean, verbose: Boolean) {
    val store = StoreStatsHandler()
    Session(false, listOf(store)).use { achievements(it, store, game, submit, verbose) }
}

/** [store] must have been passed to [s] at creation: handlers can't be added once connected. */
fun achievements(s: Session, store: StoreStatsHandler, game: File, submit: Boolean, verbose: Boolean) {
    val earned = localAspects(game)
    val stats = s.client.getHandler(SteamUserStats::class.java)!!
    val me = s.client.steamID!!
    val snap = stats.getUserStats(APP_ID, me).toFuture().get(60, TimeUnit.SECONDS)
    if (snap.result != EResult.OK) die("getUserStats failed: ${snap.result}")
    val schema = parseSchema(snap)
    if (schema.isEmpty()) die("Steam returned no achievement schema")

    // ponytail: exact aspect-id == API-name matches only; counter-based ones (achievementProgress_*) are skipped
    val missing = schema.filter { it.unlockedAt == 0 && it.name in earned }
    log("${schema.count { it.unlockedAt != 0 }}/${schema.size} unlocked on Steam; ${schema.count { it.name in earned }} earned locally by exact name")
    if (verbose) schema.sortedBy { it.unlockedAt }.forEach {
        val t = if (it.unlockedAt != 0) java.time.Instant.ofEpochSecond(it.unlockedAt.toLong()).toString().take(10) else "locked    "
        log("  $t ${if (it.name in earned) "L" else " "} ${it.name}: ${it.title} - ${it.desc}")
    }
    missing.forEach { log("  missing on Steam: ${it.name} (${it.title}: ${it.desc})") }
    if (missing.isEmpty()) { log("achievements: in sync"); return }
    if (!submit) { log("dry run; rerun with --submit to unlock these on Steam"); return }

    // Write whole stat blocks: current bits plus the unlocks. Steam ignores bit clears sent this way.
    val before = snap.achievementBlocks.associate { b -> b.achievementId to b.unlockTime.take(32).foldIndexed(0) { i, m, t -> if (t != 0) m or (1 shl i) else m } }
    val after = before.toMutableMap()
    missing.forEach { after[it.block] = (after[it.block] ?: 0) or (1 shl it.bit) }
    // Guard: the bits that change must be exactly the ones asked for.
    val changed = after.keys.flatMap { blk -> (0..31).filter { ((before[blk] ?: 0) xor after.getValue(blk)) shr it and 1 == 1 }.map { blk to it } }.toSet()
    val intended = missing.map { it.block to it.bit }.toSet()
    if (changed != intended) die("refusing: would change $changed, intended $intended")
    val touched = changed.map { it.first }.toSortedSet()

    val msg = ClientMsgProtobuf<CMsgClientStoreUserStats2.Builder>(CMsgClientStoreUserStats2::class.java, EMsg.ClientStoreUserStats2).apply {
        body.gameId = APP_ID.toLong()
        body.settorSteamId = me.convertToUInt64()
        body.setteeSteamId = me.convertToUInt64()
        body.crcStats = snap.crcStats
        body.explicitReset = false
        touched.forEach { body.addStats(CMsgClientStoreUserStats2.Stats.newBuilder().setStatId(it).setStatValue(after.getValue(it))) }
    }
    store.pending = CompletableFuture()
    s.client.send(msg)
    val res = store.pending!!.get(60, TimeUnit.SECONDS)
    val result = EResult.from(res.eresult)
    if (result != EResult.OK || res.statsOutOfDate || res.statsFailedValidationCount > 0)
        die("Steam rejected the change: $result, outOfDate=${res.statsOutOfDate}, failed=${res.statsFailedValidationList.map { it.statId }}")
    missing.forEach { log("unlocked ${it.name}") }
}
