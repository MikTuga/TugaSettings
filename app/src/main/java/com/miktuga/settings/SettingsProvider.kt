package com.miktuga.settings

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.UriMatcher
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.miktuga.design.settings.TugaSetting

/**
 * Backing ContentProvider for tuga-settings.
 *
 * Permission model: manifest declares `readPermission`/`writePermission` with
 * `protectionLevel="normal"` — any Tuga-namespaced app (or third-party app) can read/write
 * settings if it declares `<uses-permission>` for the corresponding permission.
 *
 * Multi-repo ecosystem (since v0.1.1): community-maintained apps sign with their own keys,
 * so signature-level enforcement was removed. Soft package-name check stays as a polite
 * namespacing guard.
 */
class SettingsProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.miktuga.settings.provider"
        const val OUR_PACKAGE = "com.miktuga.settings"
        const val PATH_SETTINGS = "settings"
        const val PREFS_NAME = "tugasettings_store"

        const val COL_KEY = "key"
        const val COL_VALUE = "value"

        // SharedPreferences serializes the whole file on every apply(); without a cap,
        // a signed-but-buggy caller could write a multi-megabyte string and stall every
        // other Tuga app's settings reads. 8 KB easily fits every realistic setting
        // (longest current value is a filesystem path).
        private const val MAX_VALUE_BYTES = 8 * 1024

        private const val MATCH_ALL = 1
        private const val MATCH_BY_KEY = 2

        private val URI_MATCHER = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, PATH_SETTINGS, MATCH_ALL)
            addURI(AUTHORITY, "$PATH_SETTINGS/*", MATCH_BY_KEY)
        }
    }

    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String? = when (URI_MATCHER.match(uri)) {
        MATCH_ALL -> "vnd.android.cursor.dir/vnd.$AUTHORITY.$PATH_SETTINGS"
        MATCH_BY_KEY -> "vnd.android.cursor.item/vnd.$AUTHORITY.$PATH_SETTINGS"
        else -> null
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        enforceCallerIsTugaSigned()
        val ctx = context ?: return MatrixCursor(arrayOf(COL_KEY, COL_VALUE))
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val requestedKey: String? = when (URI_MATCHER.match(uri)) {
            MATCH_BY_KEY -> uri.lastPathSegment
            MATCH_ALL -> resolveKeyFromSelection(selection, selectionArgs)
            else -> null
        }
        val cols = projection ?: arrayOf(COL_KEY, COL_VALUE)
        val cursor = MatrixCursor(cols)

        val entries: List<Pair<String, String?>> = if (requestedKey != null) {
            val stored = prefs.getString(requestedKey, null)
            val value = stored ?: defaultFor(requestedKey)
            if (value != null) listOf(requestedKey to value) else emptyList()
        } else {
            TugaSetting.all.map { setting ->
                val stored = prefs.getString(setting.key, null)
                setting.key to (stored ?: defaultSerialized(setting))
            }
        }

        for ((k, v) in entries) {
            val row = arrayOfNulls<Any>(cols.size)
            cols.forEachIndexed { i, col ->
                row[i] = when (col) {
                    COL_KEY -> k
                    COL_VALUE -> v
                    else -> null
                }
            }
            cursor.addRow(row)
        }
        return cursor
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        enforceCallerIsTugaSigned()
        if (values == null) return null
        val key = values.getAsString(COL_KEY) ?: return null
        // Reject null value: SharedPreferences.putString(key, null) silently *removes* the entry,
        // which would let a buggy caller wipe a setting via what looks like a write.
        val value = values.getAsString(COL_VALUE) ?: return null
        if (!isValueSizeOk(value)) return null
        if (TugaSetting.byKey(key) == null) return null
        val ctx = context ?: return null
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(key, value)
            .apply()
        val out = Uri.withAppendedPath(Uri.parse("content://$AUTHORITY/$PATH_SETTINGS"), key)
        ctx.contentResolver.notifyChange(out, null)
        return out
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        enforceCallerIsTugaSigned()
        if (values == null) return 0
        val key = values.getAsString(COL_KEY)
            ?: (if (URI_MATCHER.match(uri) == MATCH_BY_KEY) uri.lastPathSegment else null)
            ?: resolveKeyFromSelection(selection, selectionArgs)
            ?: return 0
        // Same null-value guard as insert(): writes should never silently remove.
        val value = values.getAsString(COL_VALUE) ?: return 0
        if (!isValueSizeOk(value)) return 0
        if (TugaSetting.byKey(key) == null) return 0
        val ctx = context ?: return 0
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(key, value)
            .apply()
        ctx.contentResolver.notifyChange(uri, null)
        return 1
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        enforceCallerIsTugaSigned()
        val ctx = context ?: return 0
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = (if (URI_MATCHER.match(uri) == MATCH_BY_KEY) uri.lastPathSegment else null)
            ?: resolveKeyFromSelection(selection, selectionArgs)
            // Refuse bulk clear: an accidental `delete(CONTENT_URI, null, null)` from any
            // signed Tuga app would otherwise wipe every cross-app preference.
            ?: return 0
        val existed = prefs.contains(key)
        prefs.edit().remove(key).apply()
        ctx.contentResolver.notifyChange(uri, null)
        return if (existed) 1 else 0
    }

    private fun isValueSizeOk(value: String): Boolean =
        value.toByteArray(Charsets.UTF_8).size <= MAX_VALUE_BYTES

    /**
     * Soft package-name namespacing — community apps signed with their own keys are allowed,
     * but a polite reminder if calling package isn't under com.miktuga.* namespace.
     * Spoofable, NOT a security boundary. Real boundary is the `normal` permission in manifest.
     */
    private fun enforceCallerIsTugaSigned() {
        val caller = callingPackage ?: return  // allow null caller (system queries, etc.)
        if (caller == OUR_PACKAGE) return
        if (!caller.startsWith("com.miktuga.")) {
            android.util.Log.w(
                "SettingsProvider",
                "Non-Tuga caller \"$caller\" reading/writing settings. Allowed but unexpected."
            )
        }
    }

    private fun resolveKeyFromSelection(
        selection: String?,
        selectionArgs: Array<out String>?
    ): String? {
        if (selection == null || selectionArgs.isNullOrEmpty()) return null
        val normalized = selection.replace(" ", "")
        return if (normalized.equals("$COL_KEY=?", ignoreCase = true)) selectionArgs[0] else null
    }

    private fun defaultFor(key: String): String? =
        TugaSetting.byKey(key)?.let { defaultSerialized(it) }

    private fun defaultSerialized(setting: TugaSetting<*>): String {
        val v: Any = setting.default
        return when (v) {
            is Enum<*> -> v.name
            else -> v.toString()
        }
    }
}
