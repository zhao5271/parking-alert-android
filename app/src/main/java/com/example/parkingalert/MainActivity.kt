package com.example.parkingalert

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import com.example.parkingalert.databinding.ActivityMainBinding
import com.example.parkingalert.databinding.DialogAddRuleBinding
import com.example.parkingalert.databinding.ItemRuleCandidateBinding
import com.example.parkingalert.databinding.ItemRuleBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class RuleCandidateDraft(
    val id: String,
    val text: String,
    val type: RuleCandidateType,
    val checked: Boolean = true,
)

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val ruleRepository by lazy { SmsRuleRepository(applicationContext) }
    private val smsHistoryTester by lazy { SmsHistoryTester(applicationContext, ruleRepository) }
    private val dateFormatter by lazy { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    @Volatile
    private var isRunningSmsHistoryTest = false

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            markPermissionsRequested()
            refreshDashboard()
        }

    private val smsReadPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                runRealSmsAlarmTest()
            } else {
                Snackbar.make(binding.root, R.string.read_sms_permission_denied, Snackbar.LENGTH_SHORT).show()
            }
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
            runRealSmsAlarmTest()
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
        val allGranted = smsGranted && cameraGranted && notificationGranted

        binding.permissionStatusText.text = getString(
            R.string.permission_status_template,
            if (smsGranted) getString(R.string.status_granted) else getString(R.string.status_missing),
            if (notificationGranted) getString(R.string.status_granted) else getString(R.string.status_missing),
            if (cameraGranted) getString(R.string.status_granted) else getString(R.string.status_missing),
        )

        binding.permissionCard.visibility = if (allGranted) View.GONE else View.VISIBLE
        binding.permissionActionButton.visibility = if (allGranted) View.GONE else View.VISIBLE
    }

    private fun renderRules() {
        val rules = ruleRepository.getRules()
        binding.rulesContainer.removeAllViews()
        binding.emptyRulesText.visibility = if (rules.isEmpty()) View.VISIBLE else View.GONE

        rules.forEach { rule ->
            val itemBinding = ItemRuleBinding.inflate(LayoutInflater.from(this), binding.rulesContainer, false)
            itemBinding.ruleTitleText.text = rule.name
            itemBinding.ruleSummaryText.text = buildRuleSummary(rule)
            itemBinding.ruleMetaText.text = buildRuleMeta(rule)
            itemBinding.ruleToggleButton.text = getString(
                if (rule.enabled) R.string.disable_rule_button else R.string.enable_rule_button,
            )
            itemBinding.ruleToggleButton.setOnClickListener {
                ruleRepository.updateRuleEnabled(rule.id, !rule.enabled)
                refreshDashboard()
            }
            itemBinding.ruleDeleteButton.setOnClickListener {
                confirmDeleteRule(rule)
            }
            binding.rulesContainer.addView(itemBinding.root)
        }
    }

    private fun confirmDeleteRule(rule: SmsRule) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_rule_dialog_title)
            .setMessage(getString(R.string.delete_rule_dialog_message, rule.name))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.delete_rule_confirm_button) { _, _ ->
                ruleRepository.deleteRule(rule.id)
                refreshDashboard()
                Snackbar.make(binding.root, R.string.rule_delete_success, Snackbar.LENGTH_SHORT).show()
            }
            .show()
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
        configureTagListViewport(dialogBinding)
        val extractedCandidates = mutableListOf<RuleCandidateDraft>()
        val dialog = MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_ParkingAlert_RuleDialog)
            .setView(dialogBinding.root)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.generate_rule_button, null)
            .create()

        dialogBinding.extractTagsButton.setOnClickListener {
            dialogBinding.ruleSampleLayout.error = null
            hideKeyboard(dialogBinding.ruleSampleInput)
            extractedCandidates.clear()
            val sampleText = dialogBinding.ruleSampleInput.text?.toString().orEmpty()
            val extracted = RuleGenerator.extractCandidates(sampleText)
            val recommendedIds = RuleGenerator.extractCandidatesForGeneration(sampleText, extracted)
                .map(RuleCandidate::id)
                .toSet()
            extractedCandidates += extracted.map { candidate ->
                toDraft(candidate, checked = recommendedIds.contains(candidate.id))
            }
            renderTagChips(dialogBinding, extractedCandidates)

            if (extractedCandidates.isEmpty()) {
                dialogBinding.ruleSampleLayout.error = getString(R.string.rule_generation_error)
            }
        }

        dialogBinding.ruleSampleInput.doAfterTextChanged {
            extractedCandidates.clear()
            dialogBinding.ruleSampleLayout.error = null
            clearCandidateViews(dialogBinding)
        }

        dialog.setOnShowListener {
            styleAddRuleDialog(dialog)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                dialogBinding.ruleSampleLayout.error = null
                if (readCandidateDrafts(dialogBinding).isEmpty()) {
                    extractedCandidates.clear()
                    val sampleText = dialogBinding.ruleSampleInput.text?.toString().orEmpty()
                    val extracted = RuleGenerator.extractCandidates(sampleText)
                    val recommendedIds = RuleGenerator.extractCandidatesForGeneration(sampleText, extracted)
                        .map(RuleCandidate::id)
                        .toSet()
                    extractedCandidates += extracted.map { candidate ->
                        toDraft(candidate, checked = recommendedIds.contains(candidate.id))
                    }
                    renderTagChips(dialogBinding, extractedCandidates)
                }

                val generatedRule = RuleGenerator.generate(
                    "",
                    dialogBinding.ruleSampleInput.text?.toString().orEmpty(),
                    buildSelectedCandidates(dialogBinding).also { selectedCandidates ->
                        if (selectedCandidates.isEmpty()) {
                            dialogBinding.ruleSampleLayout.error = getString(R.string.rule_generation_selection_error)
                            return@setOnClickListener
                        }
                    },
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

    private fun styleAddRuleDialog(dialog: AlertDialog) {
        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_rule_dialog_window)

        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.apply {
            isAllCaps = false
            setTextColor(ContextCompat.getColor(context, R.color.dialog_button_secondary_text))
            setBackgroundResource(R.drawable.bg_rule_dialog_action_secondary)
            minHeight = resources.getDimensionPixelSize(R.dimen.rule_dialog_button_height)
            minimumHeight = minHeight
        }

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
            isAllCaps = false
            setTextColor(ContextCompat.getColor(context, R.color.dialog_button_primary_text))
            setBackgroundResource(R.drawable.bg_rule_dialog_action_primary)
            minHeight = resources.getDimensionPixelSize(R.dimen.rule_dialog_button_height)
            minimumHeight = minHeight
        }
    }

    private fun configureTagListViewport(dialogBinding: DialogAddRuleBinding) {
        dialogBinding.tagScrollView.maxHeightPx = (resources.displayMetrics.heightPixels * 0.32f).toInt()
    }

    private fun runRealSmsAlarmTest() {
        if (isRunningSmsHistoryTest) {
            return
        }

        val enabledRules = ruleRepository.getRules().count(SmsRule::enabled)
        if (enabledRules == 0) {
            Snackbar.make(binding.root, R.string.sms_test_no_enabled_rules, Snackbar.LENGTH_SHORT).show()
            return
        }

        if (!hasPermission(Manifest.permission.READ_SMS)) {
            smsReadPermissionLauncher.launch(Manifest.permission.READ_SMS)
            return
        }

        isRunningSmsHistoryTest = true
        setSmsTestLoading(true)
        Thread {
            val result = runCatching {
                smsHistoryTester.findLatestMatchedMessage()
            }

            runOnUiThread {
                isRunningSmsHistoryTest = false
                setSmsTestLoading(false)

                result.getOrNull()?.let { matched ->
                    startAlertService(
                        ReminderCopyGenerator.generate(
                            messageBody = matched.body,
                            matchedRuleName = matched.matchedRuleName,
                            senderHint = matched.address,
                        ),
                    )
                    Snackbar.make(
                        binding.root,
                        getString(
                            R.string.sms_test_match_found,
                            matched.matchedRuleName ?: getString(R.string.sms_test_unknown_rule),
                        ),
                        Snackbar.LENGTH_LONG,
                    ).show()
                    return@runOnUiThread
                }

                val messageRes = if (result.isSuccess) {
                    R.string.sms_test_no_match
                } else {
                    R.string.sms_test_failed
                }
                Snackbar.make(binding.root, messageRes, Snackbar.LENGTH_SHORT).show()
            }
        }.start()
    }

    private fun setSmsTestLoading(isLoading: Boolean) {
        binding.testAlarmButton.isEnabled = !isLoading
        binding.testAlarmButton.text = getString(
            if (isLoading) R.string.test_alarm_button_loading else R.string.test_alarm_button,
        )
    }

    private fun renderTagChips(dialogBinding: DialogAddRuleBinding, candidates: List<RuleCandidateDraft>) {
        dialogBinding.candidateContainer.removeAllViews()
        dialogBinding.tagHintText.visibility = if (candidates.isEmpty()) View.VISIBLE else View.GONE
        dialogBinding.tagScrollView.visibility = if (candidates.isEmpty()) View.GONE else View.VISIBLE
        dialogBinding.tagSelectionHintText.visibility = if (candidates.isEmpty()) View.GONE else View.VISIBLE

        candidates.forEach { draft ->
            val itemBinding = ItemRuleCandidateBinding.inflate(layoutInflater, dialogBinding.candidateContainer, false)
            itemBinding.root.tag = draft
            itemBinding.candidateCheckBox.isChecked = draft.checked
            itemBinding.candidateInput.setText(draft.text)
            applyCandidateCheckedState(itemBinding, draft.checked)
            itemBinding.candidateCheckBox.setOnCheckedChangeListener { _, isChecked ->
                applyCandidateCheckedState(itemBinding, isChecked)
            }
            dialogBinding.candidateContainer.addView(itemBinding.root)
        }
    }

    private fun clearCandidateViews(dialogBinding: DialogAddRuleBinding) {
        dialogBinding.candidateContainer.removeAllViews()
        dialogBinding.tagHintText.visibility = View.VISIBLE
        dialogBinding.tagSelectionHintText.visibility = View.GONE
        dialogBinding.tagScrollView.visibility = View.GONE
    }

    private fun hideKeyboard(targetView: View) {
        targetView.clearFocus()
        val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        inputMethodManager?.hideSoftInputFromWindow(targetView.windowToken, 0)
    }

    private fun applyCandidateCheckedState(itemBinding: ItemRuleCandidateBinding, isChecked: Boolean) {
        itemBinding.root.alpha = if (isChecked) 1f else 0.58f
        itemBinding.candidateInputLayout.alpha = if (isChecked) 1f else 0.72f
    }

    private fun readCandidateDrafts(dialogBinding: DialogAddRuleBinding): List<RuleCandidateDraft> {
        return buildList {
            for (index in 0 until dialogBinding.candidateContainer.childCount) {
                val child = dialogBinding.candidateContainer.getChildAt(index)
                val itemBinding = ItemRuleCandidateBinding.bind(child)
                val draft = child.tag as? RuleCandidateDraft ?: continue
                val candidateText = itemBinding.candidateInput.text?.toString().orEmpty().trim()
                if (candidateText.isBlank()) continue

                add(
                    draft.copy(
                        text = candidateText,
                        checked = itemBinding.candidateCheckBox.isChecked,
                        type = RuleGenerator.inferCandidateType(candidateText),
                    ),
                )
            }
        }
    }

    private fun buildSelectedCandidates(dialogBinding: DialogAddRuleBinding): List<RuleCandidate> {
        return readCandidateDrafts(dialogBinding)
            .filter(RuleCandidateDraft::checked)
            .map { draft ->
                RuleCandidate(
                    text = draft.text,
                    source = RuleCandidateSource.AUTO,
                    type = draft.type,
                )
            }
    }

    private fun toDraft(candidate: RuleCandidate, checked: Boolean = true): RuleCandidateDraft {
        return RuleCandidateDraft(
            id = candidate.id,
            text = candidate.text,
            type = candidate.type,
            checked = checked,
        )
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

    private fun startAlertService(payload: ReminderAlertPayload) {
        val intent = Intent(this, AlertService::class.java).apply {
            action = AlertService.ACTION_START
            putExtra(AlertService.EXTRA_MESSAGE, payload.sourceMessage)
            putAlertPayload(payload)
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
