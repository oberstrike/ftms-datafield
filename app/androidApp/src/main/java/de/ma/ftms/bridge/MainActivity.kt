package de.ma.ftms.bridge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.arkivanov.decompose.defaultComponentContext
import de.ma.ftms.bridge.navigation.DefaultRootComponent
import de.ma.ftms.bridge.navigation.RootComponent
import de.ma.ftms.bridge.runtime.BridgeRuntime
import de.ma.ftms.bridge.ui.FtmsBridgeApp

class MainActivity : ComponentActivity() {
    private lateinit var rootComponent: RootComponent

    override fun onCreate(savedInstanceState: Bundle?) {
        // Draw behind the system bars on every Android version, as Android 15+ enforces anyway.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        BridgeRuntime.initialize(this)
        rootComponent = DefaultRootComponent(defaultComponentContext())

        setContent {
            FtmsBridgeApp(rootComponent = rootComponent)
        }
    }

    override fun onStart() {
        super.onStart()
        BridgeRuntime.onAppVisibilityChanged(true)
    }

    override fun onStop() {
        super.onStop()
        // A rotation or theme change restarts the activity at once; the app stays visible.
        if (!isChangingConfigurations) {
            BridgeRuntime.onAppVisibilityChanged(false)
        }
    }

    override fun onResume() {
        super.onResume()
        BridgeRuntime.onAppResumed()
    }
}
