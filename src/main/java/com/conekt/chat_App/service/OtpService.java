package com.conekt.chat_App.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.apache.logging.log4j.util.StringBuilders.escapeJson;

@Service
public class OtpService {
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, OtpEntry> otpStore = new ConcurrentHashMap<>();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${resend.api-key}")
    private String resendApiKey;

    @Value("${resend.mail.username}")
    private String fromEmail;

    private record OtpEntry(String otp, Instant expiresAt) {}

    public void generateAndSendOtp(String toEmail) {
        String normalizedEmail = toEmail.trim().toLowerCase();
        String otp = String.format("%06d", secureRandom.nextInt(1_000_000));
        otpStore.put(normalizedEmail, new OtpEntry(otp, Instant.now().plusSeconds(300))); // 5 mins

        String textBody = "Welcome to Conekt! Your 6-digit verification code is: " + otp
                + ". This code expires in 5 minutes.";

        String jsonPayload = String.format(
                "{\"from\":\"%s\",\"to\":[\"%s\"],\"subject\":\"Conekt — Your Verification OTP: %s\",\"text\":\"%s\"}",
                escapeJson(fromEmail),
                escapeJson(normalizedEmail),
                escapeJson(otp),
                escapeJson(textBody)
        );

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.resend.com/emails"))
                    .header("Authorization", "Bearer " + resendApiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("Resend API error (" + response.statusCode() + "): " + response.body());
            }
        } catch (Exception e) {
            otpStore.remove(normalizedEmail);
            throw new RuntimeException("Failed to send OTP email", e);
        }


    }
    public boolean verifyOtp(String email, String inputOtp) {
        if (email == null || inputOtp == null) return false;
        String normalizedEmail = email.trim().toLowerCase();
        OtpEntry entry = otpStore.get(normalizedEmail);

        if (entry == null || Instant.now().isAfter(entry.expiresAt())) {
            otpStore.remove(normalizedEmail);
            return false;
        }

        if (entry.otp().equals(inputOtp.trim())) {
            otpStore.remove(normalizedEmail);
            return true;
        }
        return false;
    }


    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
