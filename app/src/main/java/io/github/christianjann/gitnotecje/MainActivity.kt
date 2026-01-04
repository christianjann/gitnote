package io.github.christianjann.gitnotecje

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Locale
import dev.olshevski.navigation.reimagined.AnimatedNavHost
import dev.olshevski.navigation.reimagined.NavBackHandler
import dev.olshevski.navigation.reimagined.navigate
import dev.olshevski.navigation.reimagined.popAll
import dev.olshevski.navigation.reimagined.popUpTo
import dev.olshevski.navigation.reimagined.rememberNavController
import io.github.christianjann.gitnotecje.MyApp.Companion.appModule
import io.github.christianjann.gitnotecje.helper.NoteSaver
import io.github.christianjann.gitnotecje.data.Language
import io.github.christianjann.gitnotecje.ui.destination.AppDestination
import io.github.christianjann.gitnotecje.ui.destination.Destination
import io.github.christianjann.gitnotecje.ui.destination.EditParams
import io.github.christianjann.gitnotecje.ui.destination.SetupDestination
import io.github.christianjann.gitnotecje.ui.screen.app.AppScreen
import io.github.christianjann.gitnotecje.ui.screen.setup.SetupNav
import io.github.christianjann.gitnotecje.ui.theme.GitNoteTheme
import io.github.christianjann.gitnotecje.ui.theme.Theme
import io.github.christianjann.gitnotecje.ui.viewmodel.MainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun attachBaseContext(newBase: Context) {
        // Apply saved language preference to the base context
        val prefs = MyApp.appModule.appPreferences
        val savedLanguage = runBlocking { prefs.language.get() }
        
        val locale = when (savedLanguage) {
            Language.System -> null
            Language.English -> Locale.forLanguageTag("en")
            Language.Czech -> Locale.forLanguageTag("cs")
            Language.French -> Locale.forLanguageTag("fr")
            Language.PortugueseBrazilian -> Locale.forLanguageTag("pt-BR")
            Language.Russian -> Locale.forLanguageTag("ru-RU")
            Language.Ukrainian -> Locale.forLanguageTag("uk")
            Language.German -> Locale.forLanguageTag("de")
        }

        val context = if (locale != null) {
            val config = newBase.resources.configuration
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                config.setLocales(LocaleList(locale))
            } else {
                config.setLocale(locale)
            }
            newBase.createConfigurationContext(config)
        } else {
            newBase
        }
        
        super.attachBaseContext(context)
    }

    val authFlow: MutableSharedFlow<String> = MutableSharedFlow(replay = 0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")

        setContent {

            val vm: MainViewModel = viewModel()

            val theme by vm.prefs.theme.getAsState()
            val dynamicColor by vm.prefs.dynamicColor.getAsState()


            GitNoteTheme(
                darkTheme = (theme == Theme.SYSTEM && isSystemInDarkTheme()) || theme == Theme.DARK,
                dynamicColor = dynamicColor
            ) {

                val startDestination: Destination = remember {
                    if (runBlocking { vm.tryInit() }) {
                        if (NoteSaver.isEditUnsaved()) {
                            val saveInfo = NoteSaver.getSaveState()
                            if (saveInfo == null) {
                                Log.d(TAG, "can't retrieve the last saved note state")
                                Destination.App(AppDestination.Grid)
                            } else {
                                Log.d(TAG, "launch as EDIT_IS_UNSAVED")
                                Destination.App(
                                    AppDestination.Edit(
                                        EditParams.Saved(
                                            note = saveInfo.previousNote,
                                            editType = saveInfo.editType,
                                            name = saveInfo.name,
                                            content = saveInfo.content
                                        )
                                    )
                                )
                            }

                        } else Destination.App(AppDestination.Grid)
                    } else Destination.Setup(SetupDestination.Main)
                }

                if (startDestination is Destination.App) {
                    Log.i(TAG, "Starting sync from MainActivity")
                    vm.startSync()
                }

                LaunchedEffect(startDestination) {
                    if (startDestination is Destination.App) {
                        Log.i(TAG, "Starting sync from MainActivity LaunchedEffect")
                        // vm.startSync() // already called above
                    }
                }


                val navController =
                    rememberNavController(startDestination = startDestination)

                NavBackHandler(navController)

                AnimatedNavHost(
                    controller = navController
                ) { destination ->
                    when (destination) {
                        is Destination.Setup -> {
                            SetupNav(
                                startDestination = destination.setupDestination,
                                authFlow = authFlow,
                                onSetupSuccess = {
                                    navController.popUpTo(
                                        inclusive = true
                                    ) {
                                        it is Destination.Setup
                                    }
                                    navController.navigate(Destination.App(AppDestination.Grid))
                                }
                            )
                        }


                        is Destination.App -> AppScreen(
                            vm = vm,
                            appDestination = destination.appDestination,
                            onCloseRepo = {
                                navController.popAll()
                                navController.navigate(Destination.Setup(SetupDestination.Main))
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d(TAG, "onNewIntent $intent")

        val uri = intent.data ?: return
        if (uri.scheme == "gitnote-identity" && uri.host == "register-callback") {
            val code = uri.getQueryParameter("code")

            if (code != null) {
                Log.d(TAG, "received code from intent, sending it...")
                CoroutineScope(Dispatchers.Default).launch {
                    authFlow.emit(code)
                }
            } else {
                Log.w(TAG, "code is null")
            }
        }
    }

    fun changeLanguage(language: Language) {
        // Save the new language preference
        runBlocking {
            MyApp.appModule.appPreferences.language.update(language)
        }
        
        // Recreate activity to apply the new locale
        // attachBaseContext() will be called again and apply the new language
        recreate()
    }

    override fun onDestroy() {
        super.onDestroy()

        Log.d(TAG, "onDestroy")

        runBlocking {
            appModule.gitManager.shutdown()
        }
    }
}
