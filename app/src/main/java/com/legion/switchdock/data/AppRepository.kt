package com.legion.switchdock.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.legion.switchdock.display.DisplayProfile
import java.io.File
import java.io.FileOutputStream

data class AppEntry(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap,
    val isSystemApp: Boolean,
    val banner: ImageBitmap? = null,
    val category: Int = ApplicationInfo.CATEGORY_UNDEFINED
)

object AppRepository {
    const val ARTEMIS_LEGION_PACKAGE = "com.limelight.legion"
    const val PLAY_PC_ID = "legion:play_pc"
    private const val PLAY_PC_ACTIVITY = "com.limelight.legion.LegionPlayActivity"

    fun isVirtualTile(packageName: String?): Boolean = packageName?.startsWith("legion:") == true

    /** One-tap "Play PC" tile: resumes the last Artemis Legion stream on whatever screen the hub is on. */
    fun playPcTile(context: Context): AppEntry? {
        val pm = context.packageManager
        val icon = runCatching { pm.getApplicationIcon(ARTEMIS_LEGION_PACKAGE) }.getOrNull() ?: return null
        return AppEntry(
            packageName = PLAY_PC_ID,
            label = "Play PC",
            icon = drawableToImageBitmap(icon),
            isSystemApp = false,
            banner = loadCustomBanner(context, PLAY_PC_ID),
            category = ApplicationInfo.CATEGORY_GAME
        )
    }

    fun playPcIntent(): Intent = Intent().apply {
        setClassName(ARTEMIS_LEGION_PACKAGE, PLAY_PC_ACTIVITY)
    }

    // Priority keywords/packages for smart first-run pinning on the Switch-style TV row
    private val preferredPackages = listOf(
        "com.limelight.legion",        // Artemis Legion (auto TV/glasses settings)
        "com.limelight.noir",          // Artemis (Apollo client)
        "com.limelight",               // Moonlight
        "com.valvesoftware.steamlink", // Steam Link
        "com.xiaoji.egggame",          // GameHub
        "com.google.android.youtube",  // YouTube
        "com.retroarch",               // RetroArch
        "com.retroarch.aarch64",       // RetroArch 64
        "org.dolphinemu.dolphinemu",   // Dolphin
        "org.ppsspp.ppsspp",           // PPSSPP
        "xyz.aethersx2.android",       // AetherSX2 / NetherSX2
        "com.android.chrome"           // Chrome
    )

    private val preferredNameKeywords = listOf(
        "artemis", "moonlight", "apollo", "steam", "gamehub",
        "anikoto", "youtube", "retroarch", "dolphin", "ppsspp",
        "nethersx2", "aethersx2", "yuzu", "sudachi", "citron", "cemu"
    )

    // Glasses lean toward movies, anime and streaming (plus game streaming)
    private val glassesPreferredPackages = listOf(
        "com.google.android.youtube",
        "com.nuvio.app",
        "com.netflix.mediaclient",
        "com.crunchyroll.crunchyroid",
        "com.plexapp.android",
        "com.stremio.one",
        "com.disney.disneyplus",
        "com.hulu.plus",
        "com.wbd.stream",
        "com.amazon.avod.thirdpartyclient",
        "org.videolan.vlc",
        "com.limelight.noir",
        "com.limelight",
        "com.valvesoftware.steamlink",
        "com.limelight.legion"
    )

    private val glassesNameKeywords = listOf(
        "nuvio", "anikoto", "youtube", "netflix", "crunchyroll", "plex", "stremio",
        "jellyfin", "disney", "hulu", "prime video", "vlc", "mx player", "artemis", "moonlight"
    )

    fun loadLaunchableApps(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolved = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        val ownPackage = context.packageName

        return resolved
            .asSequence()
            .filter { it.activityInfo.packageName != ownPackage }
            .distinctBy { it.activityInfo.packageName }
            .mapNotNull { info ->
                val pkg = info.activityInfo.packageName
                val label = info.loadLabel(pm).toString().ifBlank { pkg }
                val drawable = runCatching { info.loadIcon(pm) }.getOrNull() ?: return@mapNotNull null
                val isSystem = (info.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val appCategory = info.activityInfo.applicationInfo.category
                AppEntry(
                    packageName = pkg,
                    label = label,
                    icon = drawableToImageBitmap(drawable),
                    isSystemApp = isSystem,
                    banner = loadCustomBanner(context, pkg),
                    category = appCategory
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    fun getOrSeedPinnedApps(context: Context, allApps: List<AppEntry>, prefs: DockPreferences): List<AppEntry> {
        val byPackage = allApps.associateBy { it.packageName }

        val profile = prefs.activeProfile
        val isGlasses = profile == DisplayProfile.GLASSES
        if (!prefs.hasInitializedPins(profile)) {
            val seeded = mutableListOf<String>()
            val packages = if (isGlasses) glassesPreferredPackages else preferredPackages
            val keywords = if (isGlasses) glassesNameKeywords else preferredNameKeywords
            // 1. Exact preferred packages
            packages.forEach { pkg ->
                if (byPackage.containsKey(pkg) && !seeded.contains(pkg)) {
                    seeded.add(pkg)
                }
            }
            // 2. Keyword matches (e.g., Anikoto or custom builds)
            allApps.forEach { app ->
                val lowerLabel = app.label.lowercase()
                val lowerPkg = app.packageName.lowercase()
                if (keywords.any { lowerLabel.contains(it) || lowerPkg.contains(it) }) {
                    if (!seeded.contains(app.packageName)) {
                        seeded.add(app.packageName)
                    }
                }
            }
            // 3. Fallback: if still fewer than 4, add top non-system user apps
            if (seeded.size < 4) {
                allApps.filter { !it.isSystemApp }.forEach { app ->
                    if (seeded.size < 6 && !seeded.contains(app.packageName)) {
                        seeded.add(app.packageName)
                    }
                }
            }
            prefs.setPinnedPackages(seeded, profile)
            prefs.setInitializedPins(profile, true)
        }

        return prefs.getPinnedPackages(profile).mapNotNull { byPackage[it] }
    }

    private fun drawableToImageBitmap(drawable: Drawable): ImageBitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap.asImageBitmap()
        }
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 144
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 144
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap.asImageBitmap()
    }

    private fun bannerFile(context: Context, packageName: String): File {
        val dir = File(context.filesDir, "banners")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "$packageName.png")
    }

    fun loadCustomBanner(context: Context, packageName: String): ImageBitmap? {
        val file = bannerFile(context, packageName)
        if (!file.exists()) return null
        return runCatching {
            BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
        }.getOrNull()
    }

    fun saveCustomBanner(context: Context, packageName: String, uri: Uri): Boolean {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val decoded = BitmapFactory.decodeStream(input) ?: return false
                // Downscale large images to max 960x540 so TV Hub stays snappy
                val maxW = 960
                val maxH = 540
                val scaled = if (decoded.width > maxW || decoded.height > maxH) {
                    val ratio = minOf(maxW.toFloat() / decoded.width, maxH.toFloat() / decoded.height)
                    Bitmap.createScaledBitmap(
                        decoded,
                        (decoded.width * ratio).toInt().coerceAtLeast(1),
                        (decoded.height * ratio).toInt().coerceAtLeast(1),
                        true
                    )
                } else {
                    decoded
                }
                FileOutputStream(bannerFile(context, packageName)).use { out ->
                    scaled.compress(Bitmap.CompressFormat.PNG, 92, out)
                }
            } != null
        }.getOrDefault(false)
    }

    fun clearCustomBanner(context: Context, packageName: String) {
        runCatching { bannerFile(context, packageName).delete() }
    }

    private val gamePackages = setOf(
        ARTEMIS_LEGION_PACKAGE,
        PLAY_PC_ID,
        "com.limelight.noir",
        "com.limelight",
        "com.valvesoftware.steamlink",
        "com.xiaoji.egggame",
        "com.retroarch",
        "com.retroarch.aarch64",
        "org.dolphinemu.dolphinemu",
        "org.ppsspp.ppsspp",
        "org.ppsspp.ppssppgold",
        "xyz.aethersx2.android",
        "com.nvidia.geforcenow",
        "com.microsoft.xcloud",
        "com.xbox.cloudgaming",
        "com.playstation.remoteplay",
        "com.grill.chiaki"
    )

    private val gameKeywords = listOf(
        "artemis", "moonlight", "apollo", "steam", "gamehub",
        "retroarch", "dolphin", "ppsspp", "nethersx2", "aethersx2",
        "yuzu", "sudachi", "citron", "cemu", "geforce", "xbox",
        "chiaki", "emulator", "game", "arcade", "play pc"
    )

    private val mediaPackages = setOf(
        "com.google.android.youtube",
        "com.google.android.youtube.tv",
        "com.google.android.apps.youtube.music",
        "com.nuvio.app",
        "com.netflix.mediaclient",
        "com.crunchyroll.crunchyroid",
        "com.plexapp.android",
        "com.stremio.one",
        "com.disney.disneyplus",
        "com.hulu.plus",
        "com.wbd.stream",
        "com.amazon.avod.thirdpartyclient",
        "org.videolan.vlc",
        "com.spotify.music",
        "org.jellyfin.mobile",
        "org.xbmc.kodi",
        "tv.twitch.android.app"
    )

    private val mediaKeywords = listOf(
        "youtube", "netflix", "crunchyroll", "plex", "stremio",
        "jellyfin", "disney", "hulu", "prime video", "vlc",
        "mx player", "spotify", "twitch", "kodi", "nuvio",
        "anikoto", "anime", "video", "music", "media", "tv", "cinema"
    )

    fun isGameApp(app: AppEntry): Boolean {
        if (app.packageName in gamePackages) return true
        if (app.category == ApplicationInfo.CATEGORY_GAME) return true
        val lowerLabel = app.label.lowercase()
        val lowerPkg = app.packageName.lowercase()
        return gameKeywords.any { lowerLabel.contains(it) || lowerPkg.contains(it) }
    }

    fun isMediaApp(app: AppEntry): Boolean {
        if (app.packageName in mediaPackages) return true
        if (app.category == ApplicationInfo.CATEGORY_VIDEO || app.category == ApplicationInfo.CATEGORY_AUDIO) return true
        val lowerLabel = app.label.lowercase()
        val lowerPkg = app.packageName.lowercase()
        return mediaKeywords.any { lowerLabel.contains(it) || lowerPkg.contains(it) }
    }
}
