package com.conekt.chat_App.dto;

import lombok.Data;

@Data
public class AuthRequestDto {
    private String userName;
    private String password;
    private String bio;
    private Double longitude;
    private Double latitude;
}
