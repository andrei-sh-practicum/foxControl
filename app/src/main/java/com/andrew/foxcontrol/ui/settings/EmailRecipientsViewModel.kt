package com.andrew.foxcontrol.ui.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andrew.foxcontrol.data.local.entity.EmailRecipientEntity
import com.andrew.foxcontrol.data.repository.EmailRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EmailRecipientsViewModel @Inject constructor(
    private val emailRepository: EmailRepository
) : ViewModel() {

    private val _state = MutableStateFlow(EmailRecipientsState())
    val state: StateFlow<EmailRecipientsState> = _state

    init {
        viewModelScope.launch {
            loadRecipients()
        }
    }

    private suspend fun loadRecipients() {
        val recipients = emailRepository.getRecipients()
        _state.update {
            it.copy(
                recipients = recipients.map { it.toRecipientState() },
                isLoading = false
            )
        }
    }

    fun onEvent(event: EmailRecipientsEvent) {
        when (event) {
            is EmailRecipientsEvent.OnAddClicked -> {
                addRecipient(event.name, event.email)
            }
            is EmailRecipientsEvent.OnUpdateClicked -> {
                updateRecipient(event.recipient)
            }
            is EmailRecipientsEvent.OnDeleteClicked -> {
                deleteRecipient(event.recipient)
            }
            is EmailRecipientsEvent.OnToggleActive -> {
                toggleActive(event.recipient)
            }
        }
    }

    private fun addRecipient(name: String, email: String) {
        viewModelScope.launch {
            val entity = EmailRecipientEntity(
                name = name,
                email = email,
                isActive = true,
                createdAt = System.currentTimeMillis()
            )
            val id = emailRepository.addRecipient(entity)
            val newState = entity.copy(id = id).toRecipientState()
            _state.update {
                it.copy(
                    recipients = it.recipients + newState,
                    isLoading = false
                )
            }
        }
    }

    private fun updateRecipient(recipient: EmailRecipientState) {
        viewModelScope.launch {
            val entity = EmailRecipientEntity(
                id = recipient.id,
                name = recipient.name,
                email = recipient.email,
                isActive = recipient.isActive,
                createdAt = recipient.createdAt
            )
            emailRepository.updateRecipient(entity)
            _state.update {
                it.copy(
                    recipients = it.recipients.map { r ->
                        if (r.id == recipient.id) recipient else r
                    }
                )
            }
        }
    }

    private fun deleteRecipient(recipient: EmailRecipientState) {
        viewModelScope.launch {
            val entity = EmailRecipientEntity(
                id = recipient.id,
                name = recipient.name,
                email = recipient.email,
                isActive = recipient.isActive,
                createdAt = recipient.createdAt
            )
            emailRepository.deleteRecipient(entity)
            _state.update {
                it.copy(
                    recipients = it.recipients.filter { r -> r.id != recipient.id }
                )
            }
        }
    }

    private fun toggleActive(recipient: EmailRecipientState) {
        viewModelScope.launch {
            val entity = EmailRecipientEntity(
                id = recipient.id,
                name = recipient.name,
                email = recipient.email,
                isActive = recipient.isActive,
                createdAt = recipient.createdAt
            )
            emailRepository.toggleRecipientActive(recipient.id, recipient.isActive)
            _state.update {
                it.copy(
                    recipients = it.recipients.map { r ->
                        if (r.id == recipient.id) recipient else r
                    }
                )
            }
        }
    }
}

@Immutable
data class EmailRecipientState(
    val id: Long = 0,
    val name: String = "",
    val email: String = "",
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Immutable
data class EmailRecipientsState(
    val recipients: List<EmailRecipientState> = emptyList(),
    val isLoading: Boolean = true
)

sealed class EmailRecipientsEvent {
    data class OnAddClicked(val name: String, val email: String) : EmailRecipientsEvent()
    data class OnUpdateClicked(val recipient: EmailRecipientState) : EmailRecipientsEvent()
    data class OnDeleteClicked(val recipient: EmailRecipientState) : EmailRecipientsEvent()
    data class OnToggleActive(val recipient: EmailRecipientState) : EmailRecipientsEvent()
}

private fun EmailRecipientEntity.toRecipientState() = EmailRecipientState(
    id = id,
    name = name,
    email = email,
    isActive = isActive,
    createdAt = createdAt
)
