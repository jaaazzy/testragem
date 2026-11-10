package com.testragem.server.user.entity;
import java.time.OffsetDateTime;
import java.util.UUID;
public record User (
        UUID id,
        String email,
        String displayName,
        OffsetDateTime createdAt
){

}
