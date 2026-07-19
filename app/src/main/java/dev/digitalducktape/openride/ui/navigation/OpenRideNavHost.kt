package dev.digitalducktape.openride.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.digitalducktape.openride.AppContainer
import dev.digitalducktape.openride.viewModelFactory
import dev.digitalducktape.openride.ui.main.MainScaffold
import dev.digitalducktape.openride.ui.profile.ProfileCreateScreen
import dev.digitalducktape.openride.ui.profile.ProfileCreateViewModel
import dev.digitalducktape.openride.ui.profile.ProfileSelectScreen
import dev.digitalducktape.openride.ui.profile.ProfileSelectViewModel
import dev.digitalducktape.openride.ui.ride.InRideScreen
import dev.digitalducktape.openride.ui.ride.InRideViewModel

/**
 * The app's single outer [androidx.navigation.NavHost] (T11). Start destination is profile
 * select (PRD P0-8: fresh launch always lands there). [Destinations.Main] hosts its own
 * nested tab graph (see [MainScaffold]) so starting/ending a ride and switching riders can
 * push/pop that whole tabbed section as one unit.
 */
@Composable
fun OpenRideNavHost(appContainer: AppContainer) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Destinations.ProfileSelect) {
        composable(Destinations.ProfileSelect) {
            val viewModel: ProfileSelectViewModel = viewModel(
                factory = viewModelFactory {
                    ProfileSelectViewModel(appContainer.profileRepository, appContainer.activeProfileHolder)
                },
            )
            ProfileSelectScreen(
                viewModel = viewModel,
                onProfileSelected = {
                    navController.navigate(Destinations.Main) {
                        popUpTo(Destinations.ProfileSelect) { inclusive = true }
                    }
                },
                onAddRider = { navController.navigate(Destinations.ProfileCreate) },
            )
        }

        composable(Destinations.ProfileCreate) {
            val viewModel: ProfileCreateViewModel = viewModel(
                factory = viewModelFactory {
                    ProfileCreateViewModel(appContainer.profileRepository, appContainer.activeProfileHolder)
                },
            )
            ProfileCreateScreen(
                viewModel = viewModel,
                onSaved = {
                    navController.navigate(Destinations.Main) {
                        popUpTo(Destinations.ProfileSelect) { inclusive = true }
                    }
                },
                onCancel = { navController.popBackStack() },
            )
        }

        composable(Destinations.Main) {
            MainScaffold(appContainer = appContainer, outerNavController = navController)
        }

        composable(Destinations.InRide) {
            val viewModel: InRideViewModel = viewModel(
                factory = viewModelFactory { InRideViewModel(appContainer.rideSessionManager) },
            )
            InRideScreen(
                viewModel = viewModel,
                onRideEnded = { navController.popBackStack() },
            )
        }
    }
}
