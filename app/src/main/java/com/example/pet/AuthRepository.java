
package com.example.pet;

import androidx.lifecycle.MutableLiveData;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class AuthRepository {
    private FirebaseAuth firebaseAuth;

    public AuthRepository() {
        try {
            firebaseAuth = FirebaseAuth.getInstance();
        } catch (Exception e) {
            // Handle cases where Google Play Services or Firebase instance fails
        }
    }

    private FirebaseAuth getFirebaseAuth() {
        if (firebaseAuth == null) {
            firebaseAuth = FirebaseAuth.getInstance();
        }
        return firebaseAuth;
    }

    public MutableLiveData<FirebaseUser> login(String email, String password) {
        MutableLiveData<FirebaseUser> userLiveData = new MutableLiveData<>();

        try {
            FirebaseAuth auth = getFirebaseAuth();
            if (auth == null) {
                userLiveData.setValue(null);
                return userLiveData;
            }

            auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            userLiveData.setValue(auth.getCurrentUser());
                        } else {
                            userLiveData.setValue(null);
                        }
                    });
        } catch (Exception e) {
            userLiveData.setValue(null);
        }
        return userLiveData;
    }

    public FirebaseUser getCurrentUser() {
        try {
            FirebaseAuth auth = getFirebaseAuth();
            return auth != null ? auth.getCurrentUser() : null;
        } catch (Exception e) {
            return null;
        }
    }
}