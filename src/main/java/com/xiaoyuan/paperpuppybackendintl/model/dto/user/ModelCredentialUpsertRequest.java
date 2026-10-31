package com.xiaoyuan.paperpuppybackendintl.model.dto.user;

/** Override record toString so request logging cannot print a secret. */
public record ModelCredentialUpsertRequest(String apiKey) {
    @Override
    public String toString() {
        return "ModelCredentialUpsertRequest[apiKey=<redacted>]";
    }
}
