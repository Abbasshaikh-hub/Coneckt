package com.conekt.chat_App.repository;

import com.conekt.chat_App.entity.AppUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Point;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;


import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends MongoRepository<AppUser, String> {
    Slice<AppUser> findByLocationNear(Point point, Distance distance, Pageable pageable);
    Optional<AppUser> findByUserNameIgnoreCase(String userName);
}
