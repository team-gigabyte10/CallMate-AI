package com.example.presentation.contacts

import android.app.Application
import android.provider.ContactsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.communication.CallManager
import com.example.communication.WhatsAppManager
import com.example.data.local.CallMateDatabase
import com.example.data.local.entities.ContactEntity
import com.example.data.repository.CallMateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class ContactsViewModel(application: Application) : AndroidViewModel(application) {

    private val database = CallMateDatabase.getDatabase(application)
    val repository = CallMateRepository(database)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _showOnlyFavorites = MutableStateFlow(false)
    val showOnlyFavorites: StateFlow<Boolean> = _showOnlyFavorites.asStateFlow()

    private val _importStatus = MutableStateFlow<String?>(null)
    val importStatus: StateFlow<String?> = _importStatus.asStateFlow()

    val contacts: StateFlow<List<ContactEntity>> = combine(
        repository.allContacts,
        _searchQuery,
        _showOnlyFavorites
    ) { all, query, onlyFavs ->
        all.filter { contact ->
            val favMatch = if (onlyFavs) contact.isFavorite else true
            val queryMatch = if (query.isBlank()) true else {
                contact.name.contains(query, ignoreCase = true) ||
                        (contact.nickname?.contains(query, ignoreCase = true) == true) ||
                        contact.phoneNumber.contains(query)
            }
            favMatch && queryMatch
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun toggleFavoritesFilter() {
        _showOnlyFavorites.value = !_showOnlyFavorites.value
    }

    fun toggleContactFavorite(contact: ContactEntity) {
        viewModelScope.launch {
            repository.updateContact(contact.copy(isFavorite = !contact.isFavorite))
        }
    }

    fun updateNickname(contact: ContactEntity, newNickname: String) {
        viewModelScope.launch {
            repository.updateContact(contact.copy(nickname = newNickname.ifBlank { null }))
        }
    }

    fun saveContact(name: String, phoneNumber: String, nickname: String?, isFavorite: Boolean) {
        viewModelScope.launch {
            val colors = listOf(0xFF6366F1, 0xFF06B6D4, 0xFF10B981, 0xFFF59E0B, 0xFFEC4899, 0xFF8B5CF6)
            val randomColor = colors.random()
            val newContact = ContactEntity(
                id = UUID.randomUUID().toString(),
                name = name,
                phoneNumber = phoneNumber,
                nickname = nickname?.ifBlank { null },
                isFavorite = isFavorite,
                avatarColor = randomColor
            )
            repository.insertContact(newContact)
        }
    }

    fun deleteContact(contact: ContactEntity) {
        viewModelScope.launch {
            repository.deleteContact(contact)
        }
    }

    fun makeCall(phoneNumber: String) {
        CallManager.launchDialer(getApplication(), phoneNumber)
    }

    fun openWhatsApp(phoneNumber: String) {
        WhatsAppManager.launchWhatsApp(getApplication(), phoneNumber, "Hello!")
    }

    fun importDeviceContacts() {
        viewModelScope.launch {
            _importStatus.value = "Reading device contacts..."
            try {
                val imported = withContext(Dispatchers.IO) {
                    val list = mutableListOf<ContactEntity>()
                    val cr = getApplication<Application>().contentResolver
                    val cursor = cr.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        arrayOf(
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                            ContactsContract.CommonDataKinds.Phone.NUMBER
                        ),
                        null,
                        null,
                        "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
                    )

                    cursor?.use {
                        val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                        val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        val colors = listOf(0xFF6366F1, 0xFF06B6D4, 0xFF10B981, 0xFFF59E0B, 0xFFEC4899, 0xFF8B5CF6)

                        while (it.moveToNext() && list.size < 50) {
                            val name = it.getString(nameIndex) ?: continue
                            val number = it.getString(numberIndex) ?: continue
                            list.add(
                                ContactEntity(
                                    id = UUID.randomUUID().toString(),
                                    name = name,
                                    phoneNumber = number,
                                    avatarColor = colors.random()
                                )
                            )
                        }
                    }
                    list
                }

                if (imported.isNotEmpty()) {
                    for (c in imported) {
                        repository.insertContact(c)
                    }
                    _importStatus.value = "Imported ${imported.size} contacts successfully!"
                } else {
                    _importStatus.value = "No device contacts found or permission not granted."
                }
            } catch (e: Exception) {
                _importStatus.value = "Import failed: ${e.message}"
            }
        }
    }

    fun clearStatus() {
        _importStatus.value = null
    }
}
