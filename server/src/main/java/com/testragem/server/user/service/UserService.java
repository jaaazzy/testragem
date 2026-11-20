package com.testragem.server.user.service;

import com.testragem.server.common.error.ApiErrors;
import com.testragem.server.user.entity.User;
import com.testragem.server.user.repository.UserRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Resolves the current user. A session cannot continue to identify a deleted account. */
@Service
public class UserService {
    private final UserRepository users;
    public UserService(UserRepository users) { this.users = users; }

    public User getById(UUID id) {
        return users.findById(id).orElseThrow(() ->
                new ApiErrors.Failure(HttpStatus.UNAUTHORIZED, "unauthenticated"));
    }
}
