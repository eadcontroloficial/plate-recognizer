package com.example.platerecognizer

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.example.platerecognizer.databinding.ActivityMainBinding
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var photoUri: Uri? = null
    private var photoFile: File? = null

    private val takePictureLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success && photoUri != null) {
                photoUri?.let { uri ->
                    val inputStream = contentResolver.openInputStream(uri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    binding.previewImage.setImageBitmap(bitmap)
                    inputStream?.close()
                    uploadPhoto(photoFile!!)
                }
            } else {
                Toast.makeText(this, "Falha ao capturar foto", Toast.LENGTH_SHORT).show()
            }
        }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                dispatchTakePictureIntent()
            } else {
                Toast.makeText(this, "Permissão de câmera negada", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.captureButton.setOnClickListener {
            startCamera()
        }
    }

    private fun startCamera() {
        if (shouldShowRequestPermissionRationale(android.Manifest.permission.CAMERA)) {
            // pode explicar o motivo, mas vamos só pedir novamente
        }
        requestPermissionLauncher.launch(android.Manifest.permission.CAMERA)
    }

    private fun dispatchTakePictureIntent() {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val imageFileName = "plate_$timeStamp.jpg"
        val storageDir = File(cacheDir, "camera_photos")
        if (!storageDir.exists()) storageDir.mkdirs()
        val file = File(storageDir, imageFileName)
        photoFile = file
        photoUri = FileProvider.getUriForFile(
            this,
            "${applicationContext.packageName}.fileprovider",
            file
        )
        takePictureLauncher.launch(photoUri!!)
    }

    private fun uploadPhoto(file: File) {
        val token = binding.apiTokenEdit.text.toString().trim()
        if (token.isEmpty()) {
            Toast.makeText(this, "Insira o token da API", Toast.LENGTH_SHORT).show()
            return
        }

        val requestBody = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
        val imagePart = MultipartBody.Part.createFormData("upload", file.name, requestBody)

        binding.resultText.text = "Enviando..."

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = NetworkClient.api.recognizePlate(
                    token = "Token $token",
                    image = imagePart
                )
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        val plates = response.body()?.results
                        if (!plates.isNullOrEmpty()) {
                            val firstPlate = plates[0].plate
                            val score = plates[0].score
                            binding.resultText.text = "Placa: $firstPlate (confiança: ${"%.2f".format(score)})"
                        } else {
                            binding.resultText.text = "Nenhuma placa encontrada"
                        }
                    } else {
                        binding.resultText.text = "Erro ${response.code()}: ${response.message()}"
                        Log.e("API_ERROR", response.errorBody()?.string() ?: "")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.resultText.text = "Exceção: ${e.localizedMessage}"
                    Log.e("UPLOAD_ERROR", e.toString())
                }
            }
        }
    }
}
