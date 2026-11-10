package com.testragem.server.user.controller;

import com.testragem.server.user.entity.User;
import com.testragem.server.user.service.UserService;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService users;
    public UserController(UserService users) { this.users = users; }

    @GetMapping("/me")
    User me(Authentication authentication) {
        return users.getById(UUID.fromString(authentication.getName()));
    }
}
