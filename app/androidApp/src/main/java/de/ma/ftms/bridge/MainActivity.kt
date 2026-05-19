package de.ma.ftms.bridge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.arkivanov.decompose.defaultComponentContext
import de.ma.ftms.bridge.navigation.DefaultRootComponent
import de.ma.ftms.bridge.navigation.RootComponent
import de.ma.ftms.bridge.runtime.BridgeRuntime
import de.ma.ftms.bridge.ui.FtmsBridgeApp

class MainActivity : ComponentActivity() {
    private lateinit var rootComponent: RootComponent

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BridgeRuntime.initialize(this)
        rootComponent = DefaultRootComponent(defaultComponentContext())

        setContent {
            FtmsBridgeApp(rootComponent = rootComponent)
        }
    }

    override fun onResume() {
        super.onResume()
        BridgeRuntime.refreshPermissionState()
    }
}
