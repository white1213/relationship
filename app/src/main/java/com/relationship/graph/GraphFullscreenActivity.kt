package com.relationship.graph

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import com.relationship.graph.data.local.GraphMode
import com.relationship.graph.ui.RelationshipApp
import com.relationship.graph.ui.theme.RelationshipTheme

class GraphFullscreenActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        val initialMode = intent.getStringExtra(EXTRA_GRAPH_MODE)
            ?.let { runCatching { GraphMode.valueOf(it) }.getOrNull() }
            ?: GraphMode.FAMILY
        setContent {
            RelationshipTheme {
                RelationshipApp(
                    fullscreenGraph = true,
                    initialGraphMode = initialMode,
                    onExitFullscreen = ::finish,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        (application as RelationshipApplication).appLockController.onForeground()
    }

    override fun onStop() {
        (application as RelationshipApplication).appLockController.onBackground()
        super.onStop()
    }

    companion object {
        const val EXTRA_GRAPH_MODE = "graph_mode"
    }
}
