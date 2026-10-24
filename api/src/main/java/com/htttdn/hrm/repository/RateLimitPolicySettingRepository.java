package com.htttdn.hrm.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.htttdn.hrm.entity.RateLimitPolicySetting;

public interface RateLimitPolicySettingRepository extends JpaRepository<RateLimitPolicySetting, String> {
}
