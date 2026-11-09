package ca.gibert.movietracker.ui.components

import android.content.Context
import android.content.Intent

/**
 * A streaming service's native Android app.
 *
 * [packages] lists every known package for the service, phone and TV builds
 * alike, in preference order: the first one installed is the one we launch.
 * Every package here must also be declared in the manifest's `<queries>`
 * block, otherwise package visibility (API 30+) hides it from us.
 */
data class StreamingApp(
    val name: String,
    val packages: List<String>,
)

private val Netflix = StreamingApp("Netflix", listOf("com.netflix.mediaclient", "com.netflix.ninja"))
private val Max = StreamingApp("Max", listOf("com.wbd.stream", "com.hbo.hbonow"))
private val AppleTv = StreamingApp(
    "Apple TV",
    listOf("com.apple.atve.androidtv.appletv", "com.apple.atve.amazon.appletv"),
)
private val DisneyPlus = StreamingApp("Disney+", listOf("com.disney.disneyplus"))
private val Hulu = StreamingApp("Hulu", listOf("com.hulu.plus", "com.hulu.livingroomplus"))
private val PrimeVideo = StreamingApp(
    "Prime Video",
    listOf("com.amazon.avod.thirdpartyclient", "com.amazon.amazonvideo.livingroom"),
)
private val ParamountPlus = StreamingApp("Paramount+", listOf("com.cbs.app", "com.cbs.ott"))
private val Starz = StreamingApp("Starz", listOf("com.bydeluxe.d3.android.program.starz"))
private val BbcIplayer = StreamingApp("BBC iPlayer", listOf("bbc.iplayer.android"))
private val Peacock = StreamingApp("Peacock", listOf("com.peacocktv.peacockandroid"))
private val DiscoveryPlus = StreamingApp(
    "discovery+",
    listOf("com.discovery.discoveryplus.mobile", "com.discovery.discoveryplus.androidtv"),
)
private val Crunchyroll = StreamingApp("Crunchyroll", listOf("com.crunchyroll.crunchyroid"))
private val RokuChannel = StreamingApp("The Roku Channel", listOf("com.roku.web.trc"))
private val Tubi = StreamingApp("Tubi", listOf("com.tubitv"))
private val Plex = StreamingApp("Plex", listOf("com.plexapp.android"))
private val Mubi = StreamingApp("MUBI", listOf("com.mubi"))

/** Every app we know how to launch; the manifest's `<queries>` must cover these. */
internal val allStreamingApps: List<StreamingApp> = listOf(
    Netflix, Max, AppleTv, DisneyPlus, Hulu, PrimeVideo, ParamountPlus, Starz,
    BbcIplayer, Peacock, DiscoveryPlus, Crunchyroll, RokuChannel, Tubi, Plex, Mubi,
)

/**
 * Maps a service or network name onto the app that streams it.
 *
 * Matching follows [serviceLogoFor] so the badge and the launch link agree.
 * Broadcast and cable networks resolve to the service that carries their
 * catalogue in the US: FX to Hulu, CBS and Showtime to Paramount+, NBC to
 * Peacock, HBO to Max.
 */
internal fun streamingAppFor(network: String?): StreamingApp? {
    val key = network?.trim()?.lowercase().orEmpty()
    if (key.isEmpty()) return null

    return when {
        "max" in key -> Max
        "apple" in key -> AppleTv
        "netflix" in key -> Netflix
        "disney" in key -> DisneyPlus
        "fx" in key -> Hulu
        "hulu" in key -> Hulu
        "amazon" in key || "prime video" in key -> PrimeVideo
        "paramount" in key -> ParamountPlus
        "hbo" in key -> Max
        "showtime" in key -> ParamountPlus
        "starz" in key -> Starz
        "bbc" in key -> BbcIplayer
        "cbs" in key -> ParamountPlus
        "nbc" in key || "peacock" in key -> Peacock
        "discovery" in key -> DiscoveryPlus
        "crunchyroll" in key -> Crunchyroll
        "roku" in key -> RokuChannel
        "tubi" in key -> Tubi
        "plex" in key -> Plex
        "mubi" in key -> Mubi
        else -> null
    }
}

/**
 * An intent that opens [app], or null when none of its packages is installed.
 *
 * Falls back to the Leanback launcher entry so TV-only builds still open on
 * Android TV, where they often have no regular launcher activity.
 */
fun launchIntentFor(context: Context, app: StreamingApp): Intent? {
    val pm = context.packageManager
    return app.packages.firstNotNullOfOrNull { pkg ->
        pm.getLaunchIntentForPackage(pkg) ?: pm.getLeanbackLaunchIntentForPackage(pkg)
    }?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
