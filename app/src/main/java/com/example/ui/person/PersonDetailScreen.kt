package com.example.ui.person

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Person
import com.example.data.model.Transaction
import com.example.data.model.TransactionType
import com.example.data.model.User
import com.example.data.repository.PersonRepository
import com.example.data.repository.TransactionRepository
import com.example.ui.common.FormatUtils
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonDetailScreen(
    person: Person,
    khataId: String,
    currentUser: User,
    transactions: List<Transaction>,
    isLoading: Boolean,
    personRepository: PersonRepository,
    transactionRepository: TransactionRepository,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedTxType by remember { mutableStateOf(TransactionType.LENA) }
    var editTransaction by remember { mutableStateOf<Transaction?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf<Transaction?>(null) }
    var showEditPersonDialog by remember { mutableStateOf(false) }
    var showPersonMenu by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = person.name,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        if (!person.phone.isNullOrBlank()) {
                            Text(
                                text = person.phone,
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showPersonMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options")
                    }
                    DropdownMenu(
                        expanded = showPersonMenu,
                        onDismissRequest = { showPersonMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit Details") },
                            onClick = {
                                showPersonMenu = false
                                showEditPersonDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Archive Person") },
                            onClick = {
                                showPersonMenu = false
                                scope.launch {
                                    personRepository.archivePerson(
                                        khataId = khataId,
                                        personId = person.personId,
                                        personName = person.name,
                                        archived = true,
                                        actorName = currentUser.name
                                    )
                                    onBackClick()
                                }
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        bottomBar = {
            // Quick Action Buttons: LENA & DENA
            SurfaceBottomBar(
                onLenaClick = {
                    selectedTxType = TransactionType.LENA
                    editTransaction = null
                    showAddDialog = true
                },
                onDenaClick = {
                    selectedTxType = TransactionType.DENA
                    editTransaction = null
                    showAddDialog = true
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Summary Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    val netColor = when {
                        person.netBalance > 0 -> Color(0xFF2E7D32)
                        person.netBalance < 0 -> Color(0xFFC62828)
                        else -> MaterialTheme.colorScheme.onSurface
                    }

                    Text(
                        text = "Net Balance",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = FormatUtils.getNetBalanceLabel(person.netBalance),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = netColor
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Total LENA", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold))
                            }
                            Text(
                                text = FormatUtils.formatRupees(person.totalLena),
                                style = MaterialTheme.typography.titleMedium.copy(color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = Color(0xFFC62828), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Total DENA", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFC62828), fontWeight = FontWeight.Bold))
                            }
                            Text(
                                text = FormatUtils.formatRupees(person.totalDena),
                                style = MaterialTheme.typography.titleMedium.copy(color = Color(0xFFC62828), fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }

            Text(
                text = "Transaction History (${transactions.size})",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            // Transactions List
            if (isLoading && transactions.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (transactions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No transactions yet",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap LENA or DENA below to add first entry",
                            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(transactions, key = { it.transactionId }) { tx ->
                        TransactionItemCard(
                            transaction = tx,
                            onEditClick = {
                                editTransaction = tx
                                selectedTxType = if (tx.type == "LENA") TransactionType.LENA else TransactionType.DENA
                                showAddDialog = true
                            },
                            onDeleteClick = {
                                showDeleteConfirmDialog = tx
                            }
                        )
                    }
                }
            }
        }
    }

    // Add / Edit Transaction Dialog
    if (showAddDialog) {
        SimpleTransactionDialog(
            personName = person.name,
            initialType = selectedTxType,
            initialAmount = editTransaction?.amount,
            initialNote = editTransaction?.note,
            isEditing = editTransaction != null,
            onDismiss = { showAddDialog = false },
            onSave = { amount, type, note ->
                showAddDialog = false
                scope.launch {
                    if (editTransaction != null) {
                        val oldTx = editTransaction!!
                        val oldType = if (oldTx.type == "LENA") TransactionType.LENA else TransactionType.DENA
                        val result = transactionRepository.updateTransaction(
                            khataId = khataId,
                            personId = person.personId,
                            personName = person.name,
                            transactionId = oldTx.transactionId,
                            oldAmount = oldTx.amount,
                            oldType = oldType,
                            newAmount = amount,
                            newType = type,
                            newNote = note,
                            actorName = currentUser.name
                        )
                        result.onFailure {
                            snackbarHostState.showSnackbar(it.localizedMessage ?: "Failed to update transaction")
                        }
                    } else {
                        val result = transactionRepository.addTransaction(
                            khataId = khataId,
                            personId = person.personId,
                            personName = person.name,
                            amount = amount,
                            type = type,
                            note = note,
                            actorName = currentUser.name
                        )
                        result.onFailure {
                            snackbarHostState.showSnackbar(it.localizedMessage ?: "Failed to add transaction")
                        }
                    }
                }
            }
        )
    }

    // Delete Confirmation Dialog
    showDeleteConfirmDialog?.let { tx ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            title = { Text("Delete Transaction") },
            text = { Text("Are you sure you want to delete this ₹${tx.amount} entry? The amount will be deducted from hisab.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = null
                        scope.launch {
                            val txType = if (tx.type == "LENA") TransactionType.LENA else TransactionType.DENA
                            transactionRepository.deleteTransaction(
                                khataId = khataId,
                                personId = person.personId,
                                personName = person.name,
                                transactionId = tx.transactionId,
                                amount = tx.amount,
                                type = txType,
                                actorName = currentUser.name
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = null }) { Text("Cancel") }
            }
        )
    }

    // Edit Person Dialog
    if (showEditPersonDialog) {
        var editName by remember { mutableStateOf(person.name) }
        var editPhone by remember { mutableStateOf(person.phone ?: "") }
        var editNote by remember { mutableStateOf(person.note ?: "") }

        AlertDialog(
            onDismissRequest = { showEditPersonDialog = false },
            title = { Text("Edit Person Details") },
            text = {
                Column {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = editPhone,
                        onValueChange = { editPhone = it },
                        label = { Text("Phone") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = editNote,
                        onValueChange = { editNote = it },
                        label = { Text("Note") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editName.trim().isNotBlank()) {
                            scope.launch {
                                personRepository.updatePerson(
                                    khataId = khataId,
                                    personId = person.personId,
                                    name = editName.trim(),
                                    phone = editPhone.trim(),
                                    note = editNote.trim(),
                                    actorName = currentUser.name
                                )
                                showEditPersonDialog = false
                            }
                        }
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showEditPersonDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun SurfaceBottomBar(
    onLenaClick: () -> Unit,
    onDenaClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = onLenaClick,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("action_lena_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("+ LENA (Mila)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Button(
                onClick = onDenaClick,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("action_dena_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
            ) {
                Icon(Icons.Default.Remove, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("- DENA (Diya)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun TransactionItemCard(
    transaction: Transaction,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val isLena = transaction.type == "LENA"
    val badgeColor = if (isLena) Color(0xFF2E7D32) else Color(0xFFC62828)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("transaction_item_${transaction.transactionId}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(badgeColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isLena) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                        contentDescription = null,
                        tint = badgeColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = FormatUtils.formatRupees(transaction.amount),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = badgeColor
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isLena) "LENA" else "DENA",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = badgeColor
                            )
                        )
                    }

                    if (!transaction.note.isNullOrBlank()) {
                        Text(
                            text = transaction.note,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Text(
                        text = "${FormatUtils.formatShortDateTime(transaction.createdAt)} • by ${transaction.createdByName}",
                        style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
            }

            Row {
                IconButton(onClick = onEditClick, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDeleteClick, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun SimpleTransactionDialog(
    personName: String,
    initialType: TransactionType,
    initialAmount: Long?,
    initialNote: String?,
    isEditing: Boolean,
    onDismiss: () -> Unit,
    onSave: (Long, TransactionType, String?) -> Unit
) {
    var amountText by remember { mutableStateOf(initialAmount?.toString() ?: "") }
    var selectedType by remember { mutableStateOf(initialType) }
    var noteText by remember { mutableStateOf(initialNote ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEditing) "Edit Transaction" else "Add Transaction for $personName",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                // Type selector chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { selectedType = TransactionType.LENA },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedType == TransactionType.LENA) Color(0xFF2E7D32) else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (selectedType == TransactionType.LENA) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tx_type_lena_button")
                    ) {
                        Text("LENA (Mila)", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { selectedType = TransactionType.DENA },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedType == TransactionType.DENA) Color(0xFFC62828) else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (selectedType == TransactionType.DENA) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tx_type_dena_button")
                    ) {
                        Text("DENA (Diya)", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() }) {
                            amountText = input
                        }
                    },
                    label = { Text("Amount (₹)") },
                    prefix = { Text("₹ ", fontWeight = FontWeight.Bold) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tx_amount_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Note / Description (Optional)") },
                    placeholder = { Text("e.g. Market ka saman, Cash payment") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tx_note_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountText.toLongOrNull() ?: 0L
                    if (amount > 0) {
                        onSave(amount, selectedType, noteText.trim().ifEmpty { null })
                    }
                },
                enabled = (amountText.toLongOrNull() ?: 0L) > 0,
                modifier = Modifier.testTag("tx_save_button")
            ) {
                Text(if (isEditing) "Update" else "Save Transaction")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
