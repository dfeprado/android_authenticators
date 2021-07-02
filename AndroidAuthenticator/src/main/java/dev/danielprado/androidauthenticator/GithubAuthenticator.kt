package dev.danielprado.githubcli

import android.app.AlertDialog
import android.app.Dialog
import android.net.Uri
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.DialogFragment
import dev.danielprado.androidauthenticator.databinding.LayoutGithubAuthBinding
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.lang.IllegalStateException
import java.net.URL
import javax.net.ssl.HttpsURLConnection

data class GithubAuthToken(val value: String, val scope: List<String>, val type: String)

class GithubAuthenticatorDialog(
    private val state: String,
    private val applicationId: String,
    private val applicationSecret: String,
    private val onProcessFinished: (GitHubAuthResult) -> Unit
): DialogFragment() {
    private val loginUrl = "https://github.com/login/oauth/authorize"
    private val codeExchangeURL = "https://github.com/login/oauth/access_token"
    private var dialog: AlertDialog? = null
    init {
        if (applicationId.trim().isEmpty() || applicationSecret.trim().isEmpty() || state.trim().isEmpty())
            throw IllegalArgumentException("applicationId/applicationSecret/state cannot be empty")
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            val layout = LayoutGithubAuthBinding.inflate(requireActivity().layoutInflater)
            dialog = AlertDialog.Builder(it).setView(layout.root).create()
            layout.webview.apply {
                settings.javaScriptEnabled = true
                setWebViewClient(object : WebViewClient() {
                    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                        super.doUpdateVisitedHistory(view, url, isReload)
                        onUrlChanged(url ?: "")
                    }
                })
                loadUrl("$loginUrl?client_id=$applicationId&state=$state&allow_signup=false")
            }
            dialog
        } ?: throw IllegalStateException("Activity cannot be null")
    }

    private suspend fun requestToken(code: String): Unit {
        // https://stleary.github.io/JSON-java/index.html
        val codeExchangeReqBody = JSONObject(mapOf<String, Any>(
                "client_id" to applicationId,
                "client_secret" to applicationSecret,
                "code" to code
        )).toString()

        return withContext(Dispatchers.IO) {
            val http = (URL(codeExchangeURL).openConnection() as HttpsURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                doOutput = true
                outputStream.write(codeExchangeReqBody.toByteArray())
            }

            val result =
                if (http.responseCode == 200)
                    Parser().parseCodeExchangeResponse(http.inputStream)
                else
                    GitHubAuthResult.Error(http.responseMessage, null)

            withContext(Dispatchers.Main) {
                onProcessFinished(result)
            }
        }
    }

    private fun onUrlChanged(url: String) {
        if (url.isEmpty())
            return

        if (url.contains("code=") && url.contains("state=")) {
            val uri = Uri.parse(url)
            val state = uri.getQueryParameter("state")
            try {
                if (this.state.equals(state))
                    CoroutineScope(Dispatchers.Main).launch {
                        requestToken(uri.getQueryParameter("code")!!)
                    }
                else
                    onProcessFinished(GitHubAuthResult.Error(
                            "State doesn't match: obtained \"$state\" but required \"${this.state}\"",
                            url
                    ))
            }
            finally {
                dialog?.dismiss()
            }
        }
        else if (url.contains("error=")) {
            val uri = Uri.parse(url)
            val error = uri.getQueryParameter("error") ?: "Error"
            val description = uri.getQueryParameter("error_description") ?: "Unknown reason"
            try {
                onProcessFinished(GitHubAuthResult.Error("$error: $description", url))
            }
            finally {
                dialog?.dismiss()
            }

        }
    }
}

sealed class GitHubAuthResult {
    data class Success(val token: GithubAuthToken): GitHubAuthResult()
    data class Error(val reason: String, val obtainedResponse: String?): GitHubAuthResult()
}

private class Parser {
    private fun readStream(stream: InputStream): String {
        val reader = BufferedReader(InputStreamReader(stream))
        val buffer = StringBuffer()
        var inputLine = reader.readLine()
        while (inputLine != null) {
            buffer.append(inputLine)
            inputLine = reader.readLine()
        }
        stream.close()
        return buffer.toString()
    }

    fun parseCodeExchangeResponse(stream: InputStream) = parseCodeExchangeResponse(readStream(stream))

    fun parseCodeExchangeResponse(json: String): GitHubAuthResult {
        val obj = JSONObject(json)
        return (
            if (obj.has("access_token"))
                GitHubAuthResult.Success(GithubAuthToken(
                        obj["access_token"] as String,
                        (obj["scope"] as String).split(","),
                        obj["token_type"] as String
                ))
            else
                GitHubAuthResult.Error(
                        obj["error"] as String,
                        json
                )
        )
    }
}