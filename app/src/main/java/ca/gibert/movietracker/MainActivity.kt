package ca.gibert.movietracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ca.gibert.movietracker.ui.MovieTrackerRoot
import ca.gibert.movietracker.ui.theme.MovieTrackerTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as MovieTrackerApp).container
        setContent {
            MovieTrackerTheme {
                MovieTrackerRoot(container)
            }
        }
    }
}
