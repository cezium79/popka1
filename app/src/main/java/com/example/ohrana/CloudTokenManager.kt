package com.example.ohrana

import android.content.Context

/**
 * Класс для управления списком токенов Yandex Cloud и Disk
 */
class CloudTokenManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("ohrana_prefs", Context.MODE_PRIVATE)
    
    companion object {
        private const val TOKENS_LIST_KEY = "cloud_tokens_list"
        private const val DEFAULT_TOKEN_KEY = "default_cloud_token"
    }
    
    /**
     * Структура токена
     */
    data class TokenInfo(
        val name: String,
        val token: String,
        val path: String = ""
    )
    
    /**
     * Добавляет токен в список
     */
    fun addToken(tokenInfo: TokenInfo) {
        val tokens = loadTokens().toMutableList()
        // Удаляем дубликаты по имени
        val filtered = tokens.filter { it.name != tokenInfo.name }.toMutableList()
        filtered.add(tokenInfo)
        saveTokens(filtered)
    }
    
    /**
     * Загружает список токенов
     */
    fun loadTokens(): List<TokenInfo> {
        val jsonString = prefs.getString(TOKENS_LIST_KEY, null)
        if (jsonString.isNullOrBlank()) return emptyList()
        
        return try {
            val jsonArray = org.json.JSONArray(jsonString)
            List(jsonArray.length()) { index ->
                val jsonObject = jsonArray.getJSONObject(index)
                TokenInfo(
                    name = jsonObject.optString("name", ""),
                    token = jsonObject.optString("token", ""),
                    path = jsonObject.optString("path", "")
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    /**
     * Сохраняет список токенов
     */
    private fun saveTokens(tokens: List<TokenInfo>) {
        val jsonArray = org.json.JSONArray()
        tokens.forEach { tokenInfo ->
            val jsonObject = org.json.JSONObject()
            jsonObject.put("name", tokenInfo.name)
            jsonObject.put("token", tokenInfo.token)
            jsonObject.put("path", tokenInfo.path)
            jsonArray.put(jsonObject)
        }
        prefs.edit().putString(TOKENS_LIST_KEY, jsonArray.toString()).apply()
    }
    
    /**
     * Удаляет токен по имени
     */
    fun removeToken(name: String) {
        val tokens = loadTokens().toMutableList()
        val filtered = tokens.filter { it.name != name }
        saveTokens(filtered)
    }
    
    /**
     * Обновляет токен
     */
    fun updateToken(tokenInfo: TokenInfo) {
        removeToken(tokenInfo.name)
        addToken(tokenInfo)
    }
    
    /**
     * Устанавливает токен по умолчанию по имени
     */
    fun setDefaultToken(name: String) {
        val tokens = loadTokens()
        val defaultToken = tokens.find { it.name == name }
        if (defaultToken != null) {
            prefs.edit().putString(DEFAULT_TOKEN_KEY, name).apply()
        }
    }
    
    /**
     * Получает имя токена по умолчанию
     */
    fun getDefaultTokenName(): String? {
        return prefs.getString(DEFAULT_TOKEN_KEY, null)
    }
    
    /**
     * Получает токен по умолчанию
     */
    fun getDefaultToken(): TokenInfo? {
        val name = getDefaultTokenName()
        return if (name != null) {
            loadTokens().find { it.name == name }
        } else {
            null
        }
    }
    
    /**
     * Получает токен по имени
     */
    fun getTokenByName(name: String): TokenInfo? {
        return loadTokens().find { it.name == name }
    }
    
    /**
     * Инициализирует список токенов при первом запуске
     */
    fun initializeDefaultTokens() {
        // Проверяем, инициализированы ли уже токены
        val tokens = loadTokens()
        if (tokens.isEmpty()) {
            // Добавляем стандартный токен
            addToken(TokenInfo(
                name = "Yandex Disk Main",
                token = "y0__wgBELu0sPQCGNDoRCDVkvCRGJAAvoTD6mFNSfWDpTMFBkRj_EVO",
                path = "Ohrana/Reports"
            ))
            setDefaultToken("Yandex Disk Main")
        }
    }
}
