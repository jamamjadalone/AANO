// More tab: settings entry points (Cloudflare sync, backup, theme, app lock).
// Copyright (C) 2026 Jam Amjad Rasheed
// Licensed under GPL-3.0-or-later.

package app.aa.aano.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import app.aa.aano.R
import app.aa.aano.data.AppLock
import app.aa.aano.data.NoteRepository
import app.aa.aano.data.TaskRepository
import app.aa.aano.databinding.FragmentMoreBinding
import app.aa.aano.net.CloudSyncManager
import kotlinx.coroutines.launch

class MoreFragment : Fragment() {

    private var _binding: FragmentMoreBinding? = null
    private val binding get() = _binding!!

    private lateinit var cloudSyncManager: CloudSyncManager

    private val lockLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            refreshLockUi()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cloudSyncManager = CloudSyncManager(
            requireContext(),
            NoteRepository(requireContext()),
            TaskRepository(requireContext())
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMoreBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }
        binding.btnTrash.setOnClickListener {
            startActivity(Intent(requireContext(), TrashActivity::class.java))
        }
        binding.btnEnableLock.setOnClickListener {
            lockLauncher.launch(
                Intent(requireContext(), LockActivity::class.java)
                    .putExtra(LockActivity.EXTRA_MODE, LockActivity.MODE_SET_PIN)
            )
        }
        binding.btnChangePin.setOnClickListener {
            lockLauncher.launch(
                Intent(requireContext(), LockActivity::class.java)
                    .putExtra(LockActivity.EXTRA_MODE, LockActivity.MODE_SET_PIN)
            )
        }
        binding.btnDisableLock.setOnClickListener {
            lockLauncher.launch(
                Intent(requireContext(), LockActivity::class.java)
                    .putExtra(LockActivity.EXTRA_MODE, LockActivity.MODE_DISABLE)
            )
        }
        binding.btnGoogle.setOnClickListener {
            if (cloudSyncManager.isLoggedIn) {
                cloudSyncManager.logout()
                Toast.makeText(requireContext(), R.string.disconnected, Toast.LENGTH_SHORT).show()
                updateCloudUi()
            } else {
                startActivity(Intent(requireContext(), SettingsActivity::class.java))
            }
        }
        binding.btnSyncNow.setOnClickListener { syncNow() }
        refreshLockUi()
        updateCloudUi()
    }

    override fun onResume() {
        super.onResume()
        refreshLockUi()
        updateCloudUi()
    }

    private fun updateCloudUi() {
        val connected = cloudSyncManager.isLoggedIn
        binding.googleStatus.text = if (connected) {
            getString(R.string.connected_as, cloudSyncManager.currentEmail ?: "")
        } else {
            getString(R.string.not_connected)
        }
        binding.btnGoogle.text = getString(
            if (connected) R.string.disconnect_google else R.string.sign_in_google
        )
        binding.btnSyncNow.visibility = if (connected) View.VISIBLE else View.GONE
    }

    private fun syncNow() {
        lifecycleScope.launch {
            val res = cloudSyncManager.syncNow()
            res.onSuccess { count ->
                Toast.makeText(requireContext(), "Sync complete. Synced $count items.", Toast.LENGTH_SHORT).show()
            }.onFailure { e ->
                Toast.makeText(
                    requireContext(),
                    getString(R.string.sync_failed, e.message ?: "error"),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun refreshLockUi() {
        val enabled = AppLock(requireContext()).pinEnabled
        binding.btnEnableLock.visibility = if (enabled) View.GONE else View.VISIBLE
        binding.btnChangePin.visibility = if (enabled) View.VISIBLE else View.GONE
        binding.btnDisableLock.visibility = if (enabled) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}