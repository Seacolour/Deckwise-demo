package com.xiaoyuan.paperpuppybackendintl.model.dto.user;

/** Public metadata only. Never add ciphertext or the complete API key here. */
public record ModelCredentialStatus(String providerId, boolean configured, String maskedKey) {
}
