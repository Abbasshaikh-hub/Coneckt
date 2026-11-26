package com.conekt.chat_App.service;

import com.conekt.chat_App.dto.ConversationSummaryDto;
import com.conekt.chat_App.entity.AppUser;
import com.conekt.chat_App.entity.ChatMessage;
import com.conekt.chat_App.repository.UserRepository;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ConversationService {

    private final MongoTemplate mongoTemplate;
    private final UserRepository userRepository;

    public ConversationService(MongoTemplate mongoTemplate, UserRepository userRepository) {
        this.mongoTemplate = mongoTemplate;
        this.userRepository = userRepository;
    }

    public List<ConversationSummaryDto> getSmartInbox(String userId) {
        // 1. Match all messages where current user is sender OR recipient
        MatchOperation matchUserMessages = Aggregation.match(
                new Criteria().orOperator(
                        Criteria.where("senderId").is(userId),
                        Criteria.where("recipientId").is(userId)
                )
        );

        // 2. Sort descending so $first picks the latest message per conversation
        SortOperation sortByLatest = Aggregation.sort(Sort.Direction.DESC, "timestamp");

        // 3. Compute partnerId and whether this message is unread by current user
        ProjectionOperation projectPartner = Aggregation.project("content", "senderId", "recipientId", "timestamp", "status")
                .and(ConditionalOperators.when(Criteria.where("senderId").is(userId))
                        .thenValueOf("recipientId")
                        .otherwiseValueOf("senderId"))
                .as("partnerId")
                .and(ConditionalOperators.when(
                                new Criteria().andOperator(
                                        Criteria.where("recipientId").is(userId),
                                        Criteria.where("status").ne("READ")
                                ))
                        .then(1)
                        .otherwise(0))
                .as("isUnread");

        // 4. Group by partnerId
        GroupOperation groupByPartner = Aggregation.group("partnerId")
                .first("content").as("lastMessage")
                .first("senderId").as("lastSenderId")
                .first("timestamp").as("lastTimestamp")
                .sum("isUnread").as("unreadCount");

        // 5. Sort conversations by most recent message timestamp
        SortOperation sortConversations = Aggregation.sort(Sort.Direction.DESC, "lastTimestamp");

        Aggregation aggregation = Aggregation.newAggregation(
                matchUserMessages,
                sortByLatest,
                projectPartner,
                groupByPartner,
                sortConversations
        );

        AggregationResults<Document> results =
                mongoTemplate.aggregate(aggregation, ChatMessage.class, Document.class);

        List<Document> docs = results.getMappedResults();
        if (docs.isEmpty()) {
            return Collections.emptyList();
        }

        // Batch-fetch partner profiles in one query (avoids N+1 database calls)
        List<String> partnerIds = docs.stream()
                .map(d -> d.getString("_id"))
                .filter(Objects::nonNull)
                .toList();

        Map<String, AppUser> partnersMap = userRepository.findAllById(partnerIds).stream()
                .collect(Collectors.toMap(AppUser::getId, Function.identity()));

        List<ConversationSummaryDto> inbox = new ArrayList<>();
        for (Document doc : docs) {
            String partnerId = doc.getString("_id");
            AppUser partner = partnersMap.get(partnerId);
            if (partner == null) continue;

            Date tsDate = doc.getDate("lastTimestamp");
            Instant lastTs = tsDate != null ? tsDate.toInstant() : Instant.now();
            Number unreadNum = (Number) doc.get("unreadCount");

            inbox.add(new ConversationSummaryDto(
                    partner.getId(),
                    partner.getUserName(),
                    partner.getBio(),
                    partner.isOnline(),
                    doc.getString("lastMessage"),
                    doc.getString("lastSenderId"),
                    lastTs,
                    unreadNum != null ? unreadNum.longValue() : 0L
            ));
        }

        return inbox;
    }
}