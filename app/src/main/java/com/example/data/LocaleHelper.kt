package com.example.data

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Lightweight locale wrapper so SLauncher 6 can switch immediately between
 * System Default, Bahasa Indonesia, and English without heavy activity restarts.
 */
object LocaleHelper {
  fun localizedContext(baseContext: Context, language: AppLanguage): Context {
    if (language == AppLanguage.SYSTEM) return baseContext
    val targetLocale =
      when (language) {
        AppLanguage.INDONESIAN -> Locale("in", "ID")
        AppLanguage.ENGLISH -> Locale.ENGLISH
        AppLanguage.SYSTEM -> return baseContext
      }
    val config = Configuration(baseContext.resources.configuration)
    config.setLocale(targetLocale)
    return baseContext.createConfigurationContext(config)
  }
}
