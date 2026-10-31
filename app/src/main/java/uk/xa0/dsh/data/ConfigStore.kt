package uk.xa0.dsh.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import uk.xa0.dsh.model.DrawerGrouping
import uk.xa0.dsh.net.AcceptedCert
import uk.xa0.dsh.net.decodeAcceptedCerts
import uk.xa0.dsh.net.encodeAcceptedCerts
import uk.xa0.dsh.term.TerminalCellSize
import uk.xa0.dsh.term.decodeHostShellSessions
import uk.xa0.dsh.term.encodeHostShellSessions

/**
 * Credentials live in EncryptedSharedPreferences (AES-256-GCM, key in the Android
 * Keystore) rather than plain prefs, because the password is kept so the app can
 * silently re-login when a session token expires (the auth proxy's default TTL
 * is 24h).
 */
class ConfigStore(context: Context) {

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "dsh_config",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun load(): DshConfig = DshConfig(
        baseUrl = prefs.getString(KEY_BASE_URL, "").orEmpty(),
        lanUrl = prefs.getString(KEY_LAN_URL, "").orEmpty(),
        username = prefs.getString(KEY_USERNAME, "").orEmpty(),
        password = prefs.getString(KEY_PASSWORD, "").orEmpty(),
        manualCookie = prefs.getString(KEY_COOKIE, "").orEmpty(),
        themeMode = prefs.getString(KEY_THEME, ThemeMode.SYSTEM) ?: ThemeMode.SYSTEM,
        hostThemeMode = prefs.getString(KEY_HOST_THEME, "").orEmpty(),
        agentPreset = prefs.getString(KEY_PRESET, "").orEmpty(),
        busyEnter = prefs.getString(KEY_BUSY_ENTER, BusyEnter.QUEUE) ?: BusyEnter.QUEUE,
        // The three-way mode is stored under the web's own ids. The old boolean key
        // is still read when the new one is absent, so an upgrade keeps the reader's
        // last choice instead of jumping back to sibling Workspaces.
        drawerGrouping = DrawerGrouping.fromStored(prefs.getString(KEY_DRAWER_GROUPING, null))
            ?: if (prefs.getBoolean(KEY_DRAWER_GROUP, true)) {
                DrawerGrouping.WORKSPACE
            } else {
                DrawerGrouping.FLAT
            },
        drawerOrderByUpdated = prefs.getBoolean(KEY_DRAWER_ORDER, false),
        drawerShowArchived = prefs.getBoolean(KEY_DRAWER_ARCHIVED, false),
        // `getStringSet` hands back the set the prefs object is still using, so a
        // caller that mutated it would edit stored state out from under the store
        // (and trip the "must not be modified" contract). Copy it on the way out.
        drawerCollapsedSections = prefs.getStringSet(KEY_DRAWER_COLLAPSED, null)
            ?.toSet()
            ?: emptySet(),
        hostShellSessions = decodeHostShellSessions(prefs.getString(KEY_HOST_SHELLS, null)),
        deepseekApiKey = prefs.getString(KEY_DEEPSEEK_KEY, "").orEmpty(),
        // Clamped on the way out, so a value written by another version — or edited
        // under `run-as` by a bug report — cannot hand the panel a cell size its own
        // bounds do not allow.
        terminalFontSp = TerminalCellSize.of(
            prefs.getFloat(KEY_TERMINAL_FONT_SP, TerminalCellSize.DEFAULT_FONT_SP),
        ).fontSp,
        acceptedCerts = decodeAcceptedCerts(prefs.getString(KEY_ACCEPTED_CERTS, null)),
    )

    fun save(config: DshConfig) {
        prefs.edit()
            .putString(KEY_BASE_URL, config.baseUrl)
            .putString(KEY_LAN_URL, config.lanUrl)
            .putString(KEY_USERNAME, config.username)
            .putString(KEY_PASSWORD, config.password)
            .putString(KEY_COOKIE, config.manualCookie)
            .putString(KEY_THEME, config.themeMode)
            .putString(KEY_HOST_THEME, config.hostThemeMode)
            .putString(KEY_PRESET, config.agentPreset)
            .putString(KEY_BUSY_ENTER, config.busyEnter)
            .putString(KEY_DRAWER_GROUPING, config.drawerGrouping.stored)
            // Keep the old key meaningful for a downgrade: a tree is still grouped,
            // so an older build opens on sibling Workspaces rather than the flat list.
            .putBoolean(KEY_DRAWER_GROUP, config.drawerGrouping.groupsByWorkspace)
            .putBoolean(KEY_DRAWER_ORDER, config.drawerOrderByUpdated)
            .putBoolean(KEY_DRAWER_ARCHIVED, config.drawerShowArchived)
            // The same aliasing hazard in reverse: `apply` may keep a reference to
            // the set, and the caller (a UiState copy) must not be able to mutate
            // what was stored. Pass a fresh copy.
            .putStringSet(KEY_DRAWER_COLLAPSED, config.drawerCollapsedSections.toSet())
            // Same hazard, same answer: the encoding copies the entries into a fresh
            // String, so nothing the caller still holds can reach the stored value.
            .putString(KEY_HOST_SHELLS, encodeHostShellSessions(config.hostShellSessions))
            .putString(KEY_DEEPSEEK_KEY, config.deepseekApiKey)
            .putFloat(KEY_TERMINAL_FONT_SP, config.terminalFontSp)
            .putString(KEY_ACCEPTED_CERTS, encodeAcceptedCerts(config.acceptedCerts))
            .apply()
    }

    fun clearCredentials() {
        prefs.edit().remove(KEY_PASSWORD).remove(KEY_COOKIE).remove(KEY_DEEPSEEK_KEY).apply()
    }

    private companion object {
        const val KEY_BASE_URL = "base_url"
        const val KEY_LAN_URL = "lan_url"
        const val KEY_USERNAME = "username"
        const val KEY_PASSWORD = "password"
        const val KEY_COOKIE = "cookie"
        const val KEY_THEME = "theme"
        const val KEY_HOST_THEME = "host_theme"
        const val KEY_PRESET = "preset"
        const val KEY_BUSY_ENTER = "busy_enter"
        const val KEY_DRAWER_GROUP = "drawer_group_by_workspace"
        const val KEY_DRAWER_GROUPING = "drawer_grouping"
        const val KEY_DRAWER_ORDER = "drawer_order_by_updated"
        const val KEY_DRAWER_ARCHIVED = "drawer_show_archived"
        const val KEY_DRAWER_COLLAPSED = "drawer_collapsed_sections"
        const val KEY_HOST_SHELLS = "host_shell_sessions"
        const val KEY_DEEPSEEK_KEY = "deepseek_api_key"
        const val KEY_TERMINAL_FONT_SP = "terminal_font_sp"
        const val KEY_ACCEPTED_CERTS = "accepted_certs"
    }
}
