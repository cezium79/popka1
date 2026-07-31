package com.example.ohrana

import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Properties
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import javax.mail.internet.MimeBodyPart
import javax.mail.Part
import android.util.Log
import java.io.OutputStream
import java.io.ByteArrayOutputStream

class EmailManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("ohrana_prefs", Context.MODE_PRIVATE)
    
    companion object {
        private const val TAG = "EmailManager"
    }
    
    /**
     * Сохраняет SMTP настройки для email
     */
    fun saveSmtpSettings(host: String, port: Int, username: String, password: String) {
        prefs.edit().apply {
            putString("smtp_host", host)
            putInt("smtp_port", port)
            putString("smtp_username", username)
            putString("smtp_password", password)
            apply()
        }
    }
    
    /**
     * Получает SMTP хост
     */
    fun getSmtpHost(): String {
        return prefs.getString("smtp_host", "smtp.yandex.ru") ?: "smtp.yandex.ru"
    }
    
    /**
     * Получает SMTP порт
     */
    fun getSmtpPort(): Int {
        return prefs.getInt("smtp_port", 465) // 465 для SSL
    }
    
    /**
     * Получает SMTP username
     */
    fun getSmtpUsername(): String {
        return prefs.getString("smtp_username", "") ?: ""
    }
    
    /**
     * Получает SMTP пароль
     */
    fun getSmtpPassword(): String {
        return prefs.getString("smtp_password", "") ?: ""
    }
    
    /**
     * Проверяет, настроен ли SMTP
     */
    fun isSmtpConfigured(): Boolean {
        return getSmtpUsername().isNotEmpty() && getSmtpPassword().isNotEmpty()
    }
    
    /**
     * Отправляет email с вложением (отчетом)
     * @param to Email получателя
     * @param subject Тема письма
     * @param body Тело письма
     * @param attachmentHtml HTML-отчет (в виде строки) - опционально
     * @param attachmentPdfBytes PDF-отчет (в виде байтов) - опционально
     * @param attachmentName Имя файла вложения
     * @return true если успешно, false если ошибка
     */
    suspend fun sendEmailWithAttachment(
        to: String,
        subject: String,
        body: String,
        attachmentHtml: String? = null,
        attachmentPdfBytes: ByteArray? = null,
        attachmentName: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val username = getSmtpUsername()
            val password = getSmtpPassword()
            val host = getSmtpHost()
            val port = getSmtpPort()
            
            Log.d(TAG, "SMTP Configuration: host=$host, port=$port, username=$username, to=$to")
            
            if (username.isEmpty() || password.isEmpty()) {
                Log.e(TAG, "SMTP credentials not configured")
                return@withContext false
            }
            
            // Проверяем, что username содержит @ (это должен быть email)
            if (!username.contains("@")) {
                Log.e(TAG, "SMTP username is not a valid email address: $username")
                return@withContext false
            }
            
            // Проверяем, что password не слишком короткий (app password обычно 16 символов)
            if (password.length < 8) {
                Log.w(TAG, "SMTP password seems too short (length: ${password.length})")
                return@withContext false
            }
            
            // Настройки для SMTP сессии
            val props = Properties().apply {
                put("mail.smtp.host", host)
                put("mail.smtp.port", port.toString())
                put("mail.smtp.ssl.enable", "true")
                put("mail.smtp.auth", "true")
                // Убираем starttls для SSL порта 465
                put("mail.smtp.starttls.enable", "false")
                put("mail.smtp.connectiontimeout", "10000")
                put("mail.smtp.timeout", "10000")
            }
            
            // Создаем сессию с аутентификацией
            val session = Session.getInstance(props, object : javax.mail.Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(username, password)
                }
            })
            
            session.debug = true // Включаем отладку SMTP
            
            // Создаем сообщение
            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(username))
                setRecipients(Message.RecipientType.TO, to)
                this.subject = subject
                setContent(body, "text/plain; charset=UTF-8")
                
                // Добавляем вложение (HTML или PDF отчет)
                val multipart = MimeMultipart()
                
                // Текстовое сообщение
                val textPart = MimeBodyPart().apply {
                    setText(body, "UTF-8")
                }
                multipart.addBodyPart(textPart)
                
                // Вложение - HTML или PDF
                if (attachmentPdfBytes != null) {
                    // PDF вложение - используем ByteArrayInputStream для бинарных данных
                    val pdfPart = MimeBodyPart()
                    pdfPart.setContent(attachmentPdfBytes, "application/pdf")
                    pdfPart.fileName = attachmentName
                    pdfPart.disposition = Part.ATTACHMENT
                    multipart.addBodyPart(pdfPart)
                    Log.d(TAG, "Added PDF attachment: $attachmentName (${attachmentPdfBytes.size} bytes, ${attachmentPdfBytes.size / 1024} KB)")
                } else if (attachmentHtml != null) {
                    // HTML вложение
                    val htmlPart = MimeBodyPart().apply {
                        setContent(attachmentHtml, "text/html; charset=UTF-8")
                        fileName = attachmentName
                        disposition = Part.ATTACHMENT
                    }
                    multipart.addBodyPart(htmlPart)
                    val htmlBytes = attachmentHtml.toByteArray(charset = StandardCharsets.UTF_8)
                    Log.d(TAG, "Added HTML attachment: $attachmentName (${htmlBytes.size} bytes, ${htmlBytes.size / 1024} KB)")
                }
                
                setContent(multipart)
            }
            
            // Отправляем сообщение
            Transport.send(message)
            
            Log.i(TAG, "Email sent successfully to $to")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send email: ${e.message}", e)
            false
        }
    }
    
    /**
     * Отправляет email с отчетом и фотографиями (как отдельные вложения)
     * @param to Email получателя
     * @param subject Тема письма
     * @param body Тело письма
     * @param attachmentHtml HTML-отчет (в виде строки) - опционально
     * @param attachmentPdfBytes PDF-отчет (в виде байтов) - опционально
     * @param attachmentName Имя файла отчета
     * @param photoFilePaths List путей к сжатым фотографиям
     * @return true если успешно, false если ошибка
     */
    suspend fun sendEmailWithAttachments(
        to: String,
        subject: String,
        body: String,
        attachmentHtml: String? = null,
        attachmentPdfBytes: ByteArray? = null,
        attachmentName: String,
        photoFilePaths: List<String>
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val username = getSmtpUsername()
            val password = getSmtpPassword()
            val host = getSmtpHost()
            val port = getSmtpPort()
            
            Log.d(TAG, "SMTP with Attachments: host=$host, port=$port, username=$username, to=$to")
            Log.d(TAG, "SMTP with Attachments: $photoFilePaths.size photos, report attachment: $attachmentName")
            
            if (username.isEmpty() || password.isEmpty()) {
                Log.e(TAG, "SMTP credentials not configured")
                return@withContext false
            }
            
            if (!username.contains("@")) {
                Log.e(TAG, "SMTP username is not a valid email address: $username")
                return@withContext false
            }
            
            if (password.length < 8) {
                Log.w(TAG, "SMTP password seems too short (length: ${password.length})")
                return@withContext false
            }
            
            // Настройки для SMTP сессии
            val props = Properties().apply {
                put("mail.smtp.host", host)
                put("mail.smtp.port", port.toString())
                put("mail.smtp.ssl.enable", "true")
                put("mail.smtp.auth", "true")
                put("mail.smtp.starttls.enable", "false")
                put("mail.smtp.connectiontimeout", "30000")
                put("mail.smtp.timeout", "30000")
                put("mail.smtp.writetimeout", "30000")
            }
            
            // Создаем сессию с аутентификацией
            val session = Session.getInstance(props, object : javax.mail.Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(username, password)
                }
            })
            
            session.debug = true
            
            // Создаем сообщение
            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(username))
                setRecipients(Message.RecipientType.TO, to)
                this.subject = subject
                
                // Создаем multipart/mixed для вложений
                val multipart = MimeMultipart("mixed")
                
                // Часть 1: Текстовое тело письма
                val textPart = MimeBodyPart().apply {
                    setText(body, "UTF-8")
                }
                multipart.addBodyPart(textPart)
                
                // Часть 2: Отчет (HTML или PDF)
                if (attachmentPdfBytes != null) {
                    val pdfPart = MimeBodyPart()
                    val byteArrayDataSource = object : javax.activation.DataSource {
                        override fun getInputStream(): java.io.InputStream = ByteArrayInputStream(attachmentPdfBytes)
                        override fun getOutputStream(): OutputStream? {
                            TODO("Not yet implemented")
                        }

                        override fun getContentType(): String = "application/pdf"
                        override fun getName(): String = attachmentName
                    }
                    pdfPart.setDataHandler(javax.activation.DataHandler(byteArrayDataSource))
                    pdfPart.fileName = attachmentName
                    pdfPart.disposition = Part.ATTACHMENT
                    multipart.addBodyPart(pdfPart)
                    Log.d(TAG, "Added PDF attachment: $attachmentName (${attachmentPdfBytes.size / 1024} KB)")
                } else if (attachmentHtml != null) {
                    val htmlPart = MimeBodyPart().apply {
                        val htmlBytes = attachmentHtml.toByteArray(charset = StandardCharsets.UTF_8)
                        val byteArrayDataSource = object : javax.activation.DataSource {
                            override fun getInputStream(): java.io.InputStream = ByteArrayInputStream(htmlBytes)
                            override fun getOutputStream(): OutputStream? {
                                TODO("Not yet implemented")
                            }

                            override fun getContentType(): String = "text/html; charset=UTF-8"
                            override fun getName(): String = attachmentName
                        }
                        setDataHandler(javax.activation.DataHandler(byteArrayDataSource))
                        fileName = attachmentName
                        disposition = Part.ATTACHMENT
                    }
                    multipart.addBodyPart(htmlPart)
                    Log.d(TAG, "Added HTML attachment: $attachmentName (${attachmentHtml.length / 1024} KB)")
                }
                
                // Часть 3: Фотографии как отдельные вложения
                photoFilePaths.forEachIndexed { index, photoPath ->
                    try {
                        val photoFile = File(photoPath)
                        if (!photoFile.exists()) {
                            Log.w(TAG, "Photo file not found for attachment: $photoPath")
                            return@forEachIndexed
                        }
                        
                        val photoPart = MimeBodyPart()
                        val fileDataSource = javax.activation.FileDataSource(photoFile)
                        photoPart.setDataHandler(javax.activation.DataHandler(fileDataSource))
                        photoPart.fileName = photoFile.name
                        photoPart.disposition = Part.ATTACHMENT
                        multipart.addBodyPart(photoPart)
                        
                        val photoBytes = photoFile.readBytes()
                        Log.d(TAG, "Added photo attachment #$index: ${photoFile.name} (${photoBytes.size / 1024} KB)")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to add photo attachment $photoPath: ${e.message}", e)
                    }
                }
                
                setContent(multipart)
            }
            
            // Отправляем сообщение
            Transport.send(message)
            
            Log.i(TAG, "Email with ${photoFilePaths.size} attachments sent successfully to $to")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send email with attachments: ${e.message}", e)
            false
        }
    }
    
    /**
     * Отправляет email без вложения (только текст)
     * @param to Email получателя
     * @param subject Тема письма
     * @param body Тело письма
     * @return true если успешно, false если ошибка
     */
    suspend fun sendSimpleEmail(to: String, subject: String, body: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val username = getSmtpUsername()
            val password = getSmtpPassword()
            val host = getSmtpHost()
            val port = getSmtpPort()
            
            Log.d(TAG, "SMTP Configuration: host=$host, port=$port, username=$username, to=$to")
            
            if (username.isEmpty() || password.isEmpty()) {
                Log.e(TAG, "SMTP credentials not configured")
                return@withContext false
            }
            
            // Проверяем, что username содержит @ (это должен быть email)
            if (!username.contains("@")) {
                Log.e(TAG, "SMTP username is not a valid email address: $username")
                return@withContext false
            }
            
            // Проверяем, что password не слишком короткий (app password обычно 16 символов)
            if (password.length < 8) {
                Log.w(TAG, "SMTP password seems too short (length: ${password.length})")
                return@withContext false
            }
            
            // Настройки для SMTP сессии
            val props = Properties().apply {
                put("mail.smtp.host", host)
                put("mail.smtp.port", port.toString())
                put("mail.smtp.ssl.enable", "true")
                put("mail.smtp.auth", "true")
                // Убираем starttls для SSL порта 465
                put("mail.smtp.starttls.enable", "false")
                put("mail.smtp.connectiontimeout", "10000")
                put("mail.smtp.timeout", "10000")
            }
            
            // Создаем сессию с аутентификацией
            val session = Session.getInstance(props, object : javax.mail.Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(username, password)
                }
            })
            
            session.debug = true // Включаем отладку SMTP
            
            // Создаем сообщение
            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(username))
                setRecipients(Message.RecipientType.TO, to)
                this.subject = subject
                setText(body, "UTF-8")
            }
            
            // Отправляем сообщение
            Transport.send(message)
            
            Log.i(TAG, "Simple email sent successfully to $to")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send simple email: ${e.message}", e)
            false
        }
    }
}
