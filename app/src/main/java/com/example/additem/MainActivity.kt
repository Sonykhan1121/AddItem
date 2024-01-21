package com.example.additem

import android.app.ProgressDialog
import android.content.DialogInterface
import android.content.Intent
import android.content.Intent.ACTION_GET_CONTENT
import android.graphics.Bitmap
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.example.additem.databinding.ActivityMainBinding
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.storage

import com.skydoves.colorpickerview.ColorEnvelope
import com.skydoves.colorpickerview.ColorPickerDialog
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.lang.Exception
import java.util.UUID

class MainActivity : AppCompatActivity() {
    private val binding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }
    private val selectedImages = mutableListOf<Uri>()
    private val selectedColors = mutableListOf<Int>()
    private val productStorage  = Firebase.storage.reference
    private val firestore = Firebase.firestore
    private var progressDialog :ProgressDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSupportActionBar(binding.toolbar)
        setContentView(binding.root)


        binding.colorsId.setOnClickListener {
            ColorPickerDialog.Builder(this)
                .setTitle("Product Color")
                .setPositiveButton("Select",object :ColorEnvelopeListener{
                    override fun onColorSelected(envelope: ColorEnvelope?, fromUser: Boolean) {
                        envelope?.let {
                            selectedColors.add(it.color)
                            UpdateColors()
                        }
                    }

                })
                .setNegativeButton("Cancel"){ colorPicker, _ ->
                    colorPicker.dismiss()

                }.show()

        }
        val selectImageActivityResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){ result->
            if(result.resultCode == RESULT_OK)
            {
                val intent  = result.data
                if(intent?.clipData !=null)
                {
                    val count = intent.clipData?.itemCount ?:0
                    (0 until count).forEach {
                        val imageuri  = intent.clipData?.getItemAt(it)?.uri
                        imageuri?.let {
                            selectedImages.add(it)
                        }

                    }
                }
                else{
                    val imageuri = intent?.data
                    imageuri?.let { selectedImages.add(it) }

                }
                updateImages()
            }

        }
        binding.pickImage.setOnClickListener {
            val intent = Intent(ACTION_GET_CONTENT)
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true)
            intent.type  = "image/*"
            selectImageActivityResult.launch(intent)
        }

    }

    private fun updateImages() {
        binding.imageCountID.setText(selectedImages.size.toString())
    }

    private fun UpdateColors() {
        var colors = ""
        selectedColors.forEach{
            colors = "$colors ${Integer.toHexString(it)}"
        }
        binding.colorIdShow.setText(colors.toString())
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.tool_bar_menu,menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if(item.itemId==R.id.save_product_id)
        {
            val valid =  validateInformation()
            if(!valid)
                return false

            saveProducts() {
                Log.d("test", it.toString())
            }
            clearAll()
        }

        return super.onOptionsItemSelected(item)
    }

    private fun clearAll() {
        binding.productNameId.setText("")
        binding.productCategoryId.setText("")
        binding.productDescriptionId.setText("")
        binding.priceId.setText("")
        binding.OfferPercentageId.setText("")
        binding.sizeId.setText("")
        binding.imageCountID.setText("0")
        binding.colorIdShow.setText("")
        selectedImages.clear()
        selectedColors.clear()
    }

    private fun saveProducts(state: (Boolean) -> Unit) {
        val sizes = getSizesList(binding.sizeId.text.toString().trim())
        val imagesByteArrays = getImagesByteArrays() //7
        val name = binding.productNameId.text.toString().trim()
        val images = mutableListOf<String>()
        val category = binding.productCategoryId.text.toString().trim()
        val productDescription = binding.productDescriptionId.text.toString().trim()
        val price = binding.priceId.text.toString().trim()
        val offerPercentage = binding.OfferPercentageId.text.toString().trim()

        lifecycleScope.launch {

            try {
                progressDialog = ProgressDialog(this@MainActivity)
                progressDialog?.setMessage("Uploading...")
                progressDialog?.setCancelable(false)
                progressDialog?.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
                progressDialog?.max = 100 // Set the maximum progress value

                val totalBytes = imagesByteArrays.sumBy { it.size }
                progressDialog?.setTitle("Image Upload")

                progressDialog?.show()
                async {
                    imagesByteArrays.forEachIndexed { index, imageByteArray ->
                        val id = UUID.randomUUID().toString()
                        val imagesStorage = productStorage.child("products/images/$id")
                        val result = imagesStorage.putBytes(imageByteArray)
                            .addOnProgressListener { taskSnapshot ->
                                val progress =
                                    ((100.0 * taskSnapshot.bytesTransferred) / taskSnapshot.totalByteCount).toInt()

                                val currentBytes = taskSnapshot.bytesTransferred
                                val imageSize = imageByteArray.size

                                progressDialog?.progress = progress
                                progressDialog?.setMessage(
                                    "Uploading Image ${index + 1}/${imagesByteArrays.size}\n" +
                                            "${String.format("%.2f", currentBytes / (1024.0 * 1024.0))} MB/${String.format("%.2f", totalBytes / (1024.0 * 1024.0))} MB\n" +
                                            "Image Size: ${String.format("%.2f", imageSize / (1024.0 * 1024.0))} MB"
                                )

                            }
                            .await()

                        val downloadUrl = result.storage.downloadUrl.await().toString()
                        images.add(downloadUrl)
                    }
                }.await()
                Toast.makeText(applicationContext, "Pictures uploaded to storage successfully", Toast.LENGTH_SHORT).show()
                progressDialog?.dismiss()
                state(true)
            } catch (e: java.lang.Exception) {
                Log.e("ImageUploadError", e.message.toString())
                progressDialog?.dismiss()
                state(false)

            }

            Log.d("test2", "test")


            val product = Product(
                UUID.randomUUID().toString(),
                name,
                category,
                price.toFloat(),
                if (offerPercentage.isEmpty()) null else offerPercentage.toFloat(),
                if (productDescription.isEmpty()) null else productDescription,
                selectedColors,
                sizes,
                images
            )

            firestore.collection("products").add(product).addOnSuccessListener {
                Toast.makeText(applicationContext, "All details added Successfully", Toast.LENGTH_SHORT).show()
                state(true)
            }.addOnFailureListener {
                Log.e("FirestoreError", it.message.toString())

                state(false)
            }

        }
    }





    private fun getImagesByteArrays(): List<ByteArray> {
        val imagebyteArray = mutableListOf<ByteArray>()
        selectedImages.forEach{
            val stream = ByteArrayOutputStream()
            val imageBmp = MediaStore.Images.Media.getBitmap(contentResolver,it)
            if(imageBmp.compress(Bitmap.CompressFormat.JPEG,100,stream))
            {
                imagebyteArray.add(stream.toByteArray())
            }
        }
        return imagebyteArray
    }

    private fun getSizesList(sizeStr: String): List<String>? {
        if(sizeStr.isEmpty())
            return null
        val sizeList = sizeStr.split(",")
        return sizeList
    }

    private fun validateInformation():Boolean {
        if(binding.productNameId.text.toString().trim().isEmpty())
        {
            binding.productNameId.apply {
                requestFocus()
                error = "This is empty"
            }
            return false

        }
        if(binding.productCategoryId.text.toString().trim().isEmpty())
        {
            binding.productCategoryId.apply {
                requestFocus()
                error = "This is empty"
            }
            return false


        }

        if(binding.priceId.text.toString().trim().isEmpty())
        {
            binding.priceId.apply {
                requestFocus()
                error = "This is empty"
            }

            return false


        }


        if (selectedImages.isEmpty())
        {
            binding.pickImage.apply {
                requestFocus()
                error = "This is empty"
            }
            return false

        }
        return true
    }
}