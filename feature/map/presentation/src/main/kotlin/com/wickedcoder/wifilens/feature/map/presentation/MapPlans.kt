package com.wickedcoder.wifilens.feature.map.presentation

import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wickedcoder.wifilens.core.designsystem.WifiLensBottomSheet
import com.wickedcoder.wifilens.core.designsystem.WifiLensIcon
import com.wickedcoder.wifilens.core.designsystem.WifiLensIconButton
import com.wickedcoder.wifilens.core.designsystem.WifiLensListItem
import com.wickedcoder.wifilens.core.designsystem.WifiLensPrimaryButton
import com.wickedcoder.wifilens.core.designsystem.WifiLensSpacing
import com.wickedcoder.wifilens.core.designsystem.WifiLensTextButton
import com.wickedcoder.wifilens.core.designsystem.danger
import com.wickedcoder.wifilens.feature.map.domain.MAX_NAME_LENGTH
import com.wickedcoder.wifilens.feature.map.domain.PlanSummary

private const val PLAN_FILE_MIME = "application/json"

/**
 * The plans sheet: every saved plan (open one marked), with rename, duplicate, export and delete, plus New plan and
 * Import plan. Files go through the Storage Access Framework, so no storage permission is involved.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlansSheet(
    plans: List<PlanSummary>,
    onDismiss: () -> Unit,
    onAction: (MapAction) -> Unit,
    onNewPlan: () -> Unit,
) {
    var exportTarget by remember { mutableStateOf<PlanSummary?>(null) }
    var renameTarget by remember { mutableStateOf<PlanSummary?>(null) }
    var deleteTarget by remember { mutableStateOf<PlanSummary?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(PLAN_FILE_MIME)) { uri ->
        val target = exportTarget
        if (uri != null && target != null) onAction(MapAction.ExportPlan(target.id, uri.toString()))
        exportTarget = null
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            onAction(MapAction.ImportPlan(uri.toString()))
            onDismiss()
        }
    }
    val copyNameFormat = stringResource(R.string.map_plans_copy_name, "%s")

    WifiLensBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = WifiLensSpacing.sm)) {
            Text(
                stringResource(R.string.map_plans_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = WifiLensSpacing.md, vertical = WifiLensSpacing.sm),
            )
            Text(
                stringResource(R.string.map_plans_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = WifiLensSpacing.md),
            )
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                items(plans, key = { it.id }) { plan ->
                    PlanRow(
                        plan = plan,
                        onOpen = {
                            if (!plan.isActive) onAction(MapAction.OpenPlan(plan.id))
                            onDismiss()
                        },
                        onRename = { renameTarget = plan },
                        onDuplicate = {
                            onAction(MapAction.DuplicatePlan(plan.id, copyNameFormat.format(plan.name).take(MAX_NAME_LENGTH)))
                            onDismiss()
                        },
                        onExport = {
                            exportTarget = plan
                            exportLauncher.launch("${plan.name}.wifilens.json")
                        },
                        onDelete = { deleteTarget = plan },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = WifiLensSpacing.md, vertical = WifiLensSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(WifiLensSpacing.sm),
            ) {
                WifiLensPrimaryButton(text = stringResource(R.string.map_plans_new), onClick = onNewPlan, modifier = Modifier.weight(1f))
                WifiLensTextButton(
                    text = stringResource(R.string.map_plans_import),
                    // Some file providers don't tag .json files, so accept any type; the codec rejects non-plans.
                    onClick = { importLauncher.launch(arrayOf(PLAN_FILE_MIME, "*/*")) },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(WifiLensSpacing.lg))
        }
    }

    renameTarget?.let { plan ->
        PlanNameSheet(
            title = stringResource(R.string.map_plans_rename_title),
            initialName = plan.name,
            confirmLabel = stringResource(R.string.map_action_save),
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                onAction(MapAction.RenamePlan(plan.id, name))
                renameTarget = null
            },
        )
    }
    deleteTarget?.let { plan ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.map_plans_delete_title, plan.name)) },
            text = { Text(stringResource(R.string.map_plans_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    onAction(MapAction.DeletePlan(plan.id))
                    deleteTarget = null
                }) { Text(stringResource(R.string.map_plans_delete), color = MaterialTheme.colorScheme.danger) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.map_action_cancel)) } },
        )
    }
}

@Composable
private fun PlanRow(
    plan: PlanSummary,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val details = if (plan.updatedAt > 0) {
        val edited = DateUtils.getRelativeTimeSpanString(plan.updatedAt).toString()
        stringResource(R.string.map_plans_details, plan.width, plan.height, edited)
    } else {
        stringResource(R.string.map_plans_details_no_date, plan.width, plan.height)
    }
    WifiLensListItem(
        headline = plan.name,
        supporting = details,
        icon = if (plan.isActive) WifiLensIcon.Check else null,
        onClick = onOpen,
        trailing = {
            Box {
                WifiLensIconButton(
                    icon = WifiLensIcon.MoreVert,
                    contentDescription = stringResource(R.string.map_plans_more, plan.name),
                    onClick = { menuOpen = true },
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    PlanMenuItem(R.string.map_plans_rename) {
                        menuOpen = false
                        onRename()
                    }
                    PlanMenuItem(R.string.map_plans_duplicate) {
                        menuOpen = false
                        onDuplicate()
                    }
                    PlanMenuItem(R.string.map_plans_export) {
                        menuOpen = false
                        onExport()
                    }
                    PlanMenuItem(R.string.map_plans_delete) {
                        menuOpen = false
                        onDelete()
                    }
                }
            }
        },
    )
}

@Composable
private fun PlanMenuItem(label: Int, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = onClick)
}

/** Single-field sheet for naming a plan (rename). Blank names are not accepted. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlanNameSheet(
    title: String,
    initialName: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    WifiLensBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = WifiLensSpacing.md, vertical = WifiLensSpacing.sm)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(WifiLensSpacing.lg))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(MAX_NAME_LENGTH) },
                label = { Text(stringResource(R.string.map_plan_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(WifiLensSpacing.lg))
            WifiLensPrimaryButton(
                text = confirmLabel,
                onClick = { onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
            WifiLensTextButton(text = stringResource(R.string.map_action_cancel), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(WifiLensSpacing.lg))
        }
    }
}
