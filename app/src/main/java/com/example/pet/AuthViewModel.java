package com.example.pet;

import android.util.Patterns;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.auth.FirebaseUser;

/**
 * ViewModel managing authentication state for Login, Registration, Logout,
 * and session persistence.
 */
public class AuthViewModel extends ViewModel {

    private final AuthRepository authRepository;

    private final MutableLiveData<FirebaseUser> userLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> registrationSuccess = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    public AuthViewModel() {
        authRepository = new AuthRepository();
    }

    public LiveData<FirebaseUser> getUserLiveData() {
        return userLiveData;
    }

    public LiveData<Boolean> getRegistrationSuccess() {
        return registrationSuccess;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public FirebaseUser getCurrentUser() {
        return authRepository.getCurrentUser();
    }

    /**
     * Authenticates user with email and password.
     */
    public void login(String email, String password) {
        if (email == null || email.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            errorMessage.setValue("Email and password cannot be empty");
            return;
        }

        isLoading.setValue(true);

        LiveData<FirebaseUser> repositoryResult = authRepository.login(email.trim(), password);
        repositoryResult.observeForever(user -> {
            isLoading.setValue(false);
            if (user != null) {
                userLiveData.setValue(user);
            } else {
                errorMessage.setValue("Login failed. Check your email and password.");
            }
        });
    }

    /**
     * Validates input parameters and creates a new user account with profile info.
     */
    public void register(String name, String email, String password, String confirmPassword) {
        if (name == null || name.trim().isEmpty()) {
            errorMessage.setValue("Please enter your full name");
            return;
        }

        if (email == null || email.trim().isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
            errorMessage.setValue("Please enter a valid email address");
            return;
        }

        if (password == null || password.length() < 6) {
            errorMessage.setValue("Password must be at least 6 characters");
            return;
        }

        if (!password.equals(confirmPassword)) {
            errorMessage.setValue("Passwords do not match");
            return;
        }

        isLoading.setValue(true);

        authRepository.register(email.trim(), password, name.trim(), new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess(FirebaseUser user) {
                isLoading.setValue(false);
                userLiveData.setValue(user);
                registrationSuccess.setValue(true);
            }

            @Override
            public void onError(Exception e) {
                isLoading.setValue(false);
                errorMessage.setValue(e.getMessage() != null ? e.getMessage() : "Registration failed.");
            }
        });
    }

    /**
     * Retries user profile creation for the currently authenticated session without re-registering.
     */
    public void retryProfileCreation(String displayName) {
        isLoading.setValue(true);
        authRepository.retryProfileCreation(displayName, new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess(FirebaseUser user) {
                isLoading.setValue(false);
                userLiveData.setValue(user);
                registrationSuccess.setValue(true);
            }

            @Override
            public void onError(Exception e) {
                isLoading.setValue(false);
                errorMessage.setValue(e.getMessage() != null ? e.getMessage() : "Failed to save profile.");
            }
        });
    }

    /**
     * Signs out the user and clears authentication state.
     */
    public void logout() {
        authRepository.logout();
        userLiveData.setValue(null);
        registrationSuccess.setValue(false);
    }
}
