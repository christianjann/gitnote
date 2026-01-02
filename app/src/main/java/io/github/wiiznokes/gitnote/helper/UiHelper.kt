package io.github.wiiznokes.gitnote.helper

import android.content.Context
import android.os.Build
import android.os.LocaleList
import android.widget.Toast
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import io.github.wiiznokes.gitnote.MyApp
import io.github.wiiznokes.gitnote.data.Language
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.Locale

class UiHelper(
    private val context: Context
) {
    private fun getLocalizedContext(): Context {
        val prefs = MyApp.appModule.appPreferences
        val savedLanguage = runBlocking { prefs.language.get() }
        
        val locale = when (savedLanguage) {
            Language.System -> return context
            Language.English -> Locale.forLanguageTag("en")
            Language.Czech -> Locale.forLanguageTag("cs")
            Language.French -> Locale.forLanguageTag("fr")
            Language.PortugueseBrazilian -> Locale.forLanguageTag("pt-BR")
            Language.Russian -> Locale.forLanguageTag("ru-RU")
            Language.Ukrainian -> Locale.forLanguageTag("uk")
            Language.German -> Locale.forLanguageTag("de")
        }

        val config = context.resources.configuration
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(locale))
        } else {
            config.setLocale(locale)
        }
        return context.createConfigurationContext(config)
    }

    fun makeToast(text: String?, duration: Int = Toast.LENGTH_SHORT) {
        if (text == null) return
        CoroutineScope(Dispatchers.Main).launch {
            Toast.makeText(context, text, duration).show()
        }
    }

    fun getString(@StringRes resId: Int): String =
        getLocalizedContext().getString(resId)

    fun getString(@StringRes resId: Int, vararg formatArgs: Any?): String {
        return getLocalizedContext().getString(resId, *formatArgs)
    }

    fun getQuantityString(@PluralsRes resId: Int, quantity: Int, vararg formatArgs: Any?): String {
        return getLocalizedContext().resources.getQuantityString(resId, quantity, formatArgs)
    }
}