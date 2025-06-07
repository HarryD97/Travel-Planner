package com.example.travel.ui.diary

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController

import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.travel.R
import com.example.travel.adapters.PhotoEditAdapter
import com.example.travel.adapters.WaypointEditAdapter
import com.example.travel.data.DiaryPhoto
import com.example.travel.data.DiaryWaypoint
import com.example.travel.data.Location
import com.example.travel.data.TravelDiary
import com.example.travel.databinding.FragmentDiaryEditBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class DiaryEditFragment : Fragment() {

    private var _binding: FragmentDiaryEditBinding? = null
    private val binding get() = _binding!!

    private lateinit var diaryViewModel: DiaryViewModel
    private lateinit var waypointAdapter: WaypointEditAdapter
    private lateinit var photoAdapter: PhotoEditAdapter

    private var currentDiary: TravelDiary? = null
    private var currentPhotoUri: Uri? = null
    private var diaryId: String? = null

    // Handle photo capture result
    private val takePhotoLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && currentPhotoUri != null) {
            addPhotoToDiary(currentPhotoUri!!.toString())
        }
    }

    // Handle photo selection result
    private val selectPhotoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            addPhotoToDiary(it.toString())
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        diaryViewModel = ViewModelProvider(requireActivity())[DiaryViewModel::class.java]
        _binding = FragmentDiaryEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerViews()
        setupUI()
        observeViewModel()
        loadDiaryData()
    }

    private fun setupRecyclerViews() {
        // Set up waypoint list
        waypointAdapter = WaypointEditAdapter { waypoint ->
            showDeleteWaypointDialog(waypoint)
        }
        binding.recyclerViewWaypoints.adapter = waypointAdapter
        binding.recyclerViewWaypoints.layoutManager = LinearLayoutManager(context)

        // Set up photo list
        photoAdapter = PhotoEditAdapter { photo ->
            showDeletePhotoDialog(photo)
        }
        binding.recyclerViewPhotos.adapter = photoAdapter
        binding.recyclerViewPhotos.layoutManager = GridLayoutManager(context, 2)
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnSave.setOnClickListener {
            saveDiary()
        }

        binding.btnAddWaypoint.setOnClickListener {
            // TODO: Open map to select location or manually input location information
            showAddWaypointDialog()
        }

        binding.btnTakePhoto.setOnClickListener {
            takePhoto()
        }

        binding.btnSelectPhoto.setOnClickListener {
            selectPhoto()
        }
    }

    private fun observeViewModel() {
        diaryViewModel.currentDiary.observe(viewLifecycleOwner) { diary ->
            currentDiary = diary
            updateUI(diary)
        }

        diaryViewModel.currentWaypoints.observe(viewLifecycleOwner) { waypoints ->
            waypointAdapter.submitList(waypoints)
            binding.tvNoWaypoints.visibility = if (waypoints.isEmpty()) View.VISIBLE else View.GONE
        }

        diaryViewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            if (error.isNotEmpty()) {
                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                diaryViewModel.clearErrorMessage()
            }
        }
    }

    private fun loadDiaryData() {
        // Get diaryId from arguments
        diaryId = arguments?.getString("diaryId")
        diaryId?.let { id ->
            diaryViewModel.selectDiary(id)
        } ?: run {
            Toast.makeText(context, "Diary ID not found", Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
        }
    }

    private fun updateUI(diary: TravelDiary?) {
        diary?.let {
            binding.etTitle.setText(it.title)
            binding.etDescription.setText(it.description)

            // Update photo list
            photoAdapter.submitList(it.photos)
            binding.tvNoPhotos.visibility = if (it.photos.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun saveDiary() {
        val title = binding.etTitle.text.toString().trim()
        val description = binding.etDescription.text.toString().trim()

        if (title.isEmpty()) {
            Toast.makeText(context, "Title cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        currentDiary?.let { diary ->
            diaryViewModel.updateDiaryInfo(diary, title, description)
            Toast.makeText(context, "Diary saved", Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
        }
    }

    private fun showAddWaypointDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_waypoint, null)
        val etName = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etWaypointName)
        val etAddress = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etWaypointAddress)

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Add Waypoint")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val name = etName.text.toString().trim()
                val address = etAddress.text.toString().trim()

                if (name.isNotEmpty()) {
                    addWaypointToDiary(name, address)
                } else {
                    Toast.makeText(context, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addWaypointToDiary(name: String, address: String) {
        currentDiary?.let { diary ->
            val location = Location(
                name = name,
                address = address,
                latitude = 0.0, // TODO: Get actual location
                longitude = 0.0
            )

            // Use current step count as waypoint step count
            diaryViewModel.addWaypointToDiary(diary.id, location, diary.totalSteps)
        }
    }

    private fun takePhoto() {
        val photoFile = createImageFile()
        currentPhotoUri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            photoFile
        )
        takePhotoLauncher.launch(currentPhotoUri)
    }

    private fun selectPhoto() {
        selectPhotoLauncher.launch("image/*")
    }

    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "JPEG_${timeStamp}_"
        val storageDir = requireContext().getExternalFilesDir("Pictures")
        return File.createTempFile(imageFileName, ".jpg", storageDir)
    }

    private fun addPhotoToDiary(photoPath: String) {
        currentDiary?.let { diary ->
            diaryViewModel.addPhotoToDiary(diary.id, photoPath, null)
        }
    }

    private fun showDeleteWaypointDialog(waypoint: DiaryWaypoint) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Delete Waypoint")
            .setMessage("Are you sure you want to delete waypoint \"${waypoint.location.name}\"?")
            .setPositiveButton("Delete") { _, _ ->
                currentDiary?.let { diary ->
                    diaryViewModel.deleteWaypoint(diary.id, waypoint)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeletePhotoDialog(photo: DiaryPhoto) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Delete Photo")
            .setMessage("Are you sure you want to delete this photo?")
            .setPositiveButton("Delete") { _, _ ->
                currentDiary?.let { diary ->
                    diaryViewModel.deletePhoto(diary.id, photo)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}