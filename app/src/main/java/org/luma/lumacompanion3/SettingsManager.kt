package org.luma.lumacompanion3

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SettingsManager {
    private const val PREFS_NAME = "luma_settings"
    private const val KEY_NAME = "patient_name"
    private const val KEY_PRONOUNS = "patient_pronouns"
    private const val KEY_PRONOUNS_CUSTOM = "patient_pronouns_custom"
    private const val KEY_RELIGION = "patient_religion"
    private const val KEY_DEMO_MODE = "demo_mode"

    data class Personalization(
        val name: String?,
        val pronouns: String?,
        val pronounsCustom: String?,
        val religion: String?
    )

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    suspend fun setName(context: Context, name: String) = withContext(Dispatchers.IO) {
        prefs(context).edit().putString(KEY_NAME, name).apply()
    }
    suspend fun setPronouns(context: Context, pronouns: String, custom: String? = null) = withContext(Dispatchers.IO) {
        prefs(context).edit().putString(KEY_PRONOUNS, pronouns).putString(KEY_PRONOUNS_CUSTOM, custom).apply()
    }
    suspend fun setReligion(context: Context, religion: String) = withContext(Dispatchers.IO) {
        prefs(context).edit().putString(KEY_RELIGION, religion).apply()
    }
    suspend fun getPersonalization(context: Context): Personalization = withContext(Dispatchers.IO) {
        val prefs = prefs(context)
        Personalization(
            name = prefs.getString(KEY_NAME, null),
            pronouns = prefs.getString(KEY_PRONOUNS, null),
            pronounsCustom = prefs.getString(KEY_PRONOUNS_CUSTOM, null),
            religion = prefs.getString(KEY_RELIGION, null)
        )
    }
    suspend fun setDemoMode(context: Context, enabled: Boolean) = withContext(Dispatchers.IO) {
        prefs(context).edit().putBoolean(KEY_DEMO_MODE, enabled).apply()
    }
    suspend fun getDemoMode(context: Context): Boolean = withContext(Dispatchers.IO) {
        prefs(context).getBoolean(KEY_DEMO_MODE, true)
    }
} 