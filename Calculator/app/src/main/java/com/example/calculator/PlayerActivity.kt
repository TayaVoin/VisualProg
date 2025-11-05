package com.example.calculator

import android.graphics.Color
import android.graphics.PorterDuff
import android.media.MediaPlayer
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import com.devadvance.circularseekbar.CircularSeekBar
import androidx.appcompat.app.AppCompatActivity
import kotlin.random.Random

class PlayerActivity : AppCompatActivity() {
    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var vinylImage: ImageView
    private lateinit var trackTimePassed: TextView
    private lateinit var trackTimeTotal: TextView
    private lateinit var seekBar: CircularSeekBar

    private val vinylColors = listOf("#D72638", "#0CA4A5", "#FFD600", "#2E294E", "#FFA700")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        vinylImage = findViewById(R.id.vinylImage)
        trackTimePassed = findViewById(R.id.trackTimePassed)
        trackTimeTotal = findViewById(R.id.trackTimeTotal)
        seekBar = findViewById(R.id.seekBar)

        val color = Color.parseColor(vinylColors[Random.nextInt(vinylColors.size)])
        vinylImage.setColorFilter(color, PorterDuff.Mode.SRC_ATOP)

        mediaPlayer = MediaPlayer.create(this, R.raw.track1)
        trackTimeTotal.text = getTimeString(mediaPlayer.duration)

        seekBar.max = mediaPlayer.duration

        seekBar.setOnSeekBarChangeListener(object : CircularSeekBar.OnCircularSeekBarChangeListener {
            override fun onProgressChanged(seekBar: CircularSeekBar, progress: Int, fromUser: Boolean) {
                trackTimePassed.text = getTimeString(progress)
            }
            override fun onStopTrackingTouch(seekBar: CircularSeekBar) {
                mediaPlayer.seekTo(seekBar.progress)
            }
            override fun onStartTrackingTouch(seekBar: CircularSeekBar) {}
        })
    }

    private fun getTimeString(ms: Int): String {
        val seconds = ms / 1000 % 60
        val minutes = ms / 1000 / 60
        return String.format("%d:%02d", minutes, seconds)
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer.release()
    }
}
