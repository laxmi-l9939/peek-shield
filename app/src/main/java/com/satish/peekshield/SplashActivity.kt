package com.satish.peekshield

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import androidx.appcompat.app.AppCompatActivity

/**
 * Shows the "Made with ❤️ by The Satish" splash for 2 seconds, then fades out
 * and launches MainActivity.
 */
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val root = findViewById<android.view.View>(R.id.splashRoot)

        Handler(Looper.getMainLooper()).postDelayed({
            val fade = AlphaAnimation(1f, 0f).apply {
                duration = 400
                setAnimationListener(object : Animation.AnimationListener {
                    override fun onAnimationStart(animation: Animation) {}
                    override fun onAnimationRepeat(animation: Animation) {}
                    override fun onAnimationEnd(animation: Animation) {
                        startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                        finish()
                    }
                })
            }
            root.startAnimation(fade)
        }, 2000)
    }
}
