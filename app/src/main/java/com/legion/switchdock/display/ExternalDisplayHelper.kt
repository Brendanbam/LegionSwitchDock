package com.legion.switchdock.display

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.view.Display
import com.legion.switchdock.data.DockPreferences
import com.legion.switchdock.shizuku.ShizukuHelper
import com.legion.switchdock.shizuku.ShizukuStatus
import kotlin.math.roundToInt

/** What kind of screen is plugged into the USB-C port. Each profile gets its own menu, pins, theme and display mode. */
enum class DisplayProfile(val id: String, val label: String) {
    TV("tv", "TV"),
    GLASSES("glasses", "XR Glasses");

    companion object {
        fun fromId(id: String?): DisplayProfile? = entries.firstOrNull { it.id == id }
    }
}

data class ExternalDisplayMode(
    val modeId: Int,
    val width: Int,
    val height: Int,
    val refreshRateHz: Int,
    val refreshRateExact: Float = refreshRateHz.toFloat()
) {
    val label: String
        get() = "${width}×${height} @ ${refreshRateHz}Hz"

    /** Stable key (mode IDs change between plug-ins, resolution/refresh do not). */
    val key: String
        get() = "${width}x${height}@${refreshRateHz}"
}

data class ExternalDisplayInfo(
    val displayId: Int,
    val name: String,
    val width: Int,
    val height: Int,
    val refreshRateHz: Int,
    val activeModeId: Int = 0,
    val supportedModes: List<ExternalDisplayMode> = emptyList(),
    val profile: DisplayProfile = DisplayProfile.TV,
    val profileAutoDetected: Boolean = true
) {
    val isGlasses: Boolean
        get() = profile == DisplayProfile.GLASSES

    val summary: String
        get() = "${width}×${height} • ${refreshRateHz}Hz"
}

object ExternalDisplayHelper {

    // EDID names reported by common XR / AR display glasses (XREAL xbx a01, XREAL Air/One, VITURE, Rokid, RayNeo...)
    private val glassesNamePattern = Regex(
        "xreal|nreal|xbx|\\ba01\\b|viture|rokid|rayneo|tcl ?nxt|inmo|\\bair ?[0-9]?\\b|glasses|\\bar\\b|\\bxr\\b",
        RegexOption.IGNORE_CASE
    )

    fun looksLikeGlasses(name: String): Boolean = glassesNamePattern.containsMatchIn(name)

    fun currentProfile(context: Context): DisplayProfile =
        findExternalDisplay(context)?.profile ?: DisplayProfile.TV

    /**
     * Picks the display mode for this screen: the mode the user locked for this profile if the
     * display supports it, otherwise the profile default (TV = highest resolution, Glasses = highest refresh).
     */
    fun resolvePreferredMode(context: Context, info: ExternalDisplayInfo): ExternalDisplayMode? {
        val prefs = DockPreferences(context)
        val savedKey = prefs.getPreferredModeKey(info.profile)
        // Ignore an old automatic pick of cinema 4K (4096 wide) on a TV
        if (info.profile == DisplayProfile.TV && savedKey.startsWith("4096x")) prefs.setPreferredModeKey(info.profile, "")
        else info.supportedModes.firstOrNull { it.key == savedKey }?.let { return it }
        // Carry over a TV mode locked before profiles existed
        if (savedKey.isEmpty() && info.profile == DisplayProfile.TV && prefs.preferredDisplayModeId != 0) {
            info.supportedModes.firstOrNull { it.modeId == prefs.preferredDisplayModeId && it.width != 4096 }?.let { return it }
        }
        return info.supportedModes.firstOrNull()
    }

    fun findExternalDisplay(context: Context): ExternalDisplayInfo? {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val displays = dm.displays
        val external = displays.firstOrNull { isPhysicalExternalDisplay(it) } ?: return null
        val mode = external.mode
        val displayName = external.name ?: "External Display"
        val override = DisplayProfile.fromId(DockPreferences(context).getDisplayTypeOverride(displayName))
        val profile = override
            ?: if (looksLikeGlasses(displayName)) DisplayProfile.GLASSES else DisplayProfile.TV
        val modes = external.supportedModes
            .map {
                ExternalDisplayMode(
                    modeId = it.modeId,
                    width = it.physicalWidth,
                    height = it.physicalHeight,
                    refreshRateHz = it.refreshRate.roundToInt(),
                    refreshRateExact = it.refreshRate
                )
            }
            .distinctBy { "${it.width}x${it.height}@${it.refreshRateHz}" }
            .sortedWith(
                if (profile == DisplayProfile.GLASSES) {
                    // Glasses: refresh rate first (120Hz unlocks xbx stabilization), then resolution
                    compareByDescending<ExternalDisplayMode> { it.refreshRateHz }
                        .thenByDescending { it.width * it.height }
                } else {
                    // TV: 3840×2160 @ 60Hz first. TVs also list 4096×2160 (cinema 4K, wider than the
                    // screen), which many TVs crop, show black bars for or refuse, so 16:9 modes come first.
                    compareByDescending<ExternalDisplayMode> { it.width == 3840 && it.height == 2160 && it.refreshRateHz == 60 }
                        .thenByDescending { it.width * 9 == it.height * 16 }
                        .thenByDescending { it.refreshRateHz == 60 }
                        .thenByDescending { it.refreshRateHz >= 60 }
                        .thenByDescending { it.width * it.height }
                        .thenByDescending { it.refreshRateHz }
                }
            )

        return ExternalDisplayInfo(
            displayId = external.displayId,
            name = displayName,
            width = mode.physicalWidth,
            height = mode.physicalHeight,
            refreshRateHz = mode.refreshRate.roundToInt(),
            activeModeId = mode.modeId,
            supportedModes = modes,
            profile = profile,
            profileAutoDetected = override == null
        )
    }

    fun isPhysicalExternalDisplay(display: Display): Boolean {
        if (display.displayId == Display.DEFAULT_DISPLAY) return false
        if (display.state == Display.STATE_OFF) return false
        // Ignore private/virtual presentation displays (e.g. screen recorders)
        val flags = display.flags
        val isPrivate = (flags and Display.FLAG_PRIVATE) != 0
        return !isPrivate
    }

    fun launchIntentOnDisplay(
        context: Context,
        intent: Intent,
        displayId: Int
    ): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val primarySuccess = runCatching {
            val options = ActivityOptions.makeBasic().apply {
                launchDisplayId = displayId
            }
            // Force WINDOWING_MODE_FULLSCREEN (1) on Lenovo ZUI Extended Mode so apps don't open in tiny floating boxes
            runCatching {
                val method = ActivityOptions::class.java.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
                method.invoke(options, 1) // 1 = WINDOWING_MODE_FULLSCREEN
            }
            context.startActivity(intent, options.toBundle())
            true
        }.getOrDefault(false)

        if (primarySuccess) return true

        // Clean fallback without reflection/windowing-mode overrides
        return runCatching {
            val fallbackOptions = ActivityOptions.makeBasic().apply {
                if (displayId != Display.DEFAULT_DISPLAY) {
                    launchDisplayId = displayId
                }
            }
            context.startActivity(intent, fallbackOptions.toBundle())
            true
        }.getOrDefault(false)
    }

    fun launchPackageOnDisplay(
        context: Context,
        packageName: String,
        displayId: Int
    ): Boolean {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return false
        launchIntent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        )
        return launchAppOnDisplay(context, launchIntent, displayId)
    }

    /**
     * Opens an app on the TV / glasses. On the glasses with a screen size below 100%, the app opens
     * in a centered window of that size; the black around it reads as see-through, so the picture
     * looks smaller and further away. Otherwise it opens full screen as usual.
     */
    fun launchAppOnDisplay(context: Context, intent: Intent, displayId: Int): Boolean {
        val info = findExternalDisplay(context)
        val pct = DockPreferences(context).glassesScreenSizePct
        if (info == null || !info.isGlasses || info.displayId != displayId || pct >= 100) {
            return launchIntentOnDisplay(context, intent, displayId)
        }
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val display = dm.getDisplay(displayId) ?: return launchIntentOnDisplay(context, intent, displayId)
        val size = android.graphics.Point()
        @Suppress("DEPRECATION") display.getRealSize(size)
        val w = size.x * pct / 100
        val h = size.y * pct / 100
        val left = (size.x - w) / 2
        val top = (size.y - h) / 2
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val ok = runCatching {
            val options = ActivityOptions.makeBasic().apply {
                launchDisplayId = displayId
                setLaunchBounds(Rect(left, top, left + w, top + h))
            }
            runCatching {
                ActivityOptions::class.java.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
                    .invoke(options, 5) // 5 = WINDOWING_MODE_FREEFORM
            }
            context.startActivity(intent, options.toBundle())
            true
        }.getOrDefault(false)
        return ok || launchIntentOnDisplay(context, intent, displayId)
    }
}
