package com.example.backgroundtimelapse

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        
        val startBtn = Button(this).apply {
            text = "Start 24hr Recording"
            setOnClickListener {
                val intent = Intent(this@MainActivity, TimeLapseService::class.java)
                ContextCompat.startForegroundService(this@MainActivity, intent)
            }
        }
        
        val stopBtn = Button(this).apply {
            text = "Stop & Create Video"
            setOnClickListener {
                stopService(Intent(this@MainActivity, TimeLapseService::class.java))
            }
        }
        
        layout.addView(startBtn)
        layout.addView(stopBtn)
        setContentView(layout)
    }
}
