package com.warehouse.auth.service;

import com.warehouse.auth.dto.AuthResponse;
import com.warehouse.auth.dto.LoginRequest;
import com.warehouse.auth.dto.RegisterRequest;
import com.warehouse.auth.dto.UserResponse;
import com.warehouse.auth.entity.Role;
import com.warehouse.auth.entity.User;
import com.warehouse.auth.exception.DuplicateUsernameException;
import com.warehouse.auth.exception.InvalidCredentialsException;
import com.warehouse.auth.exception.ResourceNotFoundException;
import com.warehouse.auth.repository.UserRepository;
import io.jsonwebtoken.Claims;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {

        String username = request.username().trim().toLowerCase();

        if (userRepository.existsByUsername(username)) {
            throw new DuplicateUsernameException(username);
        }

        // Public self-registration always creates STAFF users.
        // ADMIN / MANAGER users are provisioned by an administrator.
        User user = new User(
                username,
                passwordEncoder.encode(request.password()),
                request.fullName().trim(),
                Role.STAFF
        );

        User saved = userRepository.save(user);
        String token = jwtService.generateToken(saved);

        return AuthResponse.bearer(
                token,
                jwtService.getExpirationSeconds(),
                UserResponse.from(saved)
        );
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {

        User user = userRepository.findByUsername(request.username().trim().toLowerCase())
                .orElseThrow(InvalidCredentialsException::new);

        if (!user.isEnabled() || !passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        String token = jwtService.generateToken(user);

        return AuthResponse.bearer(
                token,
                jwtService.getExpirationSeconds(),
                UserResponse.from(user)
        );
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(String authorizationHeader) {

        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new InvalidCredentialsException();
        }

        try {
            Claims claims = jwtService.parseClaims(authorizationHeader.substring(7));
            User user = userRepository.findByUsername(claims.getSubject())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));
            return UserResponse.from(user);
        } catch (InvalidCredentialsException | ResourceNotFoundException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new InvalidCredentialsException();
        }
    }
}
