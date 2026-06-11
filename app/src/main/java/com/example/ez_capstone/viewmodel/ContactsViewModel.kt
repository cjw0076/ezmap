package com.example.ez_capstone.viewmodel

import android.content.Context
import android.provider.ContactsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ez_capstone.db.dao.ContactDao
import com.example.ez_capstone.db.entity.ContactEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 주행 중 메시지 전송을 위해 단말 연락처를 미리 앱 DB(ContactEntity)로 가져와 둔다.
 * send_message / manage_contacts 툴은 로컬 DB만 조회하므로, 미리 받아두면
 * 주행 중 이름만으로 빠르게 발신 대상을 찾을 수 있다.
 */
@HiltViewModel
class ContactsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val contactDao: ContactDao
) : ViewModel() {

    data class UiState(
        val contacts: List<ContactEntity> = emptyList(),
        val isImporting: Boolean = false,
        val lastImportAdded: Int? = null,
        val error: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init { loadContacts() }

    fun loadContacts() {
        viewModelScope.launch {
            val list = contactDao.getAll()
            _uiState.update { it.copy(contacts = list) }
        }
    }

    /** 단말 전화번호부를 읽어 신규 연락처만 DB에 추가. READ_CONTACTS 권한 필요. */
    fun importFromDevice() {
        if (_uiState.value.isImporting) return
        _uiState.update { it.copy(isImporting = true, lastImportAdded = null, error = null) }
        viewModelScope.launch {
            try {
                val added = withContext(Dispatchers.IO) {
                    val existing = contactDao.getAll()
                    val existingKeys = existing.map { "${it.name}|${normalize(it.phone)}" }.toHashSet()
                    val device = readDeviceContacts()
                    var count = 0
                    device.forEach { (name, phone) ->
                        val key = "$name|${normalize(phone)}"
                        if (key !in existingKeys) {
                            contactDao.insert(ContactEntity(name = name, phone = phone))
                            existingKeys.add(key)
                            count++
                        }
                    }
                    count
                }
                val list = contactDao.getAll()
                _uiState.update { it.copy(contacts = list, isImporting = false, lastImportAdded = added) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isImporting = false, error = e.message ?: "가져오기 실패") }
            }
        }
    }

    fun addContact(name: String, phone: String) {
        val n = name.trim()
        if (n.isBlank()) return
        viewModelScope.launch {
            contactDao.insert(ContactEntity(name = n, phone = phone.trim().ifBlank { null }))
            loadContacts()
        }
    }

    fun deleteContact(contact: ContactEntity) {
        viewModelScope.launch {
            contactDao.delete(contact)
            loadContacts()
        }
    }

    private fun normalize(phone: String?): String =
        phone?.filter { it.isDigit() } ?: ""

    private fun readDeviceContacts(): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
        )?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            if (nameIdx < 0 || numIdx < 0) return@use
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIdx)?.trim().orEmpty()
                val number = cursor.getString(numIdx)?.trim().orEmpty()
                if (name.isNotBlank() && number.isNotBlank()) {
                    result.add(name to number)
                }
            }
        }
        return result
    }
}
