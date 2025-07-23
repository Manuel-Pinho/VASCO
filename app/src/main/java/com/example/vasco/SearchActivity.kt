package com.example.vasco

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
// Se R não for encontrado automaticamente, adiciona:
// import com.example.vasco.R

class SearchActivity : ToolbarActivity() { // Herda de ToolbarActivity

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        setupToolbarClicks()

        // Se a SearchActivity tiver a sua própria toolbar (definida no seu XML)
        // e quiseres que os botões da ToolbarActivity funcionem nela,
        // então setupToolbarClicks() é chamado.
        // Se não houver botões de toolbar visíveis no layout activity_search.xml,
        // a chamada a setupToolbarClicks() não fará nada de visível,
        // mas também não dará erro se os IDs não existirem.

        webView = findViewById(R.id.webViewInfarmed)

        // Configurar a WebView
        webView.settings.javaScriptEnabled = true // Muitos sites precisam de JavaScript

        // URL do Infomed para pesquisa. Verifica se esta é a melhor URL de entrada.
        // Esta é a página inicial do Infomed.
        val infomedUrl = "https://www.infarmed.pt/web/infarmed/servicos-on-line/pesquisa-do-medicamento"
        // Poderias tentar encontrar uma URL que vá mais diretamente para a pesquisa, se existir.
        // Exemplo hipotético: "https://extranet.infarmed.pt/infomed/pesquisa.php" (esta URL é inventada)

        webView.loadUrl(infomedUrl)
    }

    // Para permitir navegar para trás na WebView com o botão "Back" do dispositivo
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}