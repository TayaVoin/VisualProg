package com.example.calculator

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class TrackListActivity : AppCompatActivity() {
    private val trackList = listOf(
        Track("Братец Леший", "Скетчбук Элазара", "track1"),
        Track("Кот-Баюн", "Скетчбук Элазара", "track2"),
        Track("Слава роду", "Helvegen", "track3"),
        Track("Она не твоя", "Григорий Лепс", "track4")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_track_list)

        val trackContainer = findViewById<LinearLayout>(R.id.trackContainer)
        trackList.forEach { track ->
            val view = layoutInflater.inflate(R.layout.item_track, trackContainer, false)
            view.findViewById<TextView>(R.id.trackTitle).text = track.title
            view.findViewById<TextView>(R.id.trackAuthor).text = track.author

            view.setOnClickListener {
                val intent = Intent(this, PlayerActivity::class.java)
                intent.putExtra("TRACK_RES_NAME", track.resourceName)
                intent.putExtra("TRACK_TITLE", track.title)
                intent.putExtra("TRACK_AUTHOR", track.author)
                startActivity(intent)
            }
            trackContainer.addView(view)
        }
    }
}

data class Track(val title: String, val author: String, val resourceName: String)
