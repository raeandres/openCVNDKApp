package com.raeanandres.opencvndkapp

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import java.io.IOException
import androidx.core.graphics.createBitmap

class MainActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private var bitmap: Bitmap? = null
    private var inputMat: Mat? = null
    private var resultMat: Mat? = null

    companion object {
        init {
            System.loadLibrary("native-opencv")
        }
    }

    external fun convertToGray(matAddrInput: Long, matAddrResult: Long)
    external fun applyBlur(matAddrInput: Long, matAddrResult: Long)
    external fun detectEdges(matAddrInput: Long, matAddrResult: Long)

    // Modern way to handle activity results
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { handleImageSelection(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        imageView = findViewById(R.id.imageView)
        
        // Initialize OpenCV
        if (!OpenCVLoader.initLocal()) {
            Toast.makeText(this, "OpenCV initialization failed", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "OpenCV initialization succeeded", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.buttonLoad).setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        setFilterListeners()
    }

    private fun setFilterListeners() {
        findViewById<Button>(R.id.buttonGray).setOnClickListener { applyFilter(::convertToGray) }
        findViewById<Button>(R.id.buttonBlur).setOnClickListener { applyFilter(::applyBlur) }
        findViewById<Button>(R.id.buttonEdges).setOnClickListener { applyFilter(::detectEdges) }
    }

    private fun applyFilter(filter: (Long, Long) -> Unit) {
        if (inputMat == null) {
            Toast.makeText(this, "Please load an image first", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            if (resultMat == null) {
                resultMat = Mat()
            }

            filter(inputMat!!.nativeObjAddr, resultMat!!.nativeObjAddr)

            val resultBmp = createBitmap(resultMat!!.width(), resultMat!!.height())
            Utils.matToBitmap(resultMat!!, resultBmp)
            imageView.setImageBitmap(resultBmp)
        } catch (e: Exception) {
            Toast.makeText(this, "Error applying filter: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun handleImageSelection(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            
            bitmap?.let { bmp ->
                imageView.setImageBitmap(bmp)

                // Convert Bitmap to Mat
                inputMat = Mat()
                val bmp32 = bmp.copy(Bitmap.Config.ARGB_8888, true)
                Utils.bitmapToMat(bmp32, inputMat)
                
                Toast.makeText(this, "Image loaded successfully", Toast.LENGTH_SHORT).show()
            }
        } catch (e: IOException) {
            Toast.makeText(this, "Error loading image: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up Mat objects to prevent memory leaks
        inputMat?.release()
        resultMat?.release()
    }
}