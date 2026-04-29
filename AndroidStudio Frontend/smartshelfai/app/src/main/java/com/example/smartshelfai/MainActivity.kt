package com.example.smartshelfai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val navController = rememberNavController()

            NavHost(
                navController = navController,
                startDestination = "welcome"
            ) {
                composable("welcome") {
                    WelcomeScreen(
                        onNavigateNext = {
                            navController.navigate("predict") {
                                popUpTo("welcome") { inclusive = true }
                            }
                        }
                    )
                }

                composable("predict") {
                    PredictScreen(
                        onNavigateToResults    = { store, family, days, promo ->
                            navController.navigate("results/$store/$family/$days/$promo")
                        },
                        onNavigateToHistorical = { store, family, days, promo ->
                            navController.navigate("historical/$store/$family/$days/$promo")
                        }
                    )
                }

                composable("results/{store}/{family}/{days}/{promo}") { back ->
                    val store  = back.arguments?.getString("store")?.toIntOrNull() ?: 1
                    val family = back.arguments?.getString("family") ?: "BEVERAGES"
                    val days   = back.arguments?.getString("days")?.toIntOrNull() ?: 7
                    val promo  = back.arguments?.getString("promo")?.toIntOrNull() ?: 0
                    ResultsScreen(
                        storeNbr    = store,
                        family      = family,
                        days        = days,
                        onPromotion = promo,
                        onBack      = { navController.popBackStack() }
                    )
                }

                composable("historical/{store}/{family}/{days}/{promo}") { back ->
                    val store  = back.arguments?.getString("store")?.toIntOrNull() ?: 1
                    val family = back.arguments?.getString("family") ?: "BEVERAGES"
                    val days   = back.arguments?.getString("days")?.toIntOrNull() ?: 7
                    val promo  = back.arguments?.getString("promo")?.toIntOrNull() ?: 0
                    HistoricalScreen(
                        storeNbr    = store,
                        family      = family,
                        days        = days,
                        onPromotion = promo,
                        onBack      = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}