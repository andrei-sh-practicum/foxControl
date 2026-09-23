package com.andrew.foxcontrol.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.andrew.foxcontrol.data.local.entity.TrackedAppEntity

/** Password field with a show/hide toggle; [errorText] is shown under the field. */
@Composable
internal fun PasswordTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    errorText: String? = null,
    singleLine: Boolean = false
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier,
        isError = isError || errorText != null,
        singleLine = singleLine,
        supportingText = if (errorText != null) { { Text(errorText) } } else null,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = if (visible) "Скрыть пароль" else "Показать пароль"
                )
            }
        }
    )
}

/** Read-only dropdown to pick one of the tracked [apps]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppPickerDropdown(
    apps: List<TrackedAppEntity>,
    selectedAppName: String,
    onAppSelected: (TrackedAppEntity) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedAppName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Приложение") },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            if (apps.isEmpty()) {
                DropdownMenuItem(
                    onClick = { expanded = false },
                    text = { Text("Нет доступных приложений") }
                )
            } else {
                apps.forEach { app ->
                    DropdownMenuItem(
                        onClick = {
                            onAppSelected(app)
                            expanded = false
                        },
                        text = { Text(app.appName) }
                    )
                }
            }
        }
    }
}
