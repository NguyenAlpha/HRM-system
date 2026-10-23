package com.htttdn.hrm.ratelimit;

/**
 * One quota. {@link #key()} is the name used in {@code rate-limit.policies.<key>.*} and in the
 * statistics; {@link #scope()} says what a bucket is counted by.
 */
public enum RateLimitPolicy {
    LOGIN("login", Scope.IP),
    REFRESH("refresh", Scope.IP),
    ACTIVATION("activation", Scope.IP),
    IP_CEILING("ip-ceiling", Scope.IP),
    LOGIN_FAILURE("login-failure", Scope.ACCOUNT),
    API("api", Scope.USER),
    PASSWORD("password", Scope.USER),
    EXPORT("export", Scope.USER),
    REPORT("report", Scope.USER),
    HEAVY_WRITE("heavy-write", Scope.USER);

    public enum Scope { IP, USER, ACCOUNT }

    private final String key;
    private final Scope scope;

    RateLimitPolicy(String key, Scope scope) {
        this.key = key;
        this.scope = scope;
    }

    public String key() {
        return key;
    }

    public Scope scope() {
        return scope;
    }
}
