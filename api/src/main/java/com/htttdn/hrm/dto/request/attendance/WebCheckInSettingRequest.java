package com.htttdn.hrm.dto.request.attendance;

import jakarta.validation.constraints.NotNull;

/** Switches web check-in on or off for the whole company. */
public record WebCheckInSettingRequest(
    @NotNull
    Boolean enabled
) {
}
