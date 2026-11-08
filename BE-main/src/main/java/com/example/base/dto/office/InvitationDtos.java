package com.example.base.dto.office;

import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;

public final class InvitationDtos {
    private InvitationDtos() {}
    public record Preview(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{43}") String token) {}
    public record Response(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{43}") String token,
                           @NotBlank @Pattern(regexp="CONFIRM|DECLINE") String decision,@Size(max=2000) String reason) {}
    // No claimant identity, evidence, private item identifiers or manager notes in public responses.
    public record View(String title, OffsetDateTime scheduledAt, String location,
                       OffsetDateTime deadline, String status) {}
}
