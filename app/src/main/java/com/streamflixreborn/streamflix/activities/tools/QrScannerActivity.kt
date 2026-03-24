package com.streamflixreborn.streamflix.activities.tools

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.Image
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.Result
import com.google.zxing.common.HybridBinarizer
import com.streamflixreborn.streamflix.R
import com.streamflixreborn.streamflix.databinding.ActivityQrScannerBinding
import com.streamflixreborn.streamflix.utils.AppLanguageManager
import com.streamflixreborn.streamflix.utils.ThemeManager
import com.streamflixreborn.streamflix.utils.UserPreferences
import java.util.concurrent.atomic.AtomicBoolean

class QrScannerActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_QR_VALUE = "extra_qr_value"
    }

    private lateinit var binding: ActivityQrScannerBinding
    private val resultDelivered = AtomicBoolean(false)
    private val qrCodeAnalyzer = QrCodeAnalyzer(::deliverResult)

    private var cameraManager: CameraManager? = null
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private var selectedCameraId: String? = null
    private var previewSize: Size = Size(1280, 720)
    private var isOpeningCamera = false

    private val textureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            openCameraIfReady()
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
    }

    private val cameraStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            isOpeningCamera = false
            cameraDevice = camera
            createCaptureSession()
        }

        override fun onDisconnected(camera: CameraDevice) {
            isOpeningCamera = false
            camera.close()
            cameraDevice = null
        }

        override fun onError(camera: CameraDevice, error: Int) {
            isOpeningCamera = false
            camera.close()
            cameraDevice = null
            Toast.makeText(
                this@QrScannerActivity,
                getString(R.string.settings_scan_resolver_failed),
                Toast.LENGTH_SHORT
            ).show()
            finish()
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openCameraIfReady()
        } else {
            Toast.makeText(
                this,
                getString(R.string.settings_scan_resolver_camera_permission_denied),
                Toast.LENGTH_SHORT
            ).show()
            finish()
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguageManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeManager.mobileThemeRes(UserPreferences.selectedTheme))

        super.onCreate(savedInstanceState)

        binding = ActivityQrScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyThemeWindowChrome()

        cameraManager = getSystemService(CameraManager::class.java)

        binding.qrScannerClose.setOnClickListener { finish() }
        binding.qrScannerPreview.surfaceTextureListener = textureListener

        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            Toast.makeText(
                this,
                getString(R.string.settings_scan_resolver_camera_unavailable),
                Toast.LENGTH_SHORT
            ).show()
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        startBackgroundThread()
        openCameraIfReady()
    }

    override fun onPause() {
        closeCamera()
        stopBackgroundThread()
        super.onPause()
    }

    private fun openCameraIfReady() {
        if (resultDelivered.get()) return
        if (!binding.qrScannerPreview.isAvailable) return

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            return
        }

        openCamera()
    }

    @Suppress("MissingPermission")
    private fun openCamera() {
        if (cameraDevice != null || isOpeningCamera) return

        val manager = cameraManager ?: return
        val cameraId = selectedCameraId ?: selectBackCamera(manager) ?: run {
            Toast.makeText(
                this,
                getString(R.string.settings_scan_resolver_camera_unavailable),
                Toast.LENGTH_SHORT
            ).show()
            finish()
            return
        }

        selectedCameraId = cameraId

        val characteristics = manager.getCameraCharacteristics(cameraId)
        val configMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        previewSize = choosePreviewSize(configMap)
        val analysisSize = chooseAnalysisSize(configMap)
        imageReader = ImageReader.newInstance(
            analysisSize.width,
            analysisSize.height,
            ImageFormat.YUV_420_888,
            2
        ).apply {
            setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    qrCodeAnalyzer.analyze(image)
                } finally {
                    image.close()
                }
            }, backgroundHandler)
        }

        isOpeningCamera = true
        manager.openCamera(cameraId, cameraStateCallback, backgroundHandler)
    }

    private fun createCaptureSession() {
        val texture = binding.qrScannerPreview.surfaceTexture ?: return
        val device = cameraDevice ?: return
        val analysisReader = imageReader ?: return

        texture.setDefaultBufferSize(previewSize.width, previewSize.height)
        val previewSurface = Surface(texture)
        val analysisSurface = analysisReader.surface

        val requestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(previewSurface)
            addTarget(analysisSurface)
            set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
        }

        device.createCaptureSession(
            listOf(previewSurface, analysisSurface),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    session.setRepeatingRequest(requestBuilder.build(), null, backgroundHandler)
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Toast.makeText(
                        this@QrScannerActivity,
                        getString(R.string.settings_scan_resolver_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                }
            },
            backgroundHandler
        )
    }

    private fun closeCamera() {
        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
        imageReader?.close()
        imageReader = null
        isOpeningCamera = false
    }

    private fun startBackgroundThread() {
        if (backgroundThread != null) return
        backgroundThread = HandlerThread("QrScanner").also { thread ->
            thread.start()
            backgroundHandler = Handler(thread.looper)
        }
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        backgroundThread?.join()
        backgroundThread = null
        backgroundHandler = null
    }

    private fun selectBackCamera(manager: CameraManager): String? {
        return manager.cameraIdList.firstOrNull { cameraId ->
            manager.getCameraCharacteristics(cameraId)
                .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: manager.cameraIdList.firstOrNull()
    }

    private fun choosePreviewSize(configMap: StreamConfigurationMap?): Size {
        val candidates = configMap
            ?.getOutputSizes(SurfaceTexture::class.java)
            ?.filter { it.width >= 720 && it.height >= 720 }
            ?.sortedBy { it.width * it.height }
            .orEmpty()

        return candidates.firstOrNull() ?: Size(1280, 720)
    }

    private fun chooseAnalysisSize(configMap: StreamConfigurationMap?): Size {
        val candidates = configMap
            ?.getOutputSizes(ImageFormat.YUV_420_888)
            ?.filter { it.width >= 640 && it.height >= 480 }
            ?.sortedBy { it.width * it.height }
            .orEmpty()

        return candidates.firstOrNull() ?: Size(1280, 720)
    }

    private fun deliverResult(result: String) {
        if (!resultDelivered.compareAndSet(false, true)) return

        setResult(
            Activity.RESULT_OK,
            Intent().putExtra(EXTRA_QR_VALUE, result)
        )
        finish()
    }

    private fun applyThemeWindowChrome() {
        val palette = ThemeManager.palette(UserPreferences.selectedTheme)
        window.statusBarColor = palette.systemBar
        window.navigationBarColor = palette.systemBar
    }

    private class QrCodeAnalyzer(
        private val onQrCodeDetected: (String) -> Unit,
    ) {
        private val reader = MultiFormatReader().apply {
            setHints(
                mapOf(
                    DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                    DecodeHintType.TRY_HARDER to true,
                )
            )
        }

        fun analyze(image: Image) {
            val result = runCatching { decode(image) }.getOrNull() ?: return
            onQrCodeDetected(result.text)
        }

        private fun decode(image: Image): Result? {
            val plane = image.planes.firstOrNull() ?: return null
            val width = image.width
            val height = image.height
            val data = extractLuminanceBytes(plane, width, height)

            for (rotation in listOf(0, 90, 180, 270)) {
                val rotatedData = rotateLuminance(data, width, height, rotation)
                val rotatedWidth = if (rotation == 90 || rotation == 270) height else width
                val rotatedHeight = if (rotation == 90 || rotation == 270) width else height
                val source = PlanarYUVLuminanceSource(
                    rotatedData,
                    rotatedWidth,
                    rotatedHeight,
                    0,
                    0,
                    rotatedWidth,
                    rotatedHeight,
                    false
                )

                try {
                    return reader.decodeWithState(BinaryBitmap(HybridBinarizer(source)))
                } catch (_: NotFoundException) {
                    reader.reset()
                }
            }

            return null
        }

        private fun extractLuminanceBytes(
            plane: Image.Plane,
            width: Int,
            height: Int,
        ): ByteArray {
            val buffer = plane.buffer
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride
            val rowBuffer = ByteArray(rowStride)
            val data = ByteArray(width * height)
            var outputOffset = 0

            for (row in 0 until height) {
                val rowLength = if (pixelStride == 1) {
                    width
                } else {
                    (width - 1) * pixelStride + 1
                }

                buffer.position(row * rowStride)
                buffer.get(rowBuffer, 0, rowLength)

                if (pixelStride == 1) {
                    System.arraycopy(rowBuffer, 0, data, outputOffset, width)
                    outputOffset += width
                } else {
                    for (column in 0 until width) {
                        data[outputOffset++] = rowBuffer[column * pixelStride]
                    }
                }
            }

            return data
        }

        private fun rotateLuminance(
            data: ByteArray,
            width: Int,
            height: Int,
            rotationDegrees: Int,
        ): ByteArray = when (rotationDegrees) {
            90 -> {
                val rotated = ByteArray(data.size)
                var index = 0
                for (x in 0 until width) {
                    for (y in height - 1 downTo 0) {
                        rotated[index++] = data[y * width + x]
                    }
                }
                rotated
            }
            180 -> {
                val rotated = ByteArray(data.size)
                for (index in data.indices) {
                    rotated[index] = data[data.lastIndex - index]
                }
                rotated
            }
            270 -> {
                val rotated = ByteArray(data.size)
                var index = 0
                for (x in width - 1 downTo 0) {
                    for (y in 0 until height) {
                        rotated[index++] = data[y * width + x]
                    }
                }
                rotated
            }
            else -> data
        }
    }
}
