package com.funnelbase.sdk.internal

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.funnelbase.sdk.DeviceContext
import java.util.TimeZone

/**
 * Device and app context attached to every event.
 *
 * Read once at startup and cached — `track` should not touch PackageManager — except [region],
 * which is read per event: someone can change the phone's language or cross a time zone without
 * the process restarting, and those two pick a message's language and clock.
 *
 * Deliberately excluded: advertising id, ANDROID_ID, IMEI, and anything else that identifies a
 * device across apps. The SDK's identity model is its own `device_id` and `anonymous_id`, both
 * made by the SDK itself — a hardware identifier would survive the reinstall that is supposed
 * to make a new device, and the reset that is supposed to make a new anonymous stretch.
 */
internal object DeviceInfo {

    fun collect(context: Context): DeviceContext = DeviceContext(
        platform = "android",
        osVersion = Build.VERSION.RELEASE,
        appVersion = packageInfo(context)?.versionName,
        deviceModel = Build.MODEL,
        deviceManufacturer = Build.MANUFACTURER,
        locale = region(context).locale,
        timezone = region(context).timezone,
        // Left null: reporting it would mean requiring ACCESS_NETWORK_STATE in every host app.
        networkType = null,
    )

    /**
     * The phone's language and time zone as they are now. The language is the application's own
     * configuration, so a per-app language chosen in the system settings (Android 13+) wins over
     * the phone's.
     */
    fun region(context: Context): DeviceRegion = DeviceRegion(
        locale = context.resources.configuration.locales[0].toLanguageTag(),
        timezone = TimeZone.getDefault().id,
    )

    /**
     * When the operating system says the app was installed, in Unix epoch milliseconds — what
     * `$first_open` reports as `installed_at_ms`. Survives app updates and is reset by a
     * reinstall, which is exactly the lifetime of a `device_id`.
     *
     * Null when the package cannot be read, or when the value is the zero a stub returns, so
     * the property is left off rather than sent as 1970.
     */
    fun installedAtMillis(context: Context): Long? =
        packageInfo(context)?.firstInstallTime?.takeIf { it > 0 }

    private fun packageInfo(context: Context): PackageInfo? =
        try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            // Can happen mid-update. Not worth failing initialization over.
            null
        }
}
