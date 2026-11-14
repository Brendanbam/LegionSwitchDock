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
import java.io.File
import java.io.FileOutputStream

data class AppEntry(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap,
    val isSystemApp: Boolean,
    val banner: ImageBitmap? = null
)

object AppRepository {
    // Priority keywords/packages for smart first-run pinning on the Switch-style TV row
    private val preferredPackages = listOf(
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
                AppEntry(
                    packageName = pkg,
                    label = label,
                    icon = drawableToImageBitmap(drawable),
                    isSystemApp = isSystem,
                    banner = loadCustomBanner(context, pkg)
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    fun getOrSeedPinnedApps(context: Context, allApps: List<AppEntry>, prefs: DockPreferences): List<AppEntry> {
        val byPackage = allApps.associateBy { it.packageName }

        if (!prefs.hasInitializedPins) {
            val seeded = mutableListOf<String>()
            // 1. Exact preferred packages
            preferredPackages.forEach { pkg ->
                if (byPackage.containsKey(pkg) && !seeded.contains(pkg)) {
                    seeded.add(pkg)
                }
            }
            // 2. Keyword matches (e.g., Anikoto or custom builds)
            allApps.forEach { app ->
                val lowerLabel = app.label.lowercase()
                val lowerPkg = app.packageName.lowercase()
                if (preferredNameKeywords.any { lowerLabel.contains(it) || lowerPkg.contains(it) }) {
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
            prefs.setPinnedPackages(seeded)
            prefs.hasInitializedPins = true
        }

        return prefs.getPinnedPackages().mapNotNull { byPackage[it] }
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
}
