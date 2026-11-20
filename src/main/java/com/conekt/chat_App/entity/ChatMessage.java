package com.conekt.chat_App.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.lang.management.MemoryType;
import java.time.Instant;

@Document(collection = "chat_messages")
public class ChatMessage {

    @Id
    private String id;

    @Indexed
    private String senderId;

    @Indexed
    private String recipientId;

    private String senderName;
    private String content;

    public enum MessageType{
        CHAT, JOIN , LEAVE, TYPING, READ_RECEIPT;

    }
    public enum MessageStatus{
        SENT, DELIVERED, READ;
    }

    private MessageType type;
    private MessageStatus status = MessageStatus.SENT;
    private Instant timestamp = Instant.now();

    public ChatMessage() {}

    public ChatMessage(String senderId, String recipientId, String senderName, String content, MessageType type) {
        this.senderId = senderId;
        this.recipientId = recipientId;
        this.senderName = senderName;
        this.content = content;
        this.type = type;
        this.status = MessageStatus.SENT;
        this.timestamp = Instant.now();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getRecipientId() { return recipientId; }
    public void setRecipientId(String recipientId) { this.recipientId = recipientId; }

    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public MessageType getType() { return type; }
    public void setType(MessageType type) { this.type = type; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public MessageStatus getStatus() {
        return status;
    }

    public void setStatus(MessageStatus status) {
        this.status = status;
    }

}
