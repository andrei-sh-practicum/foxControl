package com.andrew.foxcontrol.data.repository

import com.andrew.foxcontrol.core.security.SecretCipher
import com.andrew.foxcontrol.data.local.dao.EmailRecipientDao
import com.andrew.foxcontrol.data.local.dao.EmailSettingsDao
import com.andrew.foxcontrol.data.local.dao.ReportSendLogDao
import com.andrew.foxcontrol.data.local.entity.EmailSettingsEntity
import com.andrew.foxcontrol.data.local.entity.EmailSettingsKeys
import com.andrew.foxcontrol.core.tracking.TrackingLogStorage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.After
import org.junit.Before
import org.junit.Test

/** B-15: the SMTP app password is stored encrypted, other settings as plain text. */
class EmailRepositoryTest {

    /** Reversible fake: "enc:" + reversed text. */
    private class FakeCipher : SecretCipher {
        var failDecrypt = false
        override fun isEncrypted(stored: String) = stored.startsWith("enc:")
        override fun encrypt(plain: String) = "enc:" + plain.reversed()
        override fun decrypt(stored: String): String {
            if (failDecrypt) throw IllegalStateException("key lost")
            return stored.removePrefix("enc:").reversed()
        }
    }

    private lateinit var settingsDao: EmailSettingsDao
    private val cipher = FakeCipher()
    private lateinit var repository: EmailRepository

    @Before
    fun setUp() {
        // TrackingLogStorage falls back to android.util.Log, which isn't available on the JVM
        mockkObject(TrackingLogStorage)
        every { TrackingLogStorage.add(any(), any()) } just runs
        settingsDao = mockk()
        coEvery { settingsDao.upsertSetting(any()) } returns Unit
        repository = EmailRepository(mockk<EmailRecipientDao>(), settingsDao, mockk<ReportSendLogDao>(), cipher)
    }

    @After
    fun tearDown() = unmockkAll()

    @Test
    fun `password is encrypted on save, other settings are not`() = runBlocking {
        val saved = mutableListOf<EmailSettingsEntity>()
        coEvery { settingsDao.upsertSetting(capture(saved)) } returns Unit

        repository.saveSetting(EmailSettingsKeys.SMTP_APP_PASSWORD, "secret")
        repository.saveSetting(EmailSettingsKeys.SMTP_HOST, "smtp.example.com")

        assertEquals("enc:terces", saved[0].value)
        assertEquals("smtp.example.com", saved[1].value)
    }

    @Test
    fun `encrypted password is decrypted on read`() = runBlocking {
        coEvery { settingsDao.getAllSettings() } returns listOf(
            EmailSettingsEntity(EmailSettingsKeys.SMTP_APP_PASSWORD, "enc:terces"),
            EmailSettingsEntity(EmailSettingsKeys.SMTP_HOST, "smtp.example.com")
        )

        val settings = repository.getAllSettings()

        assertEquals("secret", settings[EmailSettingsKeys.SMTP_APP_PASSWORD])
        assertEquals("smtp.example.com", settings[EmailSettingsKeys.SMTP_HOST])
    }

    @Test
    fun `legacy plain password is returned and migrated to encrypted`() = runBlocking {
        coEvery { settingsDao.getAllSettings() } returns listOf(
            EmailSettingsEntity(EmailSettingsKeys.SMTP_APP_PASSWORD, "secret")
        )
        val saved = slot<EmailSettingsEntity>()
        coEvery { settingsDao.upsertSetting(capture(saved)) } returns Unit

        assertEquals("secret", repository.getAllSettings()[EmailSettingsKeys.SMTP_APP_PASSWORD])
        assertEquals("enc:terces", saved.captured.value)
    }

    @Test
    fun `undecryptable password is treated as not set`() = runBlocking {
        cipher.failDecrypt = true
        coEvery { settingsDao.getAllSettings() } returns listOf(
            EmailSettingsEntity(EmailSettingsKeys.SMTP_APP_PASSWORD, "enc:terces")
        )

        assertFalse(repository.getAllSettings().containsKey(EmailSettingsKeys.SMTP_APP_PASSWORD))
        coVerify(exactly = 0) { settingsDao.upsertSetting(any()) }
    }
}
