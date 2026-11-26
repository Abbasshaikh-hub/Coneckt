package com.conekt.chat_App.controller;

import com.conekt.chat_App.dto.ConversationSummaryDto;
import com.conekt.chat_App.entity.ChatMessage;
//import org.springframework.messaging.simp.SimpMessagingTemplate;
import com.conekt.chat_App.config.WebSocketPresenceTracker;
import com.conekt.chat_App.repository.ChatMessageRepository;
import com.conekt.chat_App.service.ConversationService;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Controller
@CrossOrigin(origins = "*")
public class ChatController {

    private final SimpMessagingTemplate messagingTemplate;
    private final ChatMessageRepository chatMessageRepository;
    private final WebSocketPresenceTracker presenceTracker;
    private final MongoTemplate mongoTemplate;
    private final ConversationService conversationService;

    public ChatController(SimpMessagingTemplate messagingTemplate, ChatMessageRepository chatMessageRepository, WebSocketPresenceTracker presenceTracker, MongoTemplate mongoTemplate, ConversationService conversationService) {
        this.messagingTemplate = messagingTemplate;
        this.chatMessageRepository = chatMessageRepository;
        this.presenceTracker = presenceTracker;
        this.mongoTemplate = mongoTemplate;
        this.conversationService = conversationService;
    }


    // ==========================================
    // 1. REAL-TIME WEBSOCKET MESSAGE HANDLING
    // ==========================================

    /**
     * Handles messages sent to "/app/chat.send"
     * Saves message to DB and broadcasts to private queue "/user/{recipientId}/queue/messages"
     */

    // 1. REAL-TIME MESSAGE + INSTANT DELIVERY STATUS
    @MessageMapping("/chat.send")
    public void processMessage(@Payload ChatMessage chatMessage){
        chatMessage.setTimestamp(Instant.now());
        boolean recipentOnline = presenceTracker.isUserOnline(chatMessage.getRecipientId());
        chatMessage.setStatus(recipentOnline ? ChatMessage.MessageStatus.DELIVERED : ChatMessage.MessageStatus.SENT);

        ChatMessage savedMsg = chatMessageRepository.save(chatMessage);

        messagingTemplate.convertAndSendToUser(chatMessage.getRecipientId(),
                "/queue/messages", savedMsg
        );
        // Echo saved status (with DB ID & SENT/DELIVERED state) back to sender
        messagingTemplate.convertAndSendToUser(
                chatMessage.getSenderId(),
                "/queue/status",
                savedMsg
        );
    }

    // 2. LIVE TYPING INDICATOR (Transient - not saved to MongoDB)
    @MessageMapping("/chat.typing")
    public void handleTyping(@Payload Map<String, Object> typingEvent) {
        String recipientId = (String) typingEvent.get("recipientId");
        if (recipientId != null) {
            messagingTemplate.convertAndSendToUser(recipientId,
                    "/queue/typing",
                    typingEvent);
        }
    }

    // 3. MARK MESSAGES AS READ & NOTIFY SENDER
    @MessageMapping("/chat.read")
    public void markAsRead(@Payload Map<String, String> payload) {
        String readerId = payload.get("readerId");
        String senderId = payload.get("senderId");

        Query query = new Query(Criteria.where("senderId").is(senderId)
                .and("recipientId").is(readerId)
                .and("status").ne(ChatMessage.MessageStatus.READ));

        messagingTemplate.convertAndSendToUser(senderId,
                "/queue/read-receipts",Map.of
                        ("readerId",readerId,"status","READ"));
    }

    // ==========================================
    // 4. REST API FOR FETCHING CHAT HISTORY
    // ==========================================

    /**
     * Endpoint to load historical messages between two users when opening a chat window.
     * URL: GET /api/messages/{senderId}/{recipientId}
     */

    @GetMapping("api/messages/{senderId}/{recipientId}")
    @ResponseBody
    public ResponseEntity<List<ChatMessage>> findChatMessages(
            @PathVariable String senderId,
            @PathVariable String recipientId) {

        List<ChatMessage> history = chatMessageRepository.
                findBySenderIdAndRecipientIdOrSenderIdAndRecipientIdOrderByTimestampAsc(
                        senderId, recipientId, recipientId, senderId);
        return ResponseEntity.ok(history);
    }

    // 5. REST API: INITIAL ONLINE USERS LIST
    @GetMapping("/api/users/online")
    @ResponseBody
    public ResponseEntity<Set<String>> getOnlineUsers(){
        return ResponseEntity.ok(presenceTracker.getOnlineUsers());
    }

    @GetMapping("/api/conversations/{userId}")
    public ResponseEntity<List<ConversationSummaryDto>> getSmartInbox(@PathVariable String userId) {
        return ResponseEntity.ok(conversationService.getSmartInbox(userId));
    }
}
