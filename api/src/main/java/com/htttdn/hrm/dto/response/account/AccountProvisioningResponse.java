package com.htttdn.hrm.dto.response.account;

public record AccountProvisioningResponse(
    AccountResponse account,
    AccountInvitationResponse invitation
) {
}
