package dev.danielprado.androidauthenticatorsapp

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import dev.danielprado.githubcli.GitHubAuthResult
import dev.danielprado.githubcli.GithubAuthenticatorDialog
import dev.danielprado.githubcli.GithubAuthToken

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    private fun onLoginProcessEnd(result: GitHubAuthResult) {
        if (result is GitHubAuthResult.Success)
            Log.i("GITHUB_LOGIN", "Success: ${result.token.value}")
        else
            Log.i("GITHUT_LOGIN", "Error: ${(result as GitHubAuthResult.Error).reason}")
    }

    fun onLoginClick(view: View) {
        // replace GH_APP_ID/GH_APP_SECRET with your informations
        GithubAuthenticatorDialog(
                "AndroidGithubAuthenticatorTest",
                GH_APP_ID,
                GH_APP_SECRET,
                ::onLoginProcessEnd
        ).show(supportFragmentManager, null);
    }
}