package com.example.myrecipic

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.view.Surface
import android.view.TextureView
import android.widget.Button
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.myrecipic.ml.AutoModel1
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp

class CameraActivity : AppCompatActivity() {
    private val paint = Paint()
    private lateinit var imageView: ImageView
    private lateinit var selectImage: Button
    private lateinit var openCamera: Button
    private var bitmap: Bitmap? = null
    private lateinit var model: AutoModel1
    private lateinit var labels: List<String>
    private val imageProcessor = ImageProcessor.Builder().add(ResizeOp(300, 300, ResizeOp.ResizeMethod.BILINEAR)).build()
    private val colors = listOf(
        Color.BLUE, Color.GREEN, Color.RED, Color.CYAN, Color.GRAY, Color.BLACK, Color.DKGRAY,
        Color.MAGENTA, Color.YELLOW
    )

    private lateinit var textureView: TextureView
    private lateinit var cameraManager: CameraManager
    private lateinit var handler: Handler
    private lateinit var cameraDevice: CameraDevice

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_camera)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        get_permission()
        textureView = findViewById(R.id.textureView)
        model = AutoModel1.newInstance(this)
        textureView.surfaceTextureListener = object: TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {
                // Camera will be opened when the button is clicked
            }

            override fun onSurfaceTextureSizeChanged(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {}

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                return false
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                bitmap = textureView.bitmap
                bitmap?.let {
                    getPredictions()
                }
            }
        }

        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val handlerThread = HandlerThread("videoThread")
        handlerThread.start()
        handler = Handler(handlerThread.looper)

        val intent = Intent()
        intent.type = "image/*"
        intent.action = Intent.ACTION_GET_CONTENT

        labels = FileUtil.loadLabels(this, "labels2.txt")
        imageView = findViewById(R.id.imageView)
        selectImage = findViewById(R.id.imgBtn)
        openCamera = findViewById(R.id.cameraBtn)

        selectImage.setOnClickListener {
            val intent = Intent(this, UploadImageActivity::class.java)
            startActivity(intent)
        }

        openCamera.setOnClickListener {
            if (textureView.isAvailable) {
                open_camera()
            } else {
                textureView.surfaceTextureListener = object: TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(
                        surface: SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {
                        open_camera()
                    }

                    override fun onSurfaceTextureSizeChanged(
                        surface: SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {}

                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                        return false
                    }

                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                        bitmap = textureView.bitmap
                        bitmap?.let {
                            getPredictions()
                        }
                    }
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 101) {
            val uri = data?.data
            bitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, uri)
            bitmap?.let {
                getPredictions()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        model.close()
    }

    private fun getPredictions() {
        bitmap?.let { bmp ->
            val mutable = bmp.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(mutable)
            val h = mutable.height
            val w = mutable.width
            var image = TensorImage.fromBitmap(bmp)
            image = imageProcessor.process(image)

            val outputs = model.process(image)
            val locations = outputs.locationsAsTensorBuffer.floatArray
            val classes = outputs.classesAsTensorBuffer.floatArray
            val scores = outputs.scoresAsTensorBuffer.floatArray

            paint.textSize = h / 15f
            paint.strokeWidth = h / 85f

            scores.forEachIndexed { index, score ->
                val x = index * 4
                if (score > 0.5) {
                    paint.color = colors[index % colors.size]
                    paint.style = Paint.Style.STROKE
                    canvas.drawRect(
                        RectF(
                            locations[x + 1] * w,
                            locations[x] * h,
                            locations[x + 3] * w,
                            locations[x + 2] * h
                        ), paint
                    )
                    paint.style = Paint.Style.FILL
                    canvas.drawText(
                        "${labels[classes[index].toInt()]} ",
                        locations[x + 1] * w,
                        locations[x] * h,
                        paint
                    )
                }
            }
            imageView.setImageBitmap(mutable)
        }
    }

    private fun get_permission() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 101)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            get_permission()
        }
    }

    @SuppressLint("MissingPermission")
    private fun open_camera() {
        cameraManager.openCamera(cameraManager.cameraIdList[0], object : CameraDevice.StateCallback() {
            override fun onOpened(pO: CameraDevice) {
                cameraDevice = pO

                val surfaceTexture = textureView.surfaceTexture
                val surface = Surface(surfaceTexture)

                val captureRequest = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                captureRequest.addTarget(surface)

                cameraDevice.createCaptureSession(listOf(surface), object: CameraCaptureSession.StateCallback() {
                    override fun onConfigured(pO: CameraCaptureSession) {
                        pO.setRepeatingRequest(captureRequest.build(), null, null)
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {}
                }, handler)
            }

            override fun onDisconnected(camera: CameraDevice) {}

            override fun onError(camera: CameraDevice, error: Int) {}
        }, handler)
    }
}
