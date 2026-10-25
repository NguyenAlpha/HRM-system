package com.htttdn.hrm.service.system;


import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.dto.request.ratelimit.RateLimitPolicyRequest;
import com.htttdn.hrm.dto.response.common.ErrorCode;
import com.htttdn.hrm.dto.response.ratelimit.RateLimitStatsResponse;
import com.htttdn.hrm.entity.RateLimitPolicySetting;
import com.htttdn.hrm.exception.BusinessException;
import com.htttdn.hrm.exception.ResourceNotFoundException;
import com.htttdn.hrm.ratelimit.RateLimitPolicy;
import com.htttdn.hrm.ratelimit.RateLimitProperties.Quota;
import com.htttdn.hrm.ratelimit.RateLimitService;
import com.htttdn.hrm.repository.AccountRepository;
import com.htttdn.hrm.repository.RateLimitPolicySettingRepository;
import com.htttdn.hrm.security.CurrentAccountProvider;

import jakarta.annotation.PostConstruct;

import lombok.RequiredArgsConstructor;

/**
 * Rate-limit quotas edited by administrators. Each change is stored in
 * {@code rate_limit_policy_settings} and applied to {@link RateLimitService} at once; stored
 * changes are replayed when the backend starts, before it serves requests.
 */
@Service
@RequiredArgsConstructor
public class RateLimitSettingsService {

    private final RateLimitPolicySettingRepository settingRepository;
    private final AccountRepository accountRepository;
    private final RateLimitService rateLimitService;
    private final CurrentAccountProvider currentAccountProvider;

    @PostConstruct
    void applyStoredSettings() {
        for (RateLimitPolicySetting setting : settingRepository.findAll()) {
            RateLimitPolicy.fromKey(setting.getPolicy()).ifPresent(policy -> apply(policy, setting));
        }
    }

    /** Stores the new quota and on/off state, then answers the statistics with it in force. */
    @Transactional
    public RateLimitStatsResponse update(String policyKey, RateLimitPolicyRequest request) {
        RateLimitPolicy policy = requirePolicy(policyKey);
        if (!request.enabled() && !policy.canDisable()) {
            throw new BusinessException(
                ErrorCode.RATE_LIMIT_POLICY_REQUIRED,
                policy.key() + " guards against password guessing and cannot be disabled",
                "enabled"
            );
        }
        RateLimitPolicySetting setting = settingRepository.findById(policy.key())
            .orElseGet(() -> RateLimitPolicySetting.builder().policy(policy.key()).build());
        setting.setEnabled(request.enabled());
        setting.setMaxRequests(request.maxRequests());
        setting.setWindowSeconds(request.windowSeconds());
        setting.setUpdatedByAccount(accountRepository.getReferenceById(currentAccountProvider.accountId()));
        // Flushed first, so a rejected row never reaches the buckets.
        settingRepository.saveAndFlush(setting);
        apply(policy, setting);
        return rateLimitService.stats();
    }

    /** Drops the administrator's change: the application.properties quota applies again, switched on. */
    @Transactional
    public RateLimitStatsResponse restoreDefault(String policyKey) {
        RateLimitPolicy policy = requirePolicy(policyKey);
        settingRepository.deleteById(policy.key());
        settingRepository.flush();
        rateLimitService.restoreDefault(policy);
        return rateLimitService.stats();
    }

    private void apply(RateLimitPolicy policy, RateLimitPolicySetting setting) {
        rateLimitService.configure(
            policy,
            new Quota(setting.getMaxRequests(), setting.getWindowSeconds()),
            setting.isEnabled()
        );
    }

    private static RateLimitPolicy requirePolicy(String key) {
        return RateLimitPolicy.fromKey(key).orElseThrow(() -> new ResourceNotFoundException(
            ErrorCode.RATE_LIMIT_POLICY_NOT_FOUND,
            "Rate-limit policy not found: " + key
        ));
    }
}
