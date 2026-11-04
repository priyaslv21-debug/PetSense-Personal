package com.example.pet;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.pet.databinding.LoginScreenBinding;

public class LoginActivity extends AppCompatActivity {

    private LoginScreenBinding binding;
    private AuthViewModel authViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = LoginScreenBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        // Session persistence: Check if user is already logged in
        if (authViewModel.getCurrentUser() != null) {
            new Handler(Looper.getMainLooper()).post(this::navigateToMain);
            return;
        }

        setupClickListeners();
        observeViewModel();
    }

    private void setupClickListeners() {
        binding.btnLogin.setOnClickListener(v -> {
            String email = binding.etEmail.getText() != null ? binding.etEmail.getText().toString().trim() : "";
            String password = binding.etPassword.getText() != null ? binding.etPassword.getText().toString().trim() : "";

            if (email.isEmpty()) {
                binding.etEmail.setError("Enter your email");
                return;
            }

            if (password.isEmpty()) {
                binding.etPassword.setError("Enter your password");
                return;
            }

            authViewModel.login(email, password);
        });

        binding.tvSignUp.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
            startActivity(intent);
        });
    }

    private void observeViewModel() {
        // Loading state
        authViewModel.getIsLoading().observe(this, isLoading -> {
            binding.btnLogin.setEnabled(!isLoading);
            binding.btnLogin.setText(isLoading ? "Please wait..." : "Sign In");
        });

        // Error messages
        authViewModel.getErrorMessage().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
            }
        });

        // Login success
        authViewModel.getUserLiveData().observe(this, user -> {
            if (user != null) {
                Toast.makeText(this, "Login Successful!", Toast.LENGTH_SHORT).show();
                new Handler(Looper.getMainLooper()).post(this::navigateToMain);
            }
        });
    }

    private void navigateToMain() {
        if (isFinishing() || isDestroyed()) {
            return;
        }

        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        startActivity(intent);
        // Prevent returning to login with Back button
        finish();
    }
}
