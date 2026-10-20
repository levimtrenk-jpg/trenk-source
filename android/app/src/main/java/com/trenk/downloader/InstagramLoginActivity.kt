package com.trenk.downloader

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * התחברות לאינסטגרם בתוך WebView. אחרי שמופיעה עוגיית sessionid, העוגיות נשמרות
 * לקובץ בפורמט Netscape (cookies.txt) שמועבר ל-yt-dlp עם --cookies.
 */
class InstagramLoginActivity : AppCompatActivity() {
    private var saved = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val web = WebView(this)
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                if (!saved && InstagramCookies.save(this@InstagramLoginActivity)) {
                    saved = true
                    Toast.makeText(this@InstagramLoginActivity, getString(R.string.instagram_connected), Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
        setContentView(web)
        web.loadUrl("https://www.instagram.com/accounts/login/")
    }
}

object InstagramCookies {
    private const val DOMAIN = "https://www.instagram.com"

    fun isLoggedIn(context: Context) = Engine.cookiesFile(context).exists()

    /** כותב cookies.txt אם יש התחברות (sessionid). מחזיר true אם נשמר. */
    fun save(context: Context): Boolean {
        val cm = CookieManager.getInstance()
        cm.flush()
        val raw = cm.getCookie(DOMAIN) ?: return false
        if (!raw.contains("sessionid=")) return false
        // CookieManager לא מחזיר תפוגה/דומיין, אז נותנים שנה קדימה לכל אחת
        val expiry = System.currentTimeMillis() / 1000 + 365L * 24 * 3600
        val lines = raw.split(";").mapNotNull { part ->
            val kv = part.trim()
            val eq = kv.indexOf('=')
            if (eq <= 0) null else ".instagram.com\tTRUE\t/\tTRUE\t$expiry\t${kv.substring(0, eq)}\t${kv.substring(eq + 1)}"
        }
        Engine.cookiesFile(context).writeText("# Netscape HTTP Cookie File\n" + lines.joinToString("\n") + "\n")
        return true
    }

    fun logout(context: Context) {
        Engine.cookiesFile(context).delete()
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
    }
}
