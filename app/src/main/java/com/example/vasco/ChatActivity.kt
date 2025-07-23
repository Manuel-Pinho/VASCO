package com.example.vasco

import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class ChatActivity : ToolbarActivity() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val messageHistory = mutableListOf<JSONObject>()
    private val mensagens = mutableListOf<Mensagem>()
    private lateinit var recyclerMensagens: RecyclerView
    private lateinit var adapter: MensagemAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chatbox)
        setupToolbarClicks()

        val etQuestion = findViewById<EditText>(R.id.etQuestion)
        val btnSubmit = findViewById<ImageButton>(R.id.btnSubmit)

        recyclerMensagens = findViewById(R.id.recyclerMensagens)
        adapter = MensagemAdapter(mensagens)
        recyclerMensagens.adapter = adapter
        recyclerMensagens.layoutManager = LinearLayoutManager(this)

        btnSubmit.setOnClickListener {
            val question = etQuestion.text.toString().trim()
            if (question.isEmpty()) return@setOnClickListener

            mensagens.add(Mensagem(question, "user"))
            adapter.notifyItemInserted(mensagens.size - 1)
            recyclerMensagens.scrollToPosition(mensagens.size - 1)

            val userMessage = JSONObject().apply {
                put("role", "user")
                put("content", question)
            }
            messageHistory.add(userMessage)
            etQuestion.text.clear()

            getResponseWithRetry(2, question) { response ->
                runOnUiThread {
                    mensagens.add(Mensagem(response, "vasco"))
                    adapter.notifyItemInserted(mensagens.size - 1)
                    recyclerMensagens.scrollToPosition(mensagens.size - 1)
                }

                val assistantMessage = JSONObject().apply {
                    put("role", "assistant")
                    put("content", response)
                }
                messageHistory.add(assistantMessage)
            }
        }
    }

    private fun getResponseWithRetry(
        retries: Int,
        question: String,
        callback: (String) -> Unit
    ) {
        getResponse(question, { callback(it) }) { error ->
            if (retries > 0) {
                Log.w("API", "Tentativa falhada. A tentar novamente... (${retries - 1} restantes)")
                getResponseWithRetry(retries - 1, question, callback)
            } else {
                callback(error)
            }
        }
    }

    private fun getResponse(
        question: String,
        callback: (String) -> Unit,
        callbackErro: (String) -> Unit
    ) {
        val apiKey = "0f960491-c003-4ac0-a99f-4c9e5b7c4e0d"
        val url = "https://api.kluster.ai/v1/chat/completions"

        val systemPrompt = JSONObject().apply {
            put("role", "system")
            put("content", "O teu nome é VASCO. És um assistente digital simpático e dedicado a ajudar com dúvidas de saúde e medicamentos. Fala sempre em português claro, simples e acessível a todas as pessoas. Tenta escrever respostas pequenas e simples de ler, mas tenta manter a informação relevante.")
        }

        val messagesArray = JSONArray().apply {
            put(systemPrompt)
            messageHistory.forEach { put(it) }
        }

        val requestJson = JSONObject().apply {
            put("model", "klusterai/Meta-Llama-3.3-70B-Instruct-Turbo")
            put("max_completion_tokens", 4000)
            put("temperature", 0.6)
            put("top_p", 1)
            put("messages", messagesArray)
        }

        val requestBody = requestJson.toString().toRequestBody("application/json".toMediaTypeOrNull())

        val request = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("API", "Falha de ligação: ${e.message}", e)
                callbackErro("Erro de ligação à IA.")
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    Log.e("API", "Erro HTTP: ${response.code}")
                    callbackErro("Erro da IA: ${response.code}")
                    return
                }

                val body = response.body?.string()
                if (body != null) {
                    try {
                        val jsonObject = JSONObject(body)
                        val choices = jsonObject.getJSONArray("choices")
                        val message = choices.getJSONObject(0).getJSONObject("message")
                        val reply = message.getString("content").trim()
                        callback(reply)
                    } catch (e: Exception) {
                        Log.e("API", "Erro ao interpretar JSON", e)
                        callbackErro("Erro ao processar resposta da IA.")
                    }
                } else {
                    callbackErro("Sem resposta da IA.")
                }
            }
        })
    }
}
