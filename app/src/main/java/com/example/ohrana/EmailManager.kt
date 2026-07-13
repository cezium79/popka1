package com.example.ohrana

import android.content.Context
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
     * @param attachmentHtml HTML-отчет (в виде строки)
     * @param attachmentName Имя файла вложения
     * @return true если успешно, false если ошибка
     */
    suspend fun sendEmailWithAttachment(
        to: String,
        subject: String,
        body: String,
        attachmentHtml: String,
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
                Toast.makeText(context, "Настройте SMTP в настройках", Toast.LENGTH_LONG).show()
                return@withContext false
            }
            
            // Проверяем, что username содержит @ (это должен быть email)
            if (!username.contains("@")) {
                Log.e(TAG, "SMTP username is not a valid email address: $username")
                Toast.makeText(context, "SMTP username должен быть email адресом", Toast.LENGTH_LONG).show()
                return@withContext false
            }
            
            // Проверяем, что password не слишком короткий (app password обычно 16 символов)
            if (password.length < 8) {
                Log.w(TAG, "SMTP password seems too short (length: ${password.length})")
                Toast.makeText(context, "Проверьте SMTP пароль (должен быть app password)", Toast.LENGTH_LONG).show()
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
                
                // Добавляем вложение (HTML отчет)
                val multipart = MimeMultipart()
                
                // Текстовое сообщение
                val textPart = MimeBodyPart().apply {
                    setText(body, "UTF-8")
                }
                multipart.addBodyPart(textPart)
                
                // HTML вложение
                val htmlPart = MimeBodyPart().apply {
                    setContent(attachmentHtml, "text/html; charset=UTF-8")
                    fileName = attachmentName
                    disposition = Part.ATTACHMENT
                }
                multipart.addBodyPart(htmlPart)
                
                setContent(multipart)
            }
            
            // Отправляем сообщение
            Transport.send(message)
            
            Log.i(TAG, "Email sent successfully to $to")
            Toast.makeText(context, "Письмо отправлено!", Toast.LENGTH_SHORT).show()
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send email: ${e.message}", e)
            // Добавляем более подробное сообщение об ошибке
            val errorMsg = when {
                e.message?.contains("535") == true -> "Ошибка аутентификации: проверьте SMTP username и app password"
                e.message?.contains("465") == true -> "Ошибка подключения к порту 465: проверьте настройки"
                else -> "Ошибка отправки письма: ${e.message}"
            }
            Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
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
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Настройте SMTP в настройках", Toast.LENGTH_LONG).show()
                }
                return@withContext false
            }
            
            // Проверяем, что username содержит @ (это должен быть email)
            if (!username.contains("@")) {
                Log.e(TAG, "SMTP username is not a valid email address: $username")
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "SMTP username должен быть email адресом", Toast.LENGTH_LONG).show()
                }
                return@withContext false
            }
            
            // Проверяем, что password не слишком короткий (app password обычно 16 символов)
            if (password.length < 8) {
                Log.w(TAG, "SMTP password seems too short (length: ${password.length})")
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Проверьте SMTP пароль (должен быть app password)", Toast.LENGTH_LONG).show()
                }
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
            
            withContext(Dispatchers.Main) {
                Log.i(TAG, "Simple email sent successfully to $to")
                Toast.makeText(context, "Письмо отправлено!", Toast.LENGTH_SHORT).show()
            }
            true
            
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Log.e(TAG, "Failed to send simple email: ${e.message}", e)
                // Добавляем более подробное сообщение об ошибке
                val errorMsg = when {
                    e.message?.contains("535") == true -> "Ошибка аутентификации: проверьте SMTP username и app password"
                    e.message?.contains("465") == true -> "Ошибка подключения к порту 465: проверьте настройки"
                    else -> "Ошибка отправки письма: ${e.message}"
                }
                Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
            }
            false
        }
    }
}
