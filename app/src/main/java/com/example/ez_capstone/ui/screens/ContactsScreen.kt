package com.example.ez_capstone.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.ez_capstone.db.entity.ContactEntity
import com.example.ez_capstone.ui.theme.Accent
import com.example.ez_capstone.ui.theme.Background
import com.example.ez_capstone.ui.theme.Border
import com.example.ez_capstone.ui.theme.Success
import com.example.ez_capstone.ui.theme.Surface
import com.example.ez_capstone.ui.theme.SurfaceCard
import com.example.ez_capstone.ui.theme.TextDim
import com.example.ez_capstone.ui.theme.TextPrimary
import com.example.ez_capstone.ui.theme.TextSecondary
import com.example.ez_capstone.viewmodel.ContactsViewModel

@Composable
fun ContactsScreen(
    onBackClick: () -> Unit,
    viewModel: ContactsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newPhone by remember { mutableStateOf("") }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.importFromDevice()
    }

    fun requestImport() {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.importFromDevice()
        else permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        // TopBar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", tint = TextPrimary)
            }
            Text("연락처", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { showAdd = !showAdd }) {
                Icon(Icons.Filled.Add, "연락처 추가", tint = Accent)
            }
        }

        // Import card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(SurfaceCard)
                .padding(16.dp)
        ) {
            Text(
                "주행 중 메시지를 빠르게 보내려면 전화번호부를 미리 가져오세요.",
                fontSize = 13.sp,
                color = TextSecondary
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { requestImport() },
                enabled = !state.isImporting,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Accent.copy(alpha = 0.18f),
                    contentColor = Accent
                )
            ) {
                if (state.isImporting) {
                    CircularProgressIndicator(color = Accent, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Download, null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(if (state.isImporting) "가져오는 중..." else "전화번호부 가져오기", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            state.lastImportAdded?.let { added ->
                Spacer(Modifier.height(8.dp))
                Text("$added 명 새로 추가됨 · 총 ${state.contacts.size}명", fontSize = 12.sp, color = Success)
            }
            state.error?.let { err ->
                Spacer(Modifier.height(8.dp))
                Text("오류: $err", fontSize = 12.sp, color = com.example.ez_capstone.ui.theme.Alert)
            }
        }

        // Add form
        if (showAdd) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = { Text("이름", color = TextDim) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(color = TextPrimary)
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newPhone,
                        onValueChange = { newPhone = it },
                        placeholder = { Text("전화번호", color = TextDim) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        textStyle = TextStyle(color = TextPrimary)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            viewModel.addContact(newName, newPhone)
                            newName = ""; newPhone = ""; showAdd = false
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent.copy(alpha = 0.18f), contentColor = Accent),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                    ) { Text("추가") }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "저장된 연락처 ${state.contacts.size}명",
            fontSize = 12.sp,
            color = TextDim,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        if (state.contacts.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("연락처가 없습니다.\n위 버튼으로 전화번호부를 가져오세요.", fontSize = 13.sp, color = TextDim)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(state.contacts, key = { it.id }) { contact ->
                    ContactRow(contact = contact, onDelete = { viewModel.deleteContact(contact) })
                }
            }
        }
    }
}

@Composable
private fun ContactRow(contact: ContactEntity, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Person, null, tint = Accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(contact.name, fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                contact.phone?.let { Text(it, fontSize = 12.sp, color = TextSecondary) }
            }
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.Delete, "삭제", tint = TextDim, modifier = Modifier.size(18.dp))
        }
    }
}
