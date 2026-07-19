package dev.digitalducktape.openride.ui.main

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.digitalducktape.openride.AppContainer
import dev.digitalducktape.openride.viewModelFactory
import dev.digitalducktape.openride.ui.classes.ClassesScreen
import dev.digitalducktape.openride.ui.history.HistoryScreen
import dev.digitalducktape.openride.ui.home.HomeScreen
import dev.digitalducktape.openride.ui.home.HomeViewModel
import dev.digitalducktape.openride.ui.navigation.MainTabs
import dev.digitalducktape.openride.ui.profile.ProfileTabScreen
import dev.digitalducktape.openride.ui.profile.ProfileTabViewModel

private data class TabSpec(val route: String, val label: String, val emoji: String)

private val TABS = listOf(
    TabSpec(MainTabs.Home, "Home", "🏠"),
    TabSpec(MainTabs.Classes, "Classes", "🎬"),
    TabSpec(MainTabs.History, "History", "📜"),
    TabSpec(MainTabs.Profile, "Profile", "👤"),
)

/**
 * The tabbed section of the app: a landscape-friendly side [NavigationRail] (Home / Classes /
 * History / Profile, PRD P0-7) plus a nested `NavHost` for those four destinations. Starting
 * a ride or switching riders navigates the *outer* nav controller (passed in), which lives
 * one level up in [dev.digitalducktape.openride.ui.navigation.OpenRideNavHost] — those flows
 * intentionally leave this tabbed chrome behind rather than becoming a fifth tab.
 */
@Composable
fun MainScaffold(
    appContainer: AppContainer,
    outerNavController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val innerNavController = rememberNavController()
    val backStackEntry by innerNavController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Row(modifier = modifier.fillMaxSize()) {
        NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
            TABS.forEach { tab ->
                NavigationRailItem(
                    selected = currentRoute == tab.route,
                    onClick = {
                        innerNavController.navigate(tab.route) {
                            popUpTo(innerNavController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Text(text = tab.emoji) },
                    label = { Text(text = tab.label) },
                    colors = androidx.compose.material3.NavigationRailItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                )
            }
        }

        NavHost(
            navController = innerNavController,
            startDestination = MainTabs.Home,
            modifier = Modifier.weight(1f),
        ) {
            composable(MainTabs.Home) {
                val viewModel: HomeViewModel = viewModel(
                    factory = viewModelFactory {
                        HomeViewModel(
                            appContainer.activeProfileHolder,
                            appContainer.profileRepository,
                            appContainer.rideSessionManager,
                        )
                    },
                )
                HomeScreen(
                    viewModel = viewModel,
                    onQuickStart = {
                        outerNavController.navigate(dev.digitalducktape.openride.ui.navigation.Destinations.InRide)
                    },
                )
            }
            composable(MainTabs.Classes) {
                ClassesScreen()
            }
            composable(MainTabs.History) {
                HistoryScreen()
            }
            composable(MainTabs.Profile) {
                val viewModel: ProfileTabViewModel = viewModel(
                    factory = viewModelFactory {
                        ProfileTabViewModel(appContainer.activeProfileHolder, appContainer.profileRepository)
                    },
                )
                ProfileTabScreen(viewModel = viewModel)
            }
        }
    }
}
