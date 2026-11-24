package com.docflow.common;

import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;

/**
 * Multi-tenancy: every request carries X-Tenant-Id and every query is scoped by it.
 * In production the tenant comes from the signed-in user's token (OIDC), not a header.
 */
public final class Tenants {

    public static final String HEADER = "X-Tenant-Id";
    private static final Pattern VALID = Pattern.compile("^[a-z0-9][a-z0-9-]{1,39}$");

    private Tenants() {
    }

    public static String require(String tenantId) {
        if (tenantId == null || !VALID.matcher(tenantId).matches()) {
            throw new InvalidRequestException(HttpStatus.BAD_REQUEST,
                    "Header " + HEADER + " must be 2-40 lowercase letters, digits or hyphens.");
        }
        return tenantId;
    }
}
