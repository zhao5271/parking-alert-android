package com.example.parkingalert

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import com.example.parkingalert.databinding.ActivityMainBinding
import com.example.parkingalert.databinding.DialogAddRuleBinding
import com.example.parkingalert.databinding.ItemRuleBinding
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val ruleRepository by lazy { SmsRuleRepository(applicationContext) }
    private val dateFormatter by lazy { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            markPermissionsRequested()
            refreshDashboard()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.permissionActionButton.setOnClickListener {
            requestNeededPermissions()
        }

        binding.addRuleButton.setOnClickListener {
            showAddRuleDialog()
        }

        binding.testAlarmButton.setOnClickListener {
            startAlertService(getString(R.string.test_alert_message))
        }

        binding.stopAlarmButton.setOnClickListener {
            stopAlertService()
        }

        refreshDashboard()
        binding.root.post {
            if (!hasRequestedPermissionsBefore() && hasMissingCorePermission()) {
                requestNeededPermissions()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshDashboard()
    }

    private fun requestNeededPermissions() {
        val permissions = buildList {
            add(Manifest.permission.RECEIVE_SMS)
            add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.filterNot(::hasPermission)

        if (permissions.isNotEmpty()) {
            markPermissionsRequested()
            permissionLauncher.launch(permissions.toTypedArray())
        } else {
            refreshDashboard()
        }
    }

    private fun refreshDashboard() {
        refreshPermissionStatus()
        refreshHeroStatus()
        renderRules()
    }

    private fun refreshHeroStatus() {
        val rules = ruleRepository.getRules()
        val enabledCount = rules.count(SmsRule::enabled)
        val (labelRes, backgroundRes) = when {
            hasMissingCorePermission() -> R.string.hero_status_permission_pending to R.drawable.bg_status_badge_warning
            enabledCount == 0 -> R.string.hero_status_rules_off to R.drawable.bg_status_badge_neutral
            else -> R.string.hero_status_armed to R.drawable.bg_status_badge_active
        }

        binding.heroStatusBadge.setText(labelRes)
        binding.heroStatusBadge.setBackgroundResource(backgroundRes)
        binding.heroRuleCountText.text = getString(R.string.hero_rule_count, enabledCount, rules.size)
    }

    private fun refreshPermissionStatus() {
        val smsGranted = hasPermission(Manifest.permission.RECEIVE_SMS)
        val cameraGranted = hasPermission(Manifest.permission.CAMERA)
        val notificationGranted =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                hasPermission(Manifest.permission.POST_NOTIFICATIONS)

        binding.permissionStatusText.text = getString(
            R.string.permission_status_template,
            if (smsGranted) getString(R.string.status_granted) else getString(R.string.status_missing),
            if (notificationGranted) getString(R.string.status_granted) else getString(R.string.status_missing),
            if (cameraGranted) getString(R.string.status_granted) else getString(R.string.status_missing),
        )

        binding.permissionActionButton.visibility =
            if (smsGranted && cameraGranted && notificationGranted) View.GONE else View.VISIBLE
    }

    private fun renderRules() {
        val rules = ruleRepository.getRules()
        binding.rulesContainer.removeAllViews()
        binding.emptyRulesText.visibility = if (rules.isEmpty()) View.VISIBLE else View.GONE

        rules.forEach { rule ->
            val itemBinding = ItemRuleBinding.inflate(LayoutInflater.from(this), binding.rulesContainer, false)
            itemBinding.ruleTitleText.text = rule.name
            itemBinding.ruleBadgeText.text = getString(
                if (rule.isBuiltIn) R.string.rule_badge_builtin else R.string.rule_badge_custom,
            )
            itemBinding.ruleBadgeText.setBackgroundResource(
                if (rule.isBuiltIn) R.drawable.bg_rule_badge_builtin else R.drawable.bg_rule_badge_custom,
            )
            itemBinding.ruleSummaryText.text = buildRuleSummary(rule)
            itemBinding.ruleMetaText.text = buildRuleMeta(rule)
            itemBinding.ruleEnabledSwitch.isChecked = rule.enabled
            itemBinding.ruleEnabledSwitch.setOnCheckedChangeListener { _, isChecked ->
                ruleRepository.updateRuleEnabled(rule.id, isChecked)
                refreshHeroStatus()
            }
            binding.rulesContainer.addView(itemBinding.root)
        }
    }

    private fun buildRuleSummary(rule: SmsRule): String {
        return if (rule.isBuiltIn) {
            getString(R.string.rule_summary_builtin)
        } else {
            getString(R.string.rule_summary_custom, rule.keywordPreview().ifBlank { getString(R.string.rule_keywords_fallback) })
        }
    }

    private fun buildRuleMeta(rule: SmsRule): String {
        val keywordCount = (rule.requiredKeywordGroups.flatten() + rule.supplementaryKeywords).distinct().size
        return if (rule.isBuiltIn) {
            getString(R.string.rule_meta_builtin, keywordCount)
        } else {
            getString(R.string.rule_meta_custom, dateFormatter.format(Date(rule.createdAt)), keywordCount)
        }
    }

    private fun showAddRuleDialog() {
        val dialogBinding = DialogAddRuleBinding.inflate(layoutInflater)
        var extractedTags = emptyList<String>()
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.add_rule_dialog_title)
            .setView(dialogBinding.root)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.generate_rule_button, null)
            .create()

        dialogBinding.extractTagsButton.setOnClickListener {
            dialogBinding.ruleSampleLayout.error = null
            extractedTags = RuleGenerator.extractTags(
                dialogBinding.ruleSampleInput.text?.toString().orEmpty(),
            )
            renderTagChips(dialogBinding, extractedTags)

            if (extractedTags.isEmpty()) {
                dialogBinding.ruleSampleLayout.error = getString(R.string.rule_generation_error)
            }
        }

        dialogBinding.ruleSampleInput.doAfterTextChanged {
            extractedTags = emptyList()
            dialogBinding.ruleSampleLayout.error = null
            dialogBinding.tagChipGroup.removeAllViews()
            dialogBinding.tagHintText.visibility = View.VISIBLE
        }

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                dialogBinding.ruleSampleLayout.error = null
                if (extractedTags.isEmpty()) {
                    extractedTags = RuleGenerator.extractTags(
                        dialogBinding.ruleSampleInput.text?.toString().orEmpty(),
                    )
                    renderTagChips(dialogBinding, extractedTags)
                }

                val selectedTags = buildList {
                    for (index in 0 until dialogBinding.tagChipGroup.childCount) {
                        val chip = dialogBinding.tagChipGroup.getChildAt(index) as? Chip ?: continue
                        if (chip.isChecked) add(chip.text.toString())
                    }
                }

                val generatedRule = RuleGenerator.generate(
                    dialogBinding.ruleNameInput.text?.toString().orEmpty(),
                    dialogBinding.ruleSampleInput.text?.toString().orEmpty(),
                    selectedTags,
                )

                if (generatedRule == null) {
                    dialogBinding.ruleSampleLayout.error = getString(R.string.rule_generation_tag_error)
                    return@setOnClickListener
                }

                ruleRepository.addRule(generatedRule)
                refreshDashboard()
                dialog.dismiss()
                Snackbar.make(binding.root, R.string.rule_generation_success, Snackbar.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun renderTagChips(dialogBinding: DialogAddRuleBinding, tags: List<String>) {
        dialogBinding.tagChipGroup.removeAllViews()
        dialogBinding.tagHintText.visibility = if (tags.isEmpty()) View.VISIBLE else View.GONE

        tags.forEach { tag ->
            val chip = Chip(this).apply {
                text = tag
                isCheckable = true
                isChecked = true
                isClickable = true
            }
            dialogBinding.tagChipGroup.addView(chip)
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun hasMissingCorePermission(): Boolean {
        val notificationGranted =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        return !hasPermission(Manifest.permission.RECEIVE_SMS) ||
            !hasPermission(Manifest.permission.CAMERA) ||
            !notificationGranted
    }

    private fun hasRequestedPermissionsBefore(): Boolean {
        return getSharedPreferences(UI_PREFS, MODE_PRIVATE)
            .getBoolean(KEY_HAS_REQUESTED_CORE_PERMISSIONS, false)
    }

    private fun markPermissionsRequested() {
        getSharedPreferences(UI_PREFS, MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_HAS_REQUESTED_CORE_PERMISSIONS, true)
            .apply()
    }

    private fun startAlertService(message: String) {
        val intent = Intent(this, AlertService::class.java).apply {
            action = AlertService.ACTION_START
            putExtra(AlertService.EXTRA_MESSAGE, message)
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopAlertService() {
        val intent = Intent(this, AlertService::class.java).apply {
            action = AlertService.ACTION_STOP
        }
        startService(intent)
    }

    companion object {
        private const val UI_PREFS = "parking_alert_ui"
        private const val KEY_HAS_REQUESTED_CORE_PERMISSIONS = "has_requested_core_permissions"
    }
}
