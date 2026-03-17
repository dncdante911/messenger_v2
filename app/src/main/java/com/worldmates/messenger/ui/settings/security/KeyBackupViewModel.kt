package com.worldmates.messenger.ui.settings.security

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.worldmates.messenger.network.NodeRetrofitClient
import com.worldmates.messenger.utils.signal.KeyBackupCrypto
import com.worldmates.messenger.utils.signal.SignalKeyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class KeyBackupViewModel(app: Application) : AndroidViewModel(app) {

    private val keyStore = SignalKeyStore(app)

    sealed class UiState {
        object Idle          : UiState()
        object Loading       : UiState()
        data class Success(val message: String) : UiState()
        data class Error(val message: String)   : UiState()
    }

    data class BackupStatus(
        val exists: Boolean     = false,
        val updatedAt: Long     = 0L,
    )

    private val _state        = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state

    private val _backupStatus = MutableStateFlow(BackupStatus())
    val backupStatus: StateFlow<BackupStatus> = _backupStatus

    fun loadBackupStatus() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            try {
                val resp = withContext(Dispatchers.IO) {
                    NodeRetrofitClient.api.downloadKeyBackup()
                }
                _backupStatus.value = BackupStatus(
                    exists    = resp.backup != null,
                    updatedAt = resp.backup?.updatedAt ?: 0L,
                )
                _state.value = UiState.Idle
            } catch (e: Exception) {
                _state.value = UiState.Idle // non-fatal — just show "no backup"
            }
        }
    }

    /** Create or update the encrypted backup with [password]. Runs PBKDF2 on IO. */
    fun createBackup(password: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            _state.value = UiState.Loading
            try {
                val plaintext = withContext(Dispatchers.Default) {
                    keyStore.exportForBackup()
                } ?: run {
                    _state.value = UiState.Error("no_keys")
                    onError("no_keys")
                    return@launch
                }
                val blob = withContext(Dispatchers.Default) {
                    KeyBackupCrypto.encrypt(password, plaintext)
                }
                withContext(Dispatchers.IO) {
                    NodeRetrofitClient.api.uploadKeyBackup(
                        encryptedPayload = blob.ciphertext,
                        salt             = blob.salt,
                        iv               = blob.iv,
                    )
                }
                _backupStatus.value = BackupStatus(exists = true, updatedAt = System.currentTimeMillis() / 1000)
                _state.value = UiState.Idle
                onSuccess("ok")
            } catch (e: Exception) {
                _state.value = UiState.Error(e.message ?: "unknown")
                onError(e.message ?: "unknown")
            }
        }
    }

    /** Download the backup from server and decrypt with [password], then import keys. */
    fun restoreBackup(password: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            _state.value = UiState.Loading
            try {
                val resp = withContext(Dispatchers.IO) {
                    NodeRetrofitClient.api.downloadKeyBackup()
                }
                val payload = resp.backup ?: run {
                    _state.value = UiState.Error("no_backup")
                    onError("no_backup")
                    return@launch
                }
                val blob = KeyBackupCrypto.EncryptedBlob(
                    salt       = payload.salt,
                    iv         = payload.iv,
                    ciphertext = payload.encryptedPayload,
                )
                val plaintext = withContext(Dispatchers.Default) {
                    KeyBackupCrypto.decrypt(password, blob)
                }
                withContext(Dispatchers.Default) {
                    keyStore.importFromBackup(plaintext)
                }
                _state.value = UiState.Idle
                onSuccess("ok")
            } catch (e: javax.crypto.AEADBadTagException) {
                _state.value = UiState.Error("wrong_password")
                onError("wrong_password")
            } catch (e: Exception) {
                _state.value = UiState.Error(e.message ?: "unknown")
                onError(e.message ?: "unknown")
            }
        }
    }

    /** Delete the backup from server. */
    fun deleteBackup(onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            _state.value = UiState.Loading
            try {
                withContext(Dispatchers.IO) { NodeRetrofitClient.api.deleteKeyBackup() }
                _backupStatus.value = BackupStatus(exists = false)
                _state.value = UiState.Idle
                onSuccess()
            } catch (e: Exception) {
                _state.value = UiState.Error(e.message ?: "unknown")
                onError(e.message ?: "unknown")
            }
        }
    }
}
