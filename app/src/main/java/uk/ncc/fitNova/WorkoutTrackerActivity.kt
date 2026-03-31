package uk.ncc.fitNova

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class WorkoutTrackerActivity : AppCompatActivity() {
    private lateinit var tvWorkoutTitle: TextView
    private lateinit var tvTimer: TextView
    private lateinit var btnToggleWorkout: Button
    private lateinit var btnFinishWorkout: Button

    private var isRunning = false
    private var secondsElapsed = 0
    private val handler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (isRunning) {
                secondsElapsed++
                val minutes = secondsElapsed / 60
                val seconds = secondsElapsed % 60
                tvTimer.text = String.format("%02d:%02d", minutes, seconds)
                handler.postDelayed(this, 1000)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_workout_tracker)

        tvWorkoutTitle = findViewById(R.id.tvWorkoutTitle)
        tvTimer = findViewById(R.id.tvTimer)
        btnToggleWorkout = findViewById(R.id.btnToggleWorkout)
        btnFinishWorkout = findViewById(R.id.btnFinishWorkout)

        val workoutType = intent.getStringExtra("WORKOUT_TYPE") ?: "Workout"
        tvWorkoutTitle.text = workoutType

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        btnToggleWorkout.setOnClickListener {
            if (isRunning) {
                pauseWorkout()
            } else {
                startWorkout()
            }
        }

        btnFinishWorkout.setOnClickListener {
            finish()
        }
    }

    private fun startWorkout() {
        isRunning = true
        btnToggleWorkout.text = "Pause"
        btnToggleWorkout.setBackgroundColor(resources.getColor(R.color.fitness_text_secondary, theme))
        handler.post(timerRunnable)
    }

    private fun pauseWorkout() {
        isRunning = false
        btnToggleWorkout.text = "Resume"
        btnToggleWorkout.setBackgroundColor(resources.getColor(R.color.auth_button_blue, theme))
        handler.removeCallbacks(timerRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(timerRunnable)
    }
}
