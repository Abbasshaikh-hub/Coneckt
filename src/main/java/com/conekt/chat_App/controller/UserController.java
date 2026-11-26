package com.conekt.chat_App.controller;


import com.conekt.chat_App.service.OtpService;
import com.conekt.chat_App.service.UserService;
import com.conekt.chat_App.dto.AuthRequestDto;
import com.conekt.chat_App.dto.UserRequestDto;
import com.conekt.chat_App.entity.AppUser;
import com.conekt.chat_App.repository.UserRepository;
import com.conekt.chat_App.security.JwtUtil;
import org.springframework.data.domain.Slice;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;


import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
public class UserController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final OtpService otpService;

    public UserController(UserService userService, UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil, OtpService otpService) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.otpService = otpService;
    }


    @PostMapping
    public ResponseEntity<AppUser> createUser(@RequestBody UserRequestDto dto){
        AppUser user = new AppUser();
        user.setUserName(dto.getName());
        if (dto.getLocation() != null){
            user.setLocation(new GeoJsonPoint(
                    dto.getLocation().getLongitude(),
                    dto.getLocation().getLatitude()
            ));
        }
        return ResponseEntity.ok(userService.saveUser(user));
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody AuthRequestDto payload) {
        String userName = payload.getUserName();
        String email = payload.getEmail();
        String password = payload.getPassword();
        String bio = (payload.getBio() != null && !payload.getBio().isBlank())
                ? payload.getBio() : "Hey there! I am using Conekt.";

        if (userName == null || userName.isBlank() || password == null || password.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username and password are required"));
        }

        if (!otpService.verifyOtp(payload.getEmail(), payload.getOtp())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Invalid or expired OTP. Please request a new code."));
        }

        if (userRepository.findByUserNameIgnoreCase(userName.trim()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Username already taken"));
        }

        double lng = payload.getLongitude() != null ? payload.getLongitude() : 72.8777;
        double lat = payload.getLatitude() != null ? payload.getLatitude() : 19.0760;

        AppUser newUser = new AppUser();
        newUser.setUserName(userName.trim());
        newUser.setEmail(email.trim().toLowerCase());
        newUser.setPassword(passwordEncoder.encode(password));
        newUser.setBio(bio);
        newUser.setLocation(new GeoJsonPoint(lng, lat));
        newUser.setLastActiveAt(Instant.now());
        newUser.setOnline(true);

        AppUser saved = userRepository.save(newUser);
        String token = jwtUtil.generateToken(saved.getId(), saved.getUserName());

        saved.setPassword(null);
        return ResponseEntity.ok(Map.of(
                "token", token,
                "user", saved
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<?> loginUser(@RequestBody AuthRequestDto credentials) {
        String userName = credentials.getUserName();
        String password = credentials.getPassword();

        if (userName == null || password == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username and password are required"));
        }

        Optional<AppUser> userOpt = userRepository.findByUserNameIgnoreCase(userName.trim());
        if (userOpt.isEmpty() || !passwordEncoder.matches(password, userOpt.get().getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid username or password"));
        }

        AppUser user = userOpt.get();
        user.setOnline(true);
        user.setLastActiveAt(Instant.now());
        userRepository.save(user);

        String token = jwtUtil.generateToken(user.getId(), user.getUserName());
        user.setPassword(null);

        return ResponseEntity.ok(Map.of(
                "token", token,
                "user", user
        ));
    }

    @GetMapping
    public ResponseEntity<List<AppUser>> getAllUsers() {
        List<AppUser> users = userRepository.findAll();
        users.forEach(u -> u.setPassword(null));
        return ResponseEntity.ok(users);
    }
    @PutMapping("/{userId}/location")
    public ResponseEntity<AppUser> updateLocation(
            @PathVariable String userId,
            @RequestParam double longitude,
            @RequestParam double latitude) {

        return userRepository.findById(userId).map(user -> {
            user.setLocation(new GeoJsonPoint(longitude, latitude));
            user.setLastActiveAt(Instant.now());
            AppUser updated = userRepository.save(user);
            updated.setPassword(null);
            return ResponseEntity.ok(updated);
        }).orElse(ResponseEntity.notFound().build());
    }
    @GetMapping("/nearby")
    public ResponseEntity<Slice<AppUser>> getNearbyUsers(
            @RequestParam double longitude,
            @RequestParam double latitude,
            @RequestParam(defaultValue = "5") double maxDistanceKm,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Slice<AppUser> nearbyUsersPage = userService.findNearbyUser(longitude, latitude, maxDistanceKm, page, size);
        nearbyUsersPage.forEach(u -> u.setPassword(null));
        return ResponseEntity.ok(nearbyUsersPage);
    }

    @PostMapping("/send-otp")
    public ResponseEntity<?> sendOtp(@RequestBody AuthRequestDto payload) {
        String userName = payload.getUserName();
        String email = payload.getEmail();

        if (userName == null || userName.isBlank() || email == null || !email.contains("@")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Valid username and Gmail address are required"));
        }

        if (userRepository.findByUserNameIgnoreCase(userName.trim()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Username already taken"));
        }

        if (userRepository.findByEmailIgnoreCase(email.trim()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Email is already registered"));
        }

        try {
            otpService.generateAndSendOtp(email);
            return ResponseEntity.ok(Map.of("message", "OTP sent to " + email.trim()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to send email. Check Gmail SMTP config."));
        }
    }

    // 4. UPDATE USER'S REAL-TIME GPS LOCATION
}

//1. REGISTER NEW USER
//    @PostMapping("/register")
//    public ResponseEntity<?> registerUser(@RequestBody  Map<String, Object> payload){
//        String userName = (String) payload.get("username");
//        String password = (String) payload.get("password");
//        String bio = (String) payload.getOrDefault("bio","Hey there! I am using Coneckt. ");
//
//        if (userName == null || userName.isBlank() || password == null || password.isBlank()){
//            return ResponseEntity.badRequest().body(Map.of("error", "Username and password are required"));
//        }
//        if (userRepository.findByUserNameIgnoreCase(userName.trim()).isPresent()){
//            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error","Username already taken"));
//        }
//        double lng = payload.get("longitude") != null ? Double.parseDouble(payload.get("longitude").toString()) : 72.877;
//        double lat = payload.get("latitude") != null ? Double.parseDouble(payload.get("latitude").toString()) : 19.0760;
//
//        AppUser newUser = new AppUser();
//        newUser.setUserName(userName.trim());
//        newUser.setPassword(password);
//        newUser.setBio(bio);
//        newUser.setLocation(new GeoJsonPoint(lng, lat));
//        newUser.setLastActiveAt(Instant.now());
//        newUser.setOnline(true);
//
//        AppUser saved = userRepository.save(newUser);
//        saved.setPassword(null); // Do not expose password in response
//        return ResponseEntity.ok(saved);
//    }
//
//    // 2. LOGIN EXISTING USER
//    @PostMapping("/login")
//    public ResponseEntity<?> loginUser(@RequestBody Map<String, String> credentials) {
//        String userName = credentials.get("userName");
//        String password = credentials.get("password");
//
//        if (userName == null || password == null) {
//            return ResponseEntity.badRequest().body(Map.of("error", "Username and password are required"));
//        }
//
//        Optional<AppUser> userOpt = userRepository.findByUserNameIgnoreCase(userName.trim());
//        if (userOpt.isEmpty() || !password.equals(userOpt.get().getPassword())) {
//            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
//                    .body(Map.of("error", "Invalid username or password"));
//        }
//
//        AppUser user = userOpt.get();
//        user.setOnline(true);
//        user.setLastActiveAt(Instant.now());
//        userRepository.save(user);
//
//        user.setPassword(null);
//        return ResponseEntity.ok(user);

//    }


//    @PutMapping("/{userId}/location")
//    public ResponseEntity<AppUser> updateLocation(
//            @PathVariable String userId,
//            @RequestParam double longitude,
//            @RequestParam double latitude,
//            @RequestParam(defaultValue = "true") boolean isOnline) {
//
//        return userService.findById(userId).map(user -> {
//            user.setLocation(new GeoJsonPoint(longitude, latitude));
//            user.setOnline(isOnline);
//            user.setLastActiveAt(Instant.now());
//            AppUser updatedAppUser = userRepository.save(user);
//            return ResponseEntity.ok(updatedAppUser);
//        }).orElse(ResponseEntity.notFound().build());
//    }



