package com.conekt.chat_App.config;

import com.conekt.chat_App.entity.AppUser;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexType;
import org.springframework.data.mongodb.core.index.GeospatialIndex;

@Configuration
public class MongoIndexConfig {

    private MongoTemplate mongoTemplate;

    public MongoIndexConfig(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @PostConstruct
    public void initIndexes() {
        mongoTemplate.indexOps(AppUser.class)
                .createIndex(new GeospatialIndex("location")
                        .typed(GeoSpatialIndexType.GEO_2DSPHERE));
    }
}
