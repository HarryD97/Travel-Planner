package com.example.travel.ui.diary

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.travel.R
import com.example.travel.adapters.DiaryAdapter
import com.example.travel.adapters.DiaryDetailWaypointAdapter
import com.example.travel.adapters.DiaryPhotosAdapter
import com.example.travel.databinding.FragmentDiaryViewBinding
import com.example.travel.data.TravelDiary
import com.example.travel.data.DiaryWaypoint

class DiaryViewFragment : Fragment() {

    private var _binding: FragmentDiaryViewBinding? = null
    private val binding get() = _binding!!

    private lateinit var diaryViewModel: DiaryViewModel
    private lateinit var diaryAdapter: DiaryAdapter
    private lateinit var waypointAdapter: DiaryDetailWaypointAdapter
    private lateinit var photosAdapter: DiaryPhotosAdapter

    private var selectedDiary: TravelDiary? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        diaryViewModel = ViewModelProvider(requireActivity())[DiaryViewModel::class.java]
        _binding = FragmentDiaryViewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerViews()
        setupUI()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        // Refresh data every time the Fragment is entered to ensure the latest travel data is displayed.
        diaryViewModel.loadAllDiariesWithDetails()
    }

    private fun setupRecyclerViews() {
        // Setup diary list
        diaryAdapter = DiaryAdapter(
            onDiaryClick = { diary -> selectDiary(diary) },
            onEditClick = { diary -> editDiary(diary) },
            onDeleteClick = { diary -> showDeleteConfirmDialog(diary) }
        )
        binding.recyclerViewDiaries.adapter = diaryAdapter
        binding.recyclerViewDiaries.layoutManager = LinearLayoutManager(context)

        // Setup waypoint list for selected diary
        waypointAdapter = DiaryDetailWaypointAdapter { waypoint ->
            navigateToMapWithWaypoint(waypoint)
        }
        binding.recyclerViewWaypoints.adapter = waypointAdapter
        binding.recyclerViewWaypoints.layoutManager = LinearLayoutManager(context)

        // Setup photos grid
        photosAdapter = DiaryPhotosAdapter { photoPath ->
            // Handle photo click - could open full screen view
        }
        binding.recyclerViewPhotos.adapter = photosAdapter
        binding.recyclerViewPhotos.layoutManager = GridLayoutManager(context, 2)
    }

    private fun setupUI() {
        binding.btnBackToList.setOnClickListener {
            showDiaryList()
        }

        // Initially show diary list
        showDiaryList()
    }

    private fun observeViewModel() {
        // Observe the diary list with full details, not the basic diary list.
        diaryViewModel.allDiariesWithDetails.observe(viewLifecycleOwner) { diaries ->
            diaryAdapter.submitList(diaries)
        }

        diaryViewModel.selectedDiary.observe(viewLifecycleOwner) { diary ->
            selectedDiary = diary
            updateDiaryDetail(diary)
        }

        diaryViewModel.selectedWaypoints.observe(viewLifecycleOwner) { waypoints ->
            waypointAdapter.submitList(waypoints)
        }

        diaryViewModel.selectedPhotos.observe(viewLifecycleOwner) { photos ->
            photosAdapter.submitList(photos)
        }
    }

    private fun selectDiary(diary: TravelDiary) {
        diaryViewModel.selectDiaryForView(diary.id)
        showDiaryDetail()
    }

    private fun showDiaryList() {
        binding.layoutDiaryList.visibility = View.VISIBLE
        binding.layoutDiaryDetail.visibility = View.GONE
        selectedDiary = null
    }

    private fun showDiaryDetail() {
        binding.layoutDiaryList.visibility = View.GONE
        binding.layoutDiaryDetail.visibility = View.VISIBLE
    }

    private fun updateDiaryDetail(diary: TravelDiary?) {
        diary?.let {
            binding.tvDetailTitle.text = it.title
            binding.tvDetailDescription.text = if (it.description.isNotEmpty()) it.description else "No description"
            binding.tvDetailDate.text = android.text.format.DateFormat.format("yyyy/MM/dd HH:mm", it.date)
            binding.tvDetailSteps.text = "${it.totalSteps} steps"
            binding.tvDetailDistance.text = "%.2f km".format(it.totalDistance)
            binding.tvDetailWaypoints.text = "${it.waypoints.size} waypoints"
            binding.tvDetailPhotos.text = "${it.photos.size} photos"

            val statusText = if (it.isCompleted) "Completed" else "In Progress"
            binding.tvDetailStatus.text = statusText

            // Show sections based on content
            binding.sectionWaypoints.visibility = if (it.waypoints.isNotEmpty()) View.VISIBLE else View.GONE
            binding.sectionPhotos.visibility = if (it.photos.isNotEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun navigateToMapWithWaypoint(waypoint: DiaryWaypoint) {
        // Create bundle with waypoint data
        val bundle = Bundle().apply {
            putDouble("latitude", waypoint.location.latitude)
            putDouble("longitude", waypoint.location.longitude)
            putString("locationName", waypoint.location.name)
        }

        // Navigate to map fragment with waypoint data
        findNavController().navigate(R.id.navigation_map, bundle)
    }

    private fun editDiary(diary: TravelDiary) {
        // Use a simple edit dialog, as a full edit page requires additional navigation configuration.
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
        tvWaypointCount.text = "${diary.waypoints.size} waypoints"
        tvPhotoCount.text = "${diary.photos.size} photos"

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
            .setMessage("Are you sure you want to delete the diary \"${diary.title}\"? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                deleteDiary(diary)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteDiary(diary: TravelDiary) {
        diaryViewModel.deleteDiary(diary)

        // Refresh diary list data
        diaryViewModel.loadAllDiariesWithDetails()

        Toast.makeText(context, "Deleted diary: ${diary.title}", Toast.LENGTH_SHORT).show()
        // If the currently viewed diary is deleted, return to the list page.
        if (selectedDiary?.id == diary.id) {
            showDiaryList()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}