package com.example.calculator

import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import android.os.Looper
import java.util.Locale

class PlayerActivity : AppCompatActivity() {
    private lateinit var trackList: ListView
    private lateinit var playPauseBtn: Button
    private lateinit var rewindBtn: Button
    private lateinit var forwardBtn: Button
    private lateinit var prevBtn: Button
    private lateinit var nextBtn: Button
    private lateinit var seekBar: SeekBar
    private lateinit var currentTimeTV: TextView
    private lateinit var durationTimeTV: TextView

    private val trackNames = arrayOf("Track 1", "Track 2", "Track 3")
    private val trackRes = arrayOf(R.raw.track1, R.raw.track2, R.raw.track3)
    private var currentTrack = 0
    private var isPlaying = false
    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        trackList = findViewById(R.id.track_list)
        playPauseBtn = findViewById(R.id.btn_play_pause)
        rewindBtn = findViewById(R.id.btn_rewind)
        forwardBtn = findViewById(R.id.btn_forward)
        prevBtn = findViewById(R.id.btn_prev)
        nextBtn = findViewById(R.id.btn_next)
        seekBar = findViewById(R.id.seek_bar)
        currentTimeTV = findViewById(R.id.current_time)
        durationTimeTV = findViewById(R.id.duration_time)

        // Adapter for tracks list
        trackList.adapter = object : ArrayAdapter<String>(
            this,
            R.layout.list_item_track,
            R.id.track_name,
            trackNames
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                view.setBackgroundResource(R.drawable.rounded_track)
                return view
            }
        }

        trackList.setOnItemClickListener { _, _, pos, _ ->
            currentTrack = pos
            initPlayer(trackRes[currentTrack])
        }

        prevBtn.setOnClickListener { switchTrack(-1) }
        nextBtn.setOnClickListener { switchTrack(1) }
        rewindBtn.setOnClickListener { seekPlayer(-10000) }
        forwardBtn.setOnClickListener { seekPlayer(10000) }
        playPauseBtn.setOnClickListener { togglePlayPause() }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) player?.seekTo(progress)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        initPlayer(trackRes[currentTrack])
    }

    private fun initPlayer(resId: Int) {
        player?.release()
        player = MediaPlayer.create(this, resId)
        seekBar.max = player!!.duration
        durationTimeTV.text = getTimeStr(player!!.duration)
        player!!.setOnCompletionListener { switchTrack(1) }
        startUpdateSeekBar()
        showCurrentTime()
        isPlaying = false
        updatePlayPauseIcon()
    }

    private fun switchTrack(step: Int) {
        currentTrack = (currentTrack + step + trackRes.size) % trackRes.size
        initPlayer(trackRes[currentTrack])
        player?.start()
        isPlaying = true
        updatePlayPauseIcon()
    }

    private fun seekPlayer(delta: Int) {
        val pos = (player?.currentPosition ?: 0) + delta
        player?.seekTo(pos.coerceIn(0, player!!.duration))
        showCurrentTime()
    }

    private fun togglePlayPause() {
        player?.let {
            if (isPlaying) {
                it.pause()
            } else {
                it.start()
                startUpdateSeekBar()
            }
            isPlaying = !isPlaying
            updatePlayPauseIcon()
        }
    }

    private fun updatePlayPauseIcon() {
        playPauseBtn.text = if (isPlaying) "⏸" else "▶"
    }

    private fun startUpdateSeekBar() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                player?.let {
                    seekBar.progress = it.currentPosition
                    showCurrentTime()
                    if (isPlaying) handler.postDelayed(this, 500)
                }
            }
        }, 500)
    }

    private fun showCurrentTime() {
        currentTimeTV.text = getTimeStr(player?.currentPosition ?: 0)
    }

    private fun getTimeStr(ms: Int): String {
        val seconds = ms / 1000 % 60
        val minutes = ms / 1000 / 60
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
        isPlaying = false
        updatePlayPauseIcon()
    }
    override fun onDestroy() {
        super.onDestroy()
        player?.release()
    }
}
