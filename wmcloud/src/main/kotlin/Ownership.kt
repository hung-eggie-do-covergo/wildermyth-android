package wmcloud

import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.steam.handlers.steamapps.SteamApps
import java.util.concurrent.TimeUnit

/** Wildermyth's DLC, as SteamManager.dlcId* in the game. */
val DLC_APP_IDS = listOf(2935580 /* Omenroad */, 2139130 /* Armors and Theme Skins */)

/** Which of [appIds] the signed-in account owns: Steam only issues ownership tickets for owned apps. */
fun ownedApps(appIds: List<Int>): List<Int> = Session(false).use { ownedApps(it, appIds) }

fun ownedApps(s: Session, appIds: List<Int>): List<Int> {
    val apps = s.client.getHandler(SteamApps::class.java)!!
    return appIds.filter { apps.getAppOwnershipTicket(it).toFuture().get(30, TimeUnit.SECONDS).result == EResult.OK }
}
