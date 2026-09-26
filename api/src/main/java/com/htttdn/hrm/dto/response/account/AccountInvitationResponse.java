package com.htttdn.hrm.dto.response.account;

import java.time.Instant;

public record AccountInvitationResponse(
    Long accountId,
    String activationToken,
    Instant expiresAt
) {
}
