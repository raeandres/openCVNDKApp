package com.raeanandres.opencvndkapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.SessionConfiguration
import android.hardware.camera2.params.OutputConfiguration
import java.util.concurrent.Executors
import android.media.Image
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.LruCache
import android.view.Surface
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import androidx.core.graphics.createBitmap
import java.nio.ByteBuffer

class CameraActivity : AppCompatActivity() {
    private lateinit var cameraManager: CameraManager
    private var cameraId: String? = null
    private var imageReader: ImageReader? = null
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var currentFilter = FilterMode.NORMAL
    private var currentRotation = 0 // 0, 90, 180, 270 degrees
    private var isUsingFrontCamera = false

    private val handler = Handler(Looper.getMainLooper())
    private val bitmapPool = LruCache<String, Bitmap>(5)
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    companion object {
        init {
            System.loadLibrary("native-opencv")
        }
    }

    external fun loadFaceCascade(modelPath: String)
    external fun detectFaces(matAddr: Long): LongArray

    // Native method declarations
    external fun convertToGray(matAddrInput: Long, matAddrResult: Long)
    external fun applyBlur(matAddrInput: Long, matAddrResult: Long)
    external fun detectEdges(matAddrInput: Long, matAddrResult: Long)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_camera)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize OpenCV
        if (!OpenCVLoader.initLocal()) {
            Toast.makeText(this, "OpenCV initialization failed", Toast.LENGTH_SHORT).show()
            return
        }

        startBackgroundThread()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            setupCamera()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 100)
        }

        findViewById<Button>(R.id.btnNormal).setOnClickListener { currentFilter = FilterMode.NORMAL }
        findViewById<Button>(R.id.btnGray).setOnClickListener { currentFilter = FilterMode.GRAY }
        findViewById<Button>(R.id.btnBlur).setOnClickListener { currentFilter = FilterMode.BLUR }
        findViewById<Button>(R.id.btnEdges).setOnClickListener { currentFilter = FilterMode.EDGES }
        
        // Camera control buttons
        findViewById<Button>(R.id.btnRotate).setOnClickListener { rotateCamera() }
        findViewById<Button>(R.id.btnSwitchCamera).setOnClickListener { switchCamera() }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            setupCamera()
        } else {
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupCamera() {
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        try {
            // Find the appropriate camera (front or back)
            cameraId = findCamera(isUsingFrontCamera)
            
            if (cameraId == null) {
                Toast.makeText(this, "No suitable camera found", Toast.LENGTH_SHORT).show()
                return
            }

            val characteristics = cameraManager.getCameraCharacteristics(cameraId!!)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val previewSize = map?.getOutputSizes(Surface::class.java)?.get(0)

            // Use YUV_420_888 format which is supported by Camera2 API
            imageReader = ImageReader.newInstance(640, 480, ImageFormat.YUV_420_888, 2)
            imageReader?.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage()
                if (image != null) {
                    // Process image in background thread
                    processImage(image)
                    image.close()
                }
            }, backgroundHandler)

            openCamera()
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun findCamera(useFrontCamera: Boolean): String? {
        try {
            for (cameraId in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                
                if (useFrontCamera && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    return cameraId
                } else if (!useFrontCamera && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    return cameraId
                }
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
        return null
    }

    private fun openCamera() {
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return
            }
            cameraManager.openCamera(cameraId!!, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) {
                    cameraDevice = device
                    createCameraPreview()
                }

                override fun onDisconnected(device: CameraDevice) {
                    device.close()
                    cameraDevice = null
                }

                override fun onError(device: CameraDevice, error: Int) {
                    device.close()
                    cameraDevice = null
                }
            }, backgroundHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun createCameraPreview() {
        try {
            val surface = imageReader?.surface
            val previewRequestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            previewRequestBuilder?.addTarget(surface!!)

            // Use the newer API for Android API 28+
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                val outputConfig = OutputConfiguration(surface!!)
                val sessionConfig = SessionConfiguration(
                    SessionConfiguration.SESSION_REGULAR,
                    listOf(outputConfig),
                    Executors.newSingleThreadExecutor(),
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            captureSession = session
                            val captureRequest = previewRequestBuilder?.build()
                            captureSession?.setRepeatingRequest(captureRequest!!, null, backgroundHandler)
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            Toast.makeText(this@CameraActivity, "Camera configuration failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                cameraDevice?.createCaptureSession(sessionConfig)
            } else {
                // Fallback to older API
                cameraDevice?.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        val captureRequest = previewRequestBuilder?.build()
                        captureSession?.setRepeatingRequest(captureRequest!!, null, backgroundHandler)
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Toast.makeText(this@CameraActivity, "Camera configuration failed", Toast.LENGTH_SHORT).show()
                    }
                }, backgroundHandler)
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    override fun onResume() {
        super.onResume()
        startBackgroundThread()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            if (cameraDevice == null) setupCamera()
        }
    }

    override fun onPause() {
        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
        imageReader?.close()
        imageReader = null
        stopBackgroundThread()
        super.onPause()
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground")
        backgroundThread?.start()
        backgroundHandler = Handler(backgroundThread?.looper!!)
    }

    private fun rotateCamera() {
        currentRotation = (currentRotation + 90) % 360
        Toast.makeText(this, "Rotation: ${currentRotation}°", Toast.LENGTH_SHORT).show()
    }

    private fun switchCamera() {
        // Close current camera
        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
        imageReader?.close()
        imageReader = null

        // Switch camera type
        isUsingFrontCamera = !isUsingFrontCamera
        
        val cameraType = if (isUsingFrontCamera) "Front" else "Back"
        Toast.makeText(this, "Switching to $cameraType Camera", Toast.LENGTH_SHORT).show()

        // Setup new camera
        setupCamera()
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        
        val matrix = Matrix()
        matrix.postRotate(degrees.toFloat())
        
        return Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
        )
    }

    private fun getCameraOrientation(): Int {
        if (cameraId == null) return 0
        
        try {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId!!)
            val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
            
            val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val rotation = windowManager.defaultDisplay.rotation
            
            var degrees = 0
            when (rotation) {
                Surface.ROTATION_0 -> degrees = 0
                Surface.ROTATION_90 -> degrees = 90
                Surface.ROTATION_180 -> degrees = 180
                Surface.ROTATION_270 -> degrees = 270
            }
            
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            var result: Int
            
            if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                result = (sensorOrientation + degrees) % 360
                result = (360 - result) % 360  // compensate the mirror
            } else {  // back-facing
                result = (sensorOrientation - degrees + 360) % 360
            }
            
            return result
        } catch (e: CameraAccessException) {
            e.printStackTrace()
            return 0
        }
    }

    private fun getDisplayRotation(): Int {
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return when (windowManager.defaultDisplay.rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }




    private fun processImage(image: Image) {
        try {
            val width = image.width
            val height = image.height

            // Convert YUV_420_888 to RGB
            val bitmap = yuv420ToBitmap(image)

            // Convert to Mat
            val inputMat = Mat()
            val resultMat = Mat()
            Utils.bitmapToMat(bitmap, inputMat)

            // Process in C++
            when (currentFilter) {
                FilterMode.NORMAL -> inputMat.copyTo(resultMat)
                FilterMode.GRAY -> convertToGray(inputMat.nativeObjAddr, resultMat.nativeObjAddr)
                FilterMode.BLUR -> applyBlur(inputMat.nativeObjAddr, resultMat.nativeObjAddr)
                FilterMode.EDGES -> detectEdges(inputMat.nativeObjAddr, resultMat.nativeObjAddr)
            }

            // Convert back to bitmap
            val resultBitmap = createBitmap(resultMat.width(), resultMat.height())
            Utils.matToBitmap(resultMat, resultBitmap)

            // Apply camera orientation correction
            val cameraOrientation = getCameraOrientation()
            val totalRotation = (cameraOrientation + currentRotation) % 360

            // Apply rotation if needed
            val finalBitmap = if (totalRotation != 0) {
                rotateBitmap(resultBitmap, totalRotation)
            } else {
                resultBitmap
            }

            // Update UI on main thread
            handler.post {
                findViewById<ImageView>(R.id.cameraPreview).setImageBitmap(finalBitmap)
            }

            // Clean up Mats
            inputMat.release()
            resultMat.release()

            // Clean up bitmaps if rotation was applied
            if (totalRotation != 0 && finalBitmap != resultBitmap) {
                resultBitmap.recycle()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun yuv420ToBitmap(image: Image): Bitmap {
        val planes = image.planes
        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        // Copy Y plane
        yBuffer.get(nv21, 0, ySize)
        
        // Copy U and V planes (interleaved for NV21 format)
        val uvPixelStride = planes[1].pixelStride
        if (uvPixelStride == 1) {
            uBuffer.get(nv21, ySize, uSize)
            vBuffer.get(nv21, ySize + uSize, vSize)
        } else {
            // Handle interleaved UV data
            val uvBuffer = ByteArray(uSize + vSize)
            uBuffer.get(uvBuffer, 0, uSize)
            vBuffer.get(uvBuffer, uSize, vSize)
            
            var uvIndex = 0
            for (i in 0 until uSize step uvPixelStride) {
                nv21[ySize + uvIndex] = uvBuffer[i]
                nv21[ySize + uvIndex + 1] = uvBuffer[uSize + i]
                uvIndex += 2
            }
        }

        // Use OpenCV to convert YUV to RGB
        val yuvMat = Mat(image.height + image.height / 2, image.width, org.opencv.core.CvType.CV_8UC1)
        yuvMat.put(0, 0, nv21)
        
        val rgbMat = Mat()
        Imgproc.cvtColor(yuvMat, rgbMat, Imgproc.COLOR_YUV2RGB_NV21)
        
        val bitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(rgbMat, bitmap)
        
        // Clean up
        yuvMat.release()
        rgbMat.release()
        
        return bitmap
    }

    enum class FilterMode {
        NORMAL, GRAY, BLUR, EDGES
    }
}

