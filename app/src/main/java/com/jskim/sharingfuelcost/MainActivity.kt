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
    // registerForActivityResult는 STARTED 전에 등록해야 해서 필드에서 만든다.
    private val appUpdate = ImmediateAppUpdate(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SharingFuelCostTheme {
                FuelCostRoute()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        appUpdate.check()
    }
}
