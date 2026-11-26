package com.conekt.chat_App.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OtpService {
    private final JavaMailSender mailSender;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, OtpEntry> otpStore = new ConcurrentHashMap<>();

    @Value("${spring.mail.username}")
    private String fromEmail;

    private record OtpEntry(String otp, Instant expiresAt) {}

    public OtpService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }


    public void generateAndSendOtp(String toEmail) {
        String normalizedEmail = toEmail.trim().toLowerCase();
        String otp = String.format("%06d", secureRandom.nextInt(1_000_000));
        otpStore.put(normalizedEmail, new OtpEntry(otp, Instant.now().plusSeconds(300))); // 5 mins

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(normalizedEmail);
        message.setSubject("Conekt — Your Verification OTP: " + otp);
        message.setText(
                "Welcome to Conekt!\n\n" +
                        "Your 6-digit verification code is: " + otp + "\n\n" +
                        "This code expires in 5 minutes. Do not share it with anyone."
        );

        mailSender.send(message);
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
}
