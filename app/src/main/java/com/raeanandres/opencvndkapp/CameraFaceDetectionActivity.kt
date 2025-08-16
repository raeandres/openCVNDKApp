package com.raeanandres.opencvndkapp

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File

class CameraFaceDetectionActivity : AppCompatActivity() {

    companion object {
        init {
            System.loadLibrary("native-opencv")
        }



    }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_camera_face_detection)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

//        // Copy model to internal storage (so C++ can read it)
//        val modelFile = File(filesDir, "haarcascade_frontalface_alt.xml")
//        if (!modelFile.exists()) {
//            assets.open("haarcascade_frontalface_alt.xml").use { input ->
//                modelFile.outputStream().use { output ->
//                    input.copyTo(output)
//                }
//            }
//        }

//        // Load into C++
//        loadFaceCascade(modelFile.absolutePath)
    }
}