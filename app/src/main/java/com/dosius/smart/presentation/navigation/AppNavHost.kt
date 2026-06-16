package com.dosius.smart.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.dosius.smart.presentation.dashboard.DashboardScreen
import com.dosius.smart.presentation.database.AddExerciseScreen
import com.dosius.smart.presentation.database.AddFoodScreen
import com.dosius.smart.presentation.database.DatabaseScreen
import com.dosius.smart.presentation.database.DatabaseTab
import com.dosius.smart.presentation.database.ExerciseDetailScreen
import com.dosius.smart.presentation.database.FoodDetailScreen
import com.dosius.smart.presentation.alarm.AlarmScreen
import com.dosius.smart.presentation.logs.LogsScreen
import com.dosius.smart.presentation.parameters.TherapyParametersScreen
import com.dosius.smart.presentation.settings.SettingsScreen

@Composable
fun AppNavHost(navController: NavHostController) {
    NavHost(
        navController = navController, startDestination = Screen.Dashboard.route
    ) {
        composable(Screen.Dashboard.route) {
            DashboardScreen()
        }
        composable(Screen.Settings.route) {
            SettingsScreen()
        }
        composable(Screen.Alarms.route) {
            AlarmScreen()
        }
        composable(Screen.Logs.route) {
            LogsScreen()
        }
        composable(Screen.Parameters.route) {
            TherapyParametersScreen()
        }
        composable(
            route = Screen.Data.route,
            arguments = listOf(navArgument("tab") { defaultValue = "FOOD" })
        ) { backStackEntry ->
            val tab = backStackEntry.arguments?.getString("tab") ?: "FOOD"
            DatabaseScreen(
                initialTab = if (tab == "EXERCISE") DatabaseTab.EXERCISE else DatabaseTab.FOOD,
                onNavigateToAddFood = { navController.navigate(Screen.AddFood.route) },
                onNavigateToAddExercise = { navController.navigate(Screen.AddExercise.route) },
                onNavigateToFoodDetail = { foodId -> navController.navigate(Screen.FoodDetail.createRoute(foodId)) },
                onNavigateToExerciseDetail = { type -> navController.navigate(Screen.ExerciseDetail.createRoute(type)) }
            )
        }
        composable(Screen.AddFood.route) {
            AddFoodScreen(onNavigateBack = {
                navController.navigate(Screen.Data.createRoute("FOOD")) {
                    popUpTo(Screen.Data.route) { inclusive = true }
                }
            })
        }
        composable(Screen.AddExercise.route) {
            AddExerciseScreen(onNavigateBack = {
                navController.navigate(Screen.Data.createRoute("EXERCISE")) {
                    popUpTo(Screen.Data.route) { inclusive = true }
                }
            })
        }
        composable(
            route = Screen.FoodDetail.route,
            arguments = listOf(navArgument("foodId") { type = NavType.StringType })
        ) {
            FoodDetailScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(
            route = Screen.ExerciseDetail.route,
            arguments = listOf(navArgument("type") { type = NavType.StringType })
        ) {
            ExerciseDetailScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String) {
    Box(
        modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$title — coming soon",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}
