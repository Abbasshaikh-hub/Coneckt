package com.conekt.chat_App.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexType;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexed;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "users")
public class AppUser {
    @Id
    private String id;

    @Indexed(unique = true)
    private String userName;
    private String password;
    private String profilePicUrl;
    private String bio;
    private String currentVenue;
    private String email;

    // Determines Green Ring (true) vs Red/Dark Ring (false)
    private boolean isPublic = true;

    // Fast status check
    private boolean isOnline = true;

    // 2DSphere Spatial Index allows fast radius/proximity queries on MongoDB
    @GeoSpatialIndexed(type = GeoSpatialIndexType.GEO_2DSPHERE)
    private GeoJsonPoint location;

    private Instant lastActiveAt;

}
