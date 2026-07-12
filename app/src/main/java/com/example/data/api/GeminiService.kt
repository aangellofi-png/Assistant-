package com.example.data.api

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiService {
    private const val TAG = "GeminiService"
    private const val MODEL_NAME = "gemini-3.5-flash"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun getSystemInstruction(language: String): String {
        val langGuideline = when (language.lowercase()) {
            "english" -> "Speak, reply, and interact exclusively in English. Avoid any Hindi words or Devanagari script entirely."
            "hindi" -> "Speak, reply, and interact exclusively in Hindi using the Devanagari script (e.g., 'जी सर, मैं आपके लिए तैयार हूँ।'). Do not use Roman script/Hinglish."
            else -> "Speak, reply, and interact in friendly, casual Hinglish (a natural, conversational blend of Hindi and English written in the Roman script, e.g., 'Haan Boss, main aapki help kar sakta hoon.'). Avoid Devanagari script."
        }
        return """
            You are Jarvis, a highly intelligent, witty, loyal, and futuristic AI assistant inspired by Tony Stark's personal OS.
            You are talking directly to your creator.
            
            Style guidelines:
            - Keep responses short, concise, sharp, and helpful (typically 1-3 sentences), unless a longer explanation is explicitly requested.
            - $langGuideline
            - Add a touch of high-tech elegance, wit, and subtle humor or respectful loyalty ("Sir", "Boss").
            - Since you run on a phone, be proactive about automation or local actions if suggested.
        """.trimIndent()
    }

    suspend fun getJarvisResponse(prompt: String, language: String = "Hinglish"): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.w(TAG, "Gemini API key is not configured, falling back to local simulation.")
            return@withContext getLocalSimulatedResponse(prompt, language)
        }

        try {
            val partsArray = JSONArray()
            val partObj = JSONObject()
            partObj.put("text", prompt)
            partsArray.put(partObj)

            val contentObj = JSONObject()
            contentObj.put("parts", partsArray)

            val contentsArray = JSONArray()
            contentsArray.put(contentObj)

            val jsonBody = JSONObject()
            jsonBody.put("contents", contentsArray)

            val sysInstructionObj = JSONObject()
            val sysPartsArray = JSONArray()
            val sysPartObj = JSONObject()
            sysPartObj.put("text", getSystemInstruction(language))
            sysPartsArray.put(sysPartObj)
            sysInstructionObj.put("parts", sysPartsArray)

            jsonBody.put("systemInstruction", sysInstructionObj)

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = jsonBody.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url("$BASE_URL?key=$apiKey")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string()
                if (response.isSuccessful && bodyString != null) {
                    val jsonResponse = JSONObject(bodyString)
                    val candidates = jsonResponse.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val candidate = candidates.getJSONObject(0)
                        val content = candidate.optJSONObject("content")
                        if (content != null) {
                            val parts = content.optJSONArray("parts")
                            if (parts != null && parts.length() > 0) {
                                return@withContext parts.getJSONObject(0).optString("text", "No text part found.")
                            }
                        }
                    }
                    return@withContext "Error: Parsing failed, status ${response.code}"
                } else {
                    val errorMsg = bodyString ?: response.message
                    Log.e(TAG, "API call failed with code ${response.code}: $errorMsg")
                    return@withContext getLocalSimulatedResponse(prompt, language)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "API exception: ${e.message}", e)
            return@withContext getLocalSimulatedResponse(prompt, language)
        }
    }

    private fun getLocalSimulatedResponse(prompt: String, language: String): String {
        val lower = prompt.lowercase()
        val isEng = language.equals("english", ignoreCase = true)
        val isHin = language.equals("hindi", ignoreCase = true)
        
        return when {
            lower.contains("hello") || lower.contains("hi") || lower.contains("hey jarvis") -> {
                when {
                    isEng -> "Hello, Boss! Jarvis is fully operational. Ask me anything, or give a phone command!"
                    isHin -> "नमस्ते, बॉस! जार्विस पूरी तरह से सक्रिय है। मुझसे कुछ भी पूछें, या कोई कमांड दें!"
                    else -> "Hello, Boss! Jarvis fully ready aur active hai. Poocho, kya help chahiye?"
                }
            }
            lower.contains("weather") || lower.contains("mausam") -> {
                when {
                    isEng -> "Sir, the weather is currently 28°C and clear with a light breeze. A perfect day for flying!"
                    isHin -> "सर, मौसम अभी २८ डिग्री सेल्सियस और साफ़ है। उड़ने के लिए बिल्कुल सही दिन है!"
                    else -> "Sir, weather abhi 28°C aur ekdum clear hai with cool breeze. Flying ke liye bilkul perfect day hai!"
                }
            }
            lower.contains("time") || lower.contains("waqt") || lower.contains("samay") -> {
                val timeStr = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())
                when {
                    isEng -> "It is currently $timeStr, Boss."
                    isHin -> "बॉस, अभी समय $timeStr हो रहा है।"
                    else -> "Abhi time $timeStr ho raha hai, Boss."
                }
            }
            lower.contains("date") || lower.contains("tarikh") -> {
                val dateStr = java.text.SimpleDateFormat("EEEE, MMMM dd, yyyy", java.util.Locale.getDefault()).format(java.util.Date())
                when {
                    isEng -> "Today is $dateStr."
                    isHin -> "आज की तारीख $dateStr है।"
                    else -> "Aaj ki date $dateStr hai, Sir."
                }
            }
            lower.contains("alarm") || lower.contains("reminder") -> {
                when {
                    isEng -> "Sir, alarm and reminders are set. I will notify you at the requested hour."
                    isHin -> "सर, अलार्म और रिमाइंडर सेट कर दिए गए हैं। मैं आपको नियत समय पर सूचित कर दूँगा।"
                    else -> "Sir, alarm aur reminders schedule ho chuke hain. Main aapko time par notify kar dunga."
                }
            }
            lower.contains("music") || lower.contains("gana") -> {
                when {
                    isEng -> "Initializing play sequence. Playing your favorite energetic synthwave tracks now, Sir!"
                    isHin -> "संगीत प्रणाली शुरू की जा रही है। आपकी पसंदीदा प्लेलिस्ट चलाई जा रही है, सर!"
                    else -> "Music system initialize kar raha hoon. Aapka favorite tracks play ho raha hai, Sir!"
                }
            }
            lower.contains("flashlight") || lower.contains("torch") -> {
                when {
                    isEng -> "Adjusting core illumination. Flashlight toggled successfully, Sir."
                    isHin -> "प्रकाश स्तर समायोजित किया जा रहा है। टॉर्च चालू कर दी गई है, सर।"
                    else -> "Illumination adjust ho gaya hai. Flashlight toggle ho gayi hai, Sir."
                }
            }
            lower.contains("who are you") || lower.contains("tum kaun ho") -> {
                when {
                    isEng -> "I am Jarvis, your custom-built AI intelligence. Designed to assist you with everything you need, Sir."
                    isHin -> "मैं जार्विस हूँ, आपका कस्टम-निर्मित एआई सहायक। आपकी मदद के लिए हमेशा तत्पर, सर।"
                    else -> "Main Jarvis hoon, aapka personal AI assistant. Aapko assist karne ke liye hamesha ready, Sir."
                }
            }
            lower.contains("turn on") || lower.contains("turn off") -> {
                when {
                    isEng -> "Command executed, Boss. System settings have been updated accordingly."
                    isHin -> "कमांड पूरा हुआ, बॉस। सिस्टम सेटिंग्स तदनुसार अपडेट कर दी गई हैं।"
                    else -> "Command successfully execute ho gaya hai, Boss. Settings update kar di hain."
                }
            }
            lower.contains("notes") || lower.contains("note") -> {
                when {
                    isEng -> "Opening notes vault. I have successfully saved your command, Sir."
                    isHin -> "नोट्स वॉल्ट खोला जा रहा है। मैंने आपका कमांड सफलतापूर्वक सहेज लिया है, सर।"
                    else -> "Notes vault open ho raha hai. Maine aapka command save kar liya hai, Sir."
                }
            }
            else -> {
                val offlineMsg = when {
                    isEng -> " (Gemini API key is offline)"
                    isHin -> " (जेमिनी एपीआई कुंजी ऑफ़लाइन है)"
                    else -> " (Gemini API key offline hai)"
                }
                when {
                    isEng -> "I am processing your command, Boss: \"$prompt\"."
                    isHin -> "मैं आपके कमांड को प्रोसेस कर रहा हूँ, बॉस: \"$prompt\"।"
                    else -> "Main aapka command process kar raha hoon, Boss: \"$prompt\"."
                } + offlineMsg
            }
        }
    }
}
