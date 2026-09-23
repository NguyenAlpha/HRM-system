package com.htttdn.hrm.security;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.htttdn.hrm.entity.Account;
import com.htttdn.hrm.entity.enums.AccountStatus;
import com.htttdn.hrm.repository.AccountRepository;

@Service
public class AccountUserDetailsService implements UserDetailsService {

    private final AccountRepository accountRepository;

    public AccountUserDetailsService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String usernameOrEmail) {
        Account account = accountRepository.findByLogin(usernameOrEmail)
            .orElseThrow(() -> new UsernameNotFoundException("Account not found"));

        boolean active = account.getStatus() == AccountStatus.ACTIVE;
        return User.withUsername(account.getUsername())
            .password(account.getPasswordHash())
            .authorities("ACCOUNT")
            .disabled(!active)
            .accountLocked(account.getStatus() == AccountStatus.LOCKED)
            .build();
    }
}
