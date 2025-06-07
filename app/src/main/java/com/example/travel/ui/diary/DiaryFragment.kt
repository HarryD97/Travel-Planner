package com.example.travel.ui.diary

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.travel.R
import com.example.travel.adapters.DiaryAdapter
import com.example.travel.adapters.WaypointAdapter
import com.example.travel.databinding.FragmentDiaryBinding
import com.example.travel.data.TravelDiary
import com.example.travel.data.DiaryWaypoint

class DiaryFragment : Fragment() {

    private var _binding: FragmentDiaryBinding? = null
    private val binding get() = _binding!!

    private lateinit var diaryViewModel: DiaryViewModel
    private lateinit var diaryAdapter: DiaryAdapter
    private lateinit var waypointAdapter: WaypointAdapter

    // Current diary - for viewing only
    private var currentDiary: TravelDiary? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Use requireActivity() to ensure ViewModel is shared at the Activity level
        diaryViewModel = ViewModelProvider(requireActivity())[DiaryViewModel::class.java]
        _binding = FragmentDiaryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUI()
        setupRecyclerViews()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        // Refresh data every time the Fragment is entered
        diaryViewModel.loadAllDiariesWithDetails()
    }

    private fun setupUI() {
        // DiaryFragment is mainly used for viewing the diary list; recording functionality is in DiaryRecordFragment
        // Hide recording-related UI elements
        binding.btnStartDiary.visibility = View.GONE
        binding.btnStopDiary.visibility = View.GONE
        binding.btnTakePhoto.visibility = View.GONE
        binding.btnSelectFromGallery.visibility = View.GONE
        binding.btnAddWaypoint.visibility = View.GONE
        binding.btnToggleCamera.visibility = View.GONE
        binding.cameraContainer.visibility = View.GONE

        // Hide the diary creation input area
        binding.etDiaryTitle.visibility = View.GONE
        binding.etDiaryDescription.visibility = View.GONE

        updateUI()
    }

    private fun setupRecyclerViews() {
        // Setup diary list
        diaryAdapter = DiaryAdapter(
            onDiaryClick = { diary -> viewDiary(diary) },
            onEditClick = { diary -> editDiary(diary) },
            onDeleteClick = { diary -> showDeleteConfirmDialog(diary) }
        )
        binding.recyclerViewDiaries.adapter = diaryAdapter
        binding.recyclerViewDiaries.layoutManager = LinearLayoutManager(context)

        // Setup waypoint list for current diary
        waypointAdapter = WaypointAdapter { waypoint ->
            viewWaypoint(waypoint)
        }
        binding.recyclerViewWaypoints.adapter = waypointAdapter
        binding.recyclerViewWaypoints.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
    }

    private fun observeViewModel() {
        diaryViewModel.allDiariesWithDetails.observe(viewLifecycleOwner) { diaries ->
            diaryAdapter.submitList(diaries)
        }

        // Observe the current recorded diary state (for display only)
        diaryViewModel.currentDiary.observe(viewLifecycleOwner) { diary ->
            currentDiary = diary
            updateCurrentDiaryUI()
        }

        diaryViewModel.currentWaypoints.observe(viewLifecycleOwner) { waypoints ->
            waypointAdapter.submitList(waypoints)
        }

        diaryViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        diaryViewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            if (error.isNotEmpty()) {
                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                diaryViewModel.clearErrorMessage()
            }
        }
    }

    private fun viewDiary(diary: TravelDiary) {
        diaryViewModel.selectDiary(diary.id)
    }

    private fun viewWaypoint(waypoint: DiaryWaypoint) {
        // Handle waypoint click - could show details or navigate to location
        Toast.makeText(context, "Waypoint: ${waypoint.location.name}", Toast.LENGTH_SHORT).show()
    }

    private fun editDiary(diary: TravelDiary) {
        // Use a simple edit dialog because a full edit page requires additional navigation configuration
        showSimpleEditDialog(diary)
    }

    private fun showSimpleEditDialog(diary: TravelDiary) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_diary, null)
        val etTitle = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etEditTitle)
        val etDescription = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etEditDescription)
        val tvWaypointCount = dialogView.findViewById<android.widget.TextView>(R.id.tvWaypointCount)
        val tvPhotoCount = dialogView.findViewById<android.widget.TextView>(R.id.tvPhotoCount)

        // Set current values
        etTitle.setText(diary.title)
        etDescription.setText(diary.description)
        tvWaypointCount.text = "${diary.waypoints.size} Waypoints"
        tvPhotoCount.text = "${diary.photos.size} Photos"

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Edit Diary")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val newTitle = etTitle.text.toString().trim()
                val newDescription = etDescription.text.toString().trim()

                if (newTitle.isNotEmpty()) {
                    diaryViewModel.updateDiaryInfo(diary, newTitle, newDescription)
                    Toast.makeText(context, "Diary updated", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Title cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteConfirmDialog(diary: TravelDiary) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Delete Diary")
            .setMessage("Are you sure you want to delete diary \"${diary.title}\"? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                deleteDiary(diary)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteDiary(diary: TravelDiary) {
        diaryViewModel.deleteDiary(diary)
        // Refresh list
        diaryViewModel.loadAllDiariesWithDetails()
        Toast.makeText(context, "Deleted diary: ${diary.title}", Toast.LENGTH_SHORT).show()
    }

    private fun updateUI() {
        // DiaryFragment is mainly for viewing, UI has been set in setupUI
    }

    private fun updateCurrentDiaryUI() {
        currentDiary?.let { diary ->
            binding.tvCurrentDiaryTitle.text = diary.title
            binding.tvStepCount.text = "Steps: ${diary.totalSteps}"
            binding.tvWaypointCount.text = "Waypoints: ${diary.waypoints.size}"
            binding.tvPhotoCount.text = "Photos: ${diary.photos.size}"

            binding.currentDiaryContainer.visibility = View.VISIBLE
        } ?: run {
            binding.currentDiaryContainer.visibility = View.GONE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}