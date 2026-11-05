package com.jskim.sharingfuelcost

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jskim.sharingfuelcost.presentation.fuelcost.FuelCostRoute
import com.jskim.sharingfuelcost.presentation.theme.SharingFuelCostTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SharingFuelCostTheme {
                FuelCostRoute()
            }
        }
    }
}
