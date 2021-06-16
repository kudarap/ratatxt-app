package com.chiligarlic.ratatxt;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;

public class LoginActivity extends AppCompatActivity {
    private ProgressBar progressBar;
    private Button loginButton;
    private EditText urlInput;
    private EditText usernameInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Hide the status bar.
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }

        // Set View values.
        progressBar = findViewById(R.id.progressBar);
        loginButton = findViewById(R.id.button_login);
        urlInput = findViewById(R.id.text_host);
        usernameInput = findViewById(R.id.text_username);

        AppController appCtl = AppController.getInstance();
        // Clear previous auth data.
        appCtl.clearAuth();

        // Set previous form value.
        urlInput.setText(appCtl.getApiUrl());
        usernameInput.setText(appCtl.getApiUser());
    }

    public void handleLogin(View view) {
        view.setEnabled(false);

        // Start login process.
        EditText passInput = findViewById(R.id.text_password);
        requestLogin(
                urlInput.getText().toString(),
                usernameInput.getText().toString(),
                passInput.getText().toString()
        );
    }

    private void requestLogin(String host, String user, String pass) {
        final AppController appCtl = AppController.getInstance();

        showLoader(true);
        appCtl.saveApiSettings(host, user);
        appCtl.getRatatxt().authenticate(user, pass, data -> {
            runOnUiThread(() -> {
                // Save Auth value.
                appCtl.saveAppKey(Ratatxt.AppKey.parse(data));

                // Proceed to DashboardActivity.
                Intent intent = new Intent(getApplicationContext(), DashboardActivity.class);
                startActivity(intent);

                // Prevent user to back in this activity using back navigation button.
                finish();
                showLoader(false);
            });
        }, errResp -> {
            System.out.println("errResp");
            System.out.println("errResp " + errResp.getMessage());
            System.out.println("errResp " + errResp.getType());

            runOnUiThread(() -> {
                Snackbar.make(loginButton, errResp.getMessage(), Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();

                loginButton.setEnabled(true);
                showLoader(false);
            });
        });


    }

    private void showLoader(boolean show) {
        progressBar.setVisibility(show ? ProgressBar.VISIBLE : ProgressBar.INVISIBLE);
    }
}
