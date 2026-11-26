package com.conekt.chat_App.service;

import com.conekt.chat_App.entity.AppUser;
import com.conekt.chat_App.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.geo.Point;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;
import org.springframework.stereotype.Service;

//import java.awt.*;


@Service
public class UserService {
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository){
        this.userRepository = userRepository;
    }

    public AppUser saveUser(AppUser user){
        return userRepository.save(user);
    }

    public Slice<AppUser> findNearbyUser(double longitude, double latitude, double distanceInKm, int page, int size){
        Point point = new Point( longitude, latitude);
        Distance distance = new Distance(distanceInKm, Metrics.KILOMETERS);
        Pageable pageable = PageRequest.of(page,size);

        return userRepository.findByLocationNear(point, distance, pageable);
    }
}
