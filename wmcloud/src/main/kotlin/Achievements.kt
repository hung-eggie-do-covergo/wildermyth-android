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

fun achievements(game: File, submit: Boolean, verbose: Boolean) {
    val earned = localAspects(game)
    val store = StoreStatsHandler()
    Session(false, listOf(store)).use { s ->
        val stats = s.client.getHandler(SteamUserStats::class.java)!!
        val me = s.client.steamID!!
        val snap = stats.getUserStats(APP_ID, me).toFuture().get(60, TimeUnit.SECONDS)
        if (snap.result != EResult.OK) die("getUserStats failed: ${snap.result}")
        val schema = snap.getExpandedAchievements().filter { !it.name.isNullOrBlank() }
        if (schema.isEmpty()) die("Steam returned no achievement schema")

        // ponytail: exact aspect-id == API-name matches only; counter-based ones (achievementProgress_*) are skipped
        val missing = schema.filter { !it.isUnlocked && it.name in earned }
        println("${schema.count { it.isUnlocked }}/${schema.size} unlocked on Steam; ${schema.count { it.name in earned }} earned locally by exact name")
        if (verbose) schema.sortedBy { it.unlockTimestamp }.forEach {
            val t = if (it.isUnlocked) java.time.Instant.ofEpochSecond(it.unlockTimestamp.toLong()).toString().take(10) else "locked    "
            println("  $t ${if (it.name in earned) "L" else " "} ${it.name}: ${it.displayName} - ${it.description}")
        }
        missing.forEach { println("  missing on Steam: ${it.name} (${it.displayName}: ${it.description})") }
        if (missing.isEmpty()) { println("achievements: in sync"); return }
        if (!submit) { println("dry run; rerun with --submit to unlock these on Steam"); return }

        // Each achievement is bit (id % 100) of stat block (id / 100); write whole blocks, existing bits kept.
        val masks = HashMap<Int, Int>()
        for (b in snap.achievementBlocks) masks[b.achievementId] = b.unlockTime.take(32).foldIndexed(0) { i, m, t -> if (t != 0) m or (1 shl i) else m }
        val touched = missing.map { it.achievementId / 100 to it.achievementId % 100 }.onEach { (block, bit) ->
            if (block <= 0 || bit !in 0..31) die("bad schema bit for block $block bit $bit")
            masks[block] = (masks[block] ?: 0) or (1 shl bit)
        }.map { it.first }.toSortedSet()

        val msg = ClientMsgProtobuf<CMsgClientStoreUserStats2.Builder>(CMsgClientStoreUserStats2::class.java, EMsg.ClientStoreUserStats2).apply {
            body.gameId = APP_ID.toLong()
            body.settorSteamId = me.convertToUInt64()
            body.setteeSteamId = me.convertToUInt64()
            body.crcStats = snap.crcStats
            body.explicitReset = false
            touched.forEach { body.addStats(CMsgClientStoreUserStats2.Stats.newBuilder().setStatId(it).setStatValue(masks.getValue(it))) }
        }
        store.pending = CompletableFuture()
        s.client.send(msg)
        val res = store.pending!!.get(60, TimeUnit.SECONDS)
        val result = EResult.from(res.eresult)
        if (result != EResult.OK || res.statsOutOfDate || res.statsFailedValidationCount > 0)
            die("Steam rejected the unlock: $result, outOfDate=${res.statsOutOfDate}, failed=${res.statsFailedValidationList.map { it.statId }}")
        missing.forEach { println("unlocked ${it.name}") }
    }
}
