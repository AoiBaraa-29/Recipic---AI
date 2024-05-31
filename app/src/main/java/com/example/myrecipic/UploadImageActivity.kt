package com.example.myrecipic

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.myrecipic.ml.AutoModel1
import com.example.myrecipic.ml.Model
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp


class UploadImageActivity : AppCompatActivity() {
    private val paint = Paint()
    private lateinit var imageView: ImageView
    private lateinit var button: Button
    private lateinit var openCamera: Button
    private lateinit var bitmap: Bitmap
    private lateinit var model: AutoModel1
    private lateinit var labels: List<String>
    private val imageProcessor = ImageProcessor.Builder().add(ResizeOp(300, 300, ResizeOp.ResizeMethod.BILINEAR)).build()

    private val colors = listOf(
        Color.BLUE, Color.GREEN, Color.RED, Color.CYAN, Color.GRAY, Color.BLACK, Color.DKGRAY,
        Color.MAGENTA, Color.YELLOW
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.upload_image)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val intent = Intent()
        intent.type = "image/*"
        intent.action = Intent.ACTION_GET_CONTENT

        labels = FileUtil.loadLabels(this, "labels2.txt")
        model = AutoModel1.newInstance(this)
        imageView = findViewById(R.id.imageView)
        button = findViewById(R.id.imgBtn)
        openCamera = findViewById(R.id.cameraBtn)

        button.setOnClickListener {
            startActivityForResult(intent, 101)
        }
        openCamera.setOnClickListener {
            val intent = Intent(this, CameraActivity::class.java)
            startActivity(intent)
        }

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 101) {
            val uri = data?.data
            bitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, uri)
            getPredictions()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        model.close()
    }

    private fun getPredictions() {
        var image = TensorImage.fromBitmap(bitmap)
        image = imageProcessor.process(image)

        val outputs = model.process(image)
        val locations = outputs.locationsAsTensorBuffer.floatArray
        val classes = outputs.classesAsTensorBuffer.floatArray
        val scores = outputs.scoresAsTensorBuffer.floatArray
        val numberOfDetections = outputs.numberOfDetectionsAsTensorBuffer.floatArray

        val mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutable)

        val h = mutable.height
        val w = mutable.width

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

