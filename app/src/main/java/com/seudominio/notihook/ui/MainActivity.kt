package com.seudominio.notihook.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.seudominio.notihook.ui.theme.NotiHookTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NotiHookTheme {
                Surface {
                    NotiHookNav()
                }
            }
        }
    }
}

private object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val DETAIL = "detail/{eventId}"
    fun detail(id: Long) = "detail/$id"
}

@Composable
private fun NotiHookNav() {
    val nav = rememberNavController()
    val vm: MainViewModel = viewModel(factory = MainViewModel.Factory)

    // A permissão pode mudar fora do app, então reavalia ao voltar para a tela.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refreshAccess()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                vm = vm,
                onOpenEvent = { id -> nav.navigate(Routes.detail(id)) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(vm = vm, onBack = { nav.popBackStack() })
        }
        composable(
            Routes.DETAIL,
            arguments = listOf(navArgument("eventId") { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong("eventId") ?: 0L
            EventDetailScreen(
                vm = vm,
                eventId = id,
                onBack = { nav.popBackStack() },
                onNavigateToEvent = { target ->
                    // Substitui o destino atual para não empilhar o histórico.
                    nav.navigate(Routes.detail(target)) {
                        popUpTo(Routes.DETAIL) { inclusive = true }
                    }
                },
            )
        }
    }
}
