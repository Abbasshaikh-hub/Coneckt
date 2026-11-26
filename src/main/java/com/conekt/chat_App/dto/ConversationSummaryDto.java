package com.conekt.chat_App.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ConversationSummaryDto {
    private String partnerId;
    private String partnerName;
    private String partnerBio;
    private boolean online;
    private String lastMessage;
    private String lastSenderId;
    private Instant lastTimestamp;
    private long unreadCount;
}