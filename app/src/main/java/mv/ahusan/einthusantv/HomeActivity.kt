package mv.ahusan.einthusantv

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.GridLayout

class HomeActivity : Activity() {

    private val languages = listOf(
        "Tamil", "Hindi", "Telugu", "Malayalam",
        "Kannada", "Bengali", "Marathi", "Punjabi"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val grid = findViewById<GridLayout>(R.id.languageGrid)
        languages.forEachIndexed { i, lang ->
            val btn = layoutInflater.inflate(R.layout.item_language, grid, false) as Button
            btn.text = lang
            btn.setOnClickListener {
                open("https://einthusan.tv/launcher/?lang=${lang.lowercase()}")
            }
            grid.addView(btn)
            if (i == 0) btn.requestFocus()
        }

        findViewById<Button>(R.id.btnBrowse).setOnClickListener {
            open("https://einthusan.tv/")
        }
        findViewById<Button>(R.id.btnLogin).setOnClickListener {
            open("https://einthusan.tv/account/")
        }
    }

    private fun open(url: String) {
        startActivity(Intent(this, BrowserActivity::class.java).putExtra(BrowserActivity.EXTRA_URL, url))
    }
}
