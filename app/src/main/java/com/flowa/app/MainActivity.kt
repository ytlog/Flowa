package com.flowa.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flowa.app.presentation.AppViewModel
import com.flowa.app.ui.FlowaApp
import com.flowa.app.ui.FlowaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { FlowaTheme { FlowaApp(viewModel<AppViewModel>()) } }
    }
}
