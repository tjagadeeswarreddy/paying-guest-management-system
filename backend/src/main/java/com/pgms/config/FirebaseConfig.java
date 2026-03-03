package com.pgms.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import com.google.cloud.firestore.Firestore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

@Configuration
@ConditionalOnProperty(name = "app.data-provider", havingValue = "firebase")
@EnableConfigurationProperties(FirebaseProperties.class)
public class FirebaseConfig {

    @Bean
    public FirebaseApp firebaseApp(FirebaseProperties firebaseProperties) throws IOException {
        FirebaseApp existingApp = FirebaseApp.getApps().stream().findFirst().orElse(null);
        if (existingApp != null) {
            return existingApp;
        }

        GoogleCredentials credentials = resolveCredentials(firebaseProperties);
        FirebaseOptions.Builder optionsBuilder = FirebaseOptions.builder().setCredentials(credentials);
        if (hasText(firebaseProperties.getProjectId())) {
            optionsBuilder.setProjectId(firebaseProperties.getProjectId().trim());
        }
        return FirebaseApp.initializeApp(optionsBuilder.build());
    }

    @Bean
    public Firestore firestore(FirebaseApp firebaseApp) {
        return FirestoreClient.getFirestore(firebaseApp);
    }

    private GoogleCredentials resolveCredentials(FirebaseProperties firebaseProperties) throws IOException {
        if (hasText(firebaseProperties.getCredentialsPath())) {
            try (InputStream stream = new FileInputStream(firebaseProperties.getCredentialsPath().trim())) {
                return GoogleCredentials.fromStream(stream);
            }
        }
        return GoogleCredentials.getApplicationDefault();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
