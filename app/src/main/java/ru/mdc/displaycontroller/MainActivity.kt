package ru.mdc.displaycontroller

import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import ru.mdc.displaycontroller.safety.ProductCapabilities

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }

        root.addView(TextView(this).apply {
            text = "Mazda Display Controller 1.0.1"
            textSize = 28f
            gravity = Gravity.CENTER
        })

        root.addView(TextView(this).apply {
            text = "Internal bootstrap\n${ProductCapabilities.SAFETY_PROFILE}\nCAN WRITE: OFF"
            textSize = 18f
            gravity = Gravity.CENTER
        })

        setContentView(root)
    }
}
