package com.example.ohrana

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.nfc.NfcAdapter
import android.nfc.NfcManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ohrana.ui.components.OhranaOutlinedButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckpointEditorScreen(
    checkpointId: String? = null,
    onBack: () -> Unit,
    onSave: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity

    val prefsManager = remember { SharedPrefsManager(context) }

    val existingCheckpoint = checkpointId?.let { if (it.isEmpty()) null else prefsManager.getCheckpointById(it) }
    val isEditing = existingCheckpoint != null

    fun extractIdNumber(fullId: String): String {
        return if (fullId.startsWith("CP-")) {
            fullId.substring(3)
        } else {
            fullId
        }
    }

    var idNumberField by remember { mutableStateOf(existingCheckpoint?.let { extractIdNumber(it.id) } ?: "") }
    var idField by remember { mutableStateOf(existingCheckpoint?.id ?: "") }
    var nameField by remember { mutableStateOf(existingCheckpoint?.name ?: "") }

    var showSaveDialog by remember { mutableStateOf(false) }
    var lastSavedId by remember { mutableStateOf("") }
    var actionField by remember { mutableStateOf(existingCheckpoint?.action ?: CheckpointAction.CHECKPOINT) }
    var questionText by remember { mutableStateOf(existingCheckpoint?.questionText ?: "") }
    var inputTitle by remember { mutableStateOf(existingCheckpoint?.inputTitle ?: "") }
    var imageUri by remember { mutableStateOf(existingCheckpoint?.imageUri ?: "") }
    var nfcId by remember { mutableStateOf(existingCheckpoint?.nfcId ?: "") }
    var nfcScanningEnabled by remember { mutableStateOf(false) }
    var nfcScanResult by remember { mutableStateOf<String?>(null) }
    var answers by remember { mutableStateOf(existingCheckpoint?.answers ?: emptyList()) }

    val bitmapState = remember(imageUri) {
        mutableStateOf<Bitmap?>(null)
    }

    LaunchedEffect(imageUri) {
        if (imageUri.isNotEmpty()) {
            val uri = Uri.parse(imageUri)
            bitmapState.value = runCatching {
                BitmapFactory.decodeStream(context.contentResolver.openInputStream(uri))
            }.getOrNull()
        } else {
            bitmapState.value = null
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            uri?.let {
                val uriString = uri.toString()
                Toast.makeText(context, "Выбран URI: $uriString", Toast.LENGTH_SHORT).show()
                imageUri = uriString
            } ?: run {
                Toast.makeText(context, "URI не выбран", Toast.LENGTH_SHORT).show()
            }
        }
    )

    fun saveCheckpoint() {
        if (idNumberField.isBlank()) {
            Toast.makeText(context, "Номер ID не может быть пустым", Toast.LENGTH_SHORT).show()
            return
        }

        if (!idNumberField.matches(Regex("\\d+"))) {
            Toast.makeText(context, "Номер ID должен содержать только цифры", Toast.LENGTH_SHORT).show()
            return
        }

        val fullId = "CP-$idNumberField"

        if (nameField.isBlank()) {
            Toast.makeText(context, "Имя не может быть пустым", Toast.LENGTH_SHORT).show()
            return
        }

        val checkpoint = Checkpoint(
            id = fullId,
            name = nameField.trim(),
            action = actionField,
            questionText = if (actionField == CheckpointAction.QUESTION) questionText else null,
            answers = answers.filter { it.isNotBlank() }.toList(),
            inputTitle = if (actionField == CheckpointAction.INPUT) inputTitle else null,
            imageUri = if (imageUri.isNotEmpty()) imageUri else null,
            nfcId = if (nfcId.isNotEmpty()) nfcId else null
        )

        Toast.makeText(context, "Сохранение: imageUri=${checkpoint.imageUri}", Toast.LENGTH_LONG).show()

        prefsManager.addCheckpoint(checkpoint)
        lastSavedId = fullId
        showSaveDialog = true
    }

    // NFC scanning handler
    var nfcAdapter by remember { mutableStateOf<NfcAdapter?>(null) }

    LaunchedEffect(Unit) {
        try {
            nfcAdapter = NfcAdapter.getDefaultAdapter(context)
        } catch (e: Exception) {
            // NFC не поддерживается
        }
    }

    LaunchedEffect(nfcScanningEnabled) {
        if (nfcScanningEnabled && nfcAdapter != null && activity != null) {
            try {
                nfcAdapter?.enableReaderMode(
                    activity,
                    { tag ->
                        val nfcId = tag.id.joinToString(":") { byte -> String.format("%02X", byte) }
                        nfcScanResult = nfcId
                    },
                    NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B,
                    null
                )
            } catch (e: Exception) {
                // Error handling
            }
        } else {
            try {
                nfcAdapter?.disableReaderMode(activity)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    // Check for NFC scan result
    LaunchedEffect(nfcScanResult) {
        if (nfcScanResult != null) {
            nfcId = nfcScanResult!!
            nfcScanningEnabled = false
            nfcScanResult = null
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (nfcScanningEnabled && nfcAdapter != null) {
                try {
                    nfcAdapter?.disableReaderMode(activity)
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "Редактировать чекпоинт" else "Создать чекпоинт") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF616161) // Серый фон как у экрана
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Размытый фон
            BlurredBackground()

            // Контент экрана
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (showSaveDialog) {
                    Dialog(
                        onDismissRequest = { },
                        properties = DialogProperties(usePlatformDefaultWidth = false)
                    ) {
                        Card(
                            modifier = Modifier.padding(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF595757))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = "Чекпоинт сохранен",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFFFFFF)
                                )
                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = "Чекпоинт $lastSavedId успешно сохранен.",
                                    fontSize = 14.sp,
                                    color = Color(0xFFFFFFFF)
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(
                                        onClick = {
                                            showSaveDialog = false
                                            onBack()
                                        }
                                    ) {
                                        Text("Выйти", color = Color(0xFFFFFFFF))
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Button(
                                        onClick = {
                                            showSaveDialog = false
                                            idNumberField = ""
                                            nameField = ""
                                            actionField = CheckpointAction.CHECKPOINT
                                            questionText = ""
                                            inputTitle = ""
                                            imageUri = ""
                                            nfcId = ""
                                            answers = emptyList()
                                            Toast.makeText(context, "Форма очищена.", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Text("Добавить еще один", color = Color(0xFFFFFFFF))
                                    }
                                }
                            }
                        }
                    }
                }

                if (imageUri.isNotEmpty()) {
                    Text(
                        text = "Текущий URI: $imageUri",
                        fontSize = 12.sp,
                        color = Color(0xFF00FF00),
                        modifier = Modifier.padding(8.dp)
                    )
                }

                OutlinedTextField(
                    value = "CP-$idNumberField",
                    onValueChange = {
                        val input = it.replace("CP-", "")
                        val numericValue = input.filter { it.isDigit() }
                        idNumberField = numericValue
                    },
                    label = { Text("ID чекпоинта (CP-XXX)", color = Color(0xFF000000)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFFFFFFFF),
                        unfocusedTextColor = Color(0xFFFFFFFF),
                        focusedContainerColor = Color(0xFF424242),
                        unfocusedContainerColor = Color(0xFF424242),
                        disabledContainerColor = Color(0xFF424242),
                        focusedBorderColor = Color(0xFFFFFFFF),
                        unfocusedBorderColor = Color(0xFFFFFFFF)
                    )
                )

                OutlinedTextField(
                    value = nameField,
                    onValueChange = { nameField = it },
                    label = { Text("Имя чекпоинта", color = Color(0xFF000000)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFFFFFFFF),
                        unfocusedTextColor = Color(0xFFFFFFFF),
                        focusedContainerColor = Color(0xFF424242),
                        unfocusedContainerColor = Color(0xFF424242),
                        disabledContainerColor = Color(0xFF424242),
                        focusedBorderColor = Color(0xFFFFFFFF),
                        unfocusedBorderColor = Color(0xFFFFFFFF)
                    )
                )

                Text("Тип действия:", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color(0xFFFFFFFF))

                var expanded by remember { mutableStateOf(false) }

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = actionField.name.lowercase().replace('_', ' '),
                        onValueChange = { },
                        readOnly = true,
                        label = { Text("Выберите тип", color = Color(0xFF000000)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFFFFFFFF),
                            unfocusedTextColor = Color(0xFFFFFFFF),
                            focusedContainerColor = Color(0xFF424242),
                            unfocusedContainerColor = Color(0xFF424242),
                            disabledContainerColor = Color(0xFF424242),
                            focusedBorderColor = Color(0xFFFFFFFF),
                            unfocusedBorderColor = Color(0xFFFFFFFF)
                        ),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CheckpointAction.values().forEach { action ->
                            DropdownMenuItem(
                                text = { Text(action.name.lowercase().replace('_', ' '), color = Color(0xFFFFFFFF)) },
                                onClick = {
                                    actionField = action
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                if (actionField == CheckpointAction.QUESTION) {
                    OutlinedTextField(
                        value = questionText,
                        onValueChange = { questionText = it },
                        label = { Text("Текст вопроса", color = Color(0xFF000000)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFFFFFFFF),
                            unfocusedTextColor = Color(0xFFFFFFFF),
                            focusedContainerColor = Color(0xFF424242),
                            unfocusedContainerColor = Color(0xFF424242),
                            disabledContainerColor = Color(0xFF424242),
                            focusedBorderColor = Color(0xFFFFFFFF),
                            unfocusedBorderColor = Color(0xFFFFFFFF)
                        )
                    )

                    Text("Ответы:", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    answers.forEachIndexed { index, answer ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = answer,
                                onValueChange = { newAnswer ->
                                    answers = answers.toMutableList().apply { this[index] = newAnswer }
                                },
                                label = { Text("Ответ ${index + 1}", color = Color(0xFF000000)) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color(0xFFFFFFFF),
                                    unfocusedTextColor = Color(0xFFFFFFFF),
                                    focusedContainerColor = Color(0xFF424242),
                                    unfocusedContainerColor = Color(0xFF424242),
                                    disabledContainerColor = Color(0xFF424242),
                                    focusedBorderColor = Color(0xFFFFFFFF),
                                    unfocusedBorderColor = Color(0xFFFFFFFF)
                                )
                            )
                            if (answers.size > 1) {
                                IconButton(
                                    onClick = { answers = answers.toMutableList().apply { removeAt(index) } }
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Удалить ответ")
                                }
                            }
                        }
                    }

                    IconButton(onClick = { answers = answers + "Добавить ответ" }) {
                        Icon(Icons.Default.Add, contentDescription = "Добавить ответ")
                    }
                }

                if (actionField == CheckpointAction.INPUT) {
                    OutlinedTextField(
                        value = inputTitle,
                        onValueChange = { inputTitle = it },
                        label = { Text("Текст запроса (название поля)", color = Color(0xFF000000)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFFFFFFFF),
                            unfocusedTextColor = Color(0xFFFFFFFF),
                            focusedContainerColor = Color(0xFF424242),
                            unfocusedContainerColor = Color(0xFF424242),
                            disabledContainerColor = Color(0xFF424242),
                            focusedBorderColor = Color(0xFFFFFFFF),
                            unfocusedBorderColor = Color(0xFFFFFFFF)
                        )
                    )
                }

                Text("Картинка прибора:", fontSize = 16.sp, fontWeight = FontWeight.Medium)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (imageUri.isNotEmpty() && bitmapState.value != null) {
                        Image(
                            bitmap = bitmapState.value!!.asImageBitmap(),
                            contentDescription = "Предпросмотр картинки",
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    } else {
                        Card(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF424242))
                        ) {
                            Text(
                                text = "Нет картинки",
                                fontSize = 12.sp,
                                color = Color(0xFFFFFFFF),
                                modifier = Modifier.fillMaxSize(),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Button(
                            onClick = { imagePickerLauncher.launch("image/*") },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF424242), // Тёмно-серый
                                contentColor = Color(0xFFFFFFFF)    // Белый текст
                            ),
                            elevation = androidx.compose.material3.ButtonDefaults.buttonElevation(
                                defaultElevation = 8.dp,
                                pressedElevation = 16.dp,
                                disabledElevation = 8.dp
                            )
                        ) {
                            Text("Выбрать картинку")
                        }
                        if (imageUri.isNotEmpty()) {
                            TextButton(
                                onClick = { imageUri = "" },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "Удалить картинку",
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                // NFC-ID with scanner button
                OutlinedTextField(
                    value = nfcId,
                    onValueChange = { nfcId = it },
                    label = { Text("NFC-ID (для чекпоинтов без QR)", color = Color(0xFF000000)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFFFFFFFF),
                        unfocusedTextColor = Color(0xFFFFFFFF),
                        focusedContainerColor = Color(0xFF424242),
                        unfocusedContainerColor = Color(0xFF424242),
                        disabledContainerColor = Color(0xFF424242),
                        focusedBorderColor = Color(0xFFFFFFFF),
                        unfocusedBorderColor = Color(0xFFFFFFFF)
                    ),
                    placeholder = { Text("Опционально: ID NFC-тега") }
                )

                if (nfcScanningEnabled) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF424242))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Сканирование NFC...", fontSize = 16.sp, color = Color(0xFFFFFFFF))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Приложите NFC-тег к устройству", fontSize = 14.sp, color = Color(0xFFFFFFFF))
                        }
                    }
                } else {
                    Button(
                        onClick = { nfcScanningEnabled = true },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF424242), // Тёмно-серый
                            contentColor = Color(0xFFFFFFFF)    // Белый текст
                        ),
                        elevation = androidx.compose.material3.ButtonDefaults.buttonElevation(
                            defaultElevation = 8.dp,
                            pressedElevation = 16.dp,
                            disabledElevation = 8.dp
                        )
                    ) {
                        Text("Сканировать NFC-тег")
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Кнопка сохранения
                Button(
                    onClick = { saveCheckpoint() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 32.dp)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF424242), // Синий
                        contentColor = Color(0xFFFFFFFF)    // Белый текст
                    ),
                    elevation = androidx.compose.material3.ButtonDefaults.buttonElevation(
                        defaultElevation = 8.dp,
                        pressedElevation = 16.dp,
                        disabledElevation = 8.dp
                    )
                ) {
                    Icon(Icons.Default.Save, contentDescription = "Сохранить", modifier = Modifier.padding(end = 8.dp))
                    Text("Сохранить чекпоинт", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // Обработка системной кнопки "Назад"
    BackHandler(onBack = onBack)
}