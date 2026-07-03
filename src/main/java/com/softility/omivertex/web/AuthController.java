package com.softility.omivertex.web;

import com.softility.omivertex.domain.AppUser;
import com.softility.omivertex.repository.AppUserRepository;
import com.softility.omivertex.web.error.BadRequestException;
import com.softility.omivertex.web.error.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final Map<String, String> DISPLAY_NAMES = Map.of(
            "admin", "Super Admin",
            "viewer", "Viewer");

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record GoogleLoginRequest(@NotBlank String email, @NotBlank String name) {
    }

    public record UserResponse(String username, String role, String displayName) {

        static UserResponse from(Authentication auth) {
            String role = auth.getAuthorities().stream()
                    .map(a -> a.getAuthority().replaceFirst("^ROLE_", ""))
                    .findFirst().orElse("VIEWER");
            String name = auth.getName();
            return new UserResponse(name, role, DISPLAY_NAMES.getOrDefault(name, name));
        }
    }

    private final AuthenticationManager authenticationManager;
    private final AppUserRepository appUserRepository;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public AuthController(AuthenticationManager authenticationManager, AppUserRepository appUserRepository) {
        this.authenticationManager = authenticationManager;
        this.appUserRepository = appUserRepository;
    }

    @PostMapping("/login")
    public UserResponse login(@jakarta.validation.Valid @RequestBody LoginRequest login,
                              HttpServletRequest request, HttpServletResponse response) {
        Authentication auth;
        try {
            auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(login.username(), login.password()));
        } catch (AuthenticationException e) {
            throw new UnauthorizedException("Invalid username or password");
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        return UserResponse.from(auth);
    }

    @PostMapping("/google")
    public UserResponse googleLogin(@jakarta.validation.Valid @RequestBody GoogleLoginRequest googleRequest,
                                    HttpServletRequest request, HttpServletResponse response) {
        String email = googleRequest.email().trim().toLowerCase();
        if (!email.endsWith("@softility.com")) {
            throw new BadRequestException("Only @softility.com company email addresses are allowed.");
        }

        AppUser appUser = appUserRepository.findByEmailIgnoreCase(email).orElseGet(() -> {
            AppUser newRequest = new AppUser();
            newRequest.setEmail(email);
            newRequest.setName(googleRequest.name());
            newRequest.setStatus("PENDING");
            return appUserRepository.save(newRequest);
        });

        if ("PENDING".equals(appUser.getStatus())) {
            throw new UnauthorizedException("Your access request is pending approval by the Admin.");
        }
        if ("REJECTED".equals(appUser.getStatus())) {
            throw new UnauthorizedException("Your access request was rejected by the Admin.");
        }

        Authentication auth = new UsernamePasswordAuthenticationToken(
                appUser.getEmail(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + appUser.getRole()))
        );

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        return new UserResponse(appUser.getEmail(), appUser.getRole(), appUser.getName());
    }

    @GetMapping("/me")
    public UserResponse me(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            throw new UnauthorizedException("Not signed in");
        }
        String username = auth.getName();
        String role = auth.getAuthorities().stream()
                .map(a -> a.getAuthority().replaceFirst("^ROLE_", ""))
                .findFirst().orElse("VIEWER");
        String displayName = DISPLAY_NAMES.get(username);
        if (displayName == null) {
            displayName = appUserRepository.findByEmailIgnoreCase(username)
                    .map(AppUser::getName)
                    .orElse(username);
        }
        return new UserResponse(username, role, displayName);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }
}
