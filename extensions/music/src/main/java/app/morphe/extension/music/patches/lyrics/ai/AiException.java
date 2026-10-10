/*
 * Copyright 2026 Morphe.
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */
package app.morphe.extension.music.patches.lyrics.ai;

import java.io.IOException;

/** Safe diagnostics: never include response bodies, authorization URLs or credentials. */
public final class AiException extends IOException {
    public enum Kind { AUTH, PERMISSION, LIMIT, NETWORK, PROTOCOL }
    public final Kind kind;
    public final int status;
    public final String code;
    public final String requestId;

    public AiException(Kind kind, int status, String code, String requestId) {
        super(kind + ": " + code);
        this.kind = kind;
        this.status = status;
        this.code = code;
        this.requestId = requestId;
    }

    public static AiException remote(int status, String code, String requestId) {
        Kind kind = status == 401 ? Kind.AUTH : status == 403 ? Kind.PERMISSION
                : status == 429 ? Kind.LIMIT : status >= 500 ? Kind.NETWORK : Kind.PROTOCOL;
        if ("subscription_sharing_usage_limit_exceeded".equals(code) || "rate_limit_exceeded".equals(code)
                || "insufficient_quota".equals(code) || "usage_limit_reached".equals(code)) kind = Kind.LIMIT;
        if ("subscription_sharing_user_not_eligible".equals(code)
                || code.startsWith("chatpass_v2_")) kind = Kind.PERMISSION;
        if (code.endsWith("_unavailable")) kind = Kind.NETWORK;
        return new AiException(kind, status, code, requestId);
    }
}
