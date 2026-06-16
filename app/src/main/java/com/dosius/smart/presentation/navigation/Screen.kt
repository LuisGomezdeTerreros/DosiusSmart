package com.dosius.smart.presentation.navigation

sealed class Screen(val route: String, val navRoute: String = route) {
    data object Logs : Screen("logs")
    data object Dashboard : Screen("dashboard")
    data object Parameters : Screen("parameters")
    data object Data : Screen("data?tab={tab}", "data") {
        fun createRoute(tab: String = "FOOD") = "data?tab=$tab"
    }
    data object Settings : Screen("settings")
    data object Alarms : Screen("alarms")

    data object AddFood: Screen("addFood")
    data object AddExercise: Screen("addExercise")

    data object FoodDetail: Screen("foodDetail/{foodId}") {
        fun createRoute(foodId: String) =
            "foodDetail/$foodId"
    }

    data object ExerciseDetail: Screen("exerciseDetail/{type}") {
        fun createRoute(type: String) =
            "exerciseDetail/$type"
    }
}

