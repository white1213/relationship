package com.relationship.graph

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.relationship.graph.ui.RelationshipApp
import com.relationship.graph.ui.theme.RelationshipTheme

class MainActivity : FragmentActivity() {
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                (application as RelationshipApplication).appLockController.lockNow()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ContextCompat.registerReceiver(
            this,
            screenOffReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        setContent {
            RelationshipTheme {
                RelationshipApp()
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

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenOffReceiver) }
        super.onDestroy()
    }
}
