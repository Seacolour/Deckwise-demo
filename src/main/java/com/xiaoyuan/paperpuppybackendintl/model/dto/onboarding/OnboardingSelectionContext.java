package com.xiaoyuan.paperpuppybackendintl.model.dto.onboarding;

import org.springframework.util.StringUtils;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

/**
 * 首次邮箱登录时用于记录语言、地区和时区偏好的客户端上下文。
 */
public record OnboardingSelectionContext(
        String locale,
        String country,
        String timeZone,
        String acceptLanguage
) {

    public static final OnboardingSelectionContext EMPTY =
            new OnboardingSelectionContext(null, null, null, null);

    public OnboardingSelectionContext {
        acceptLanguage = trimToNull(acceptLanguage, 512);
        locale = normalizeLocale(firstNonBlank(locale, firstAcceptLanguageTag(acceptLanguage)));
        country = normalizeCountry(country);
        timeZone = normalizeTimeZone(timeZone);
    }

    public static OnboardingSelectionContext of(String locale,
                                                String country,
                                                String timeZone,
                                                String acceptLanguage) {
        return new OnboardingSelectionContext(locale, country, timeZone, acceptLanguage);
    }

    public boolean isEmpty() {
        return !StringUtils.hasText(locale)
                && !StringUtils.hasText(country)
                && !StringUtils.hasText(timeZone)
                && !StringUtils.hasText(acceptLanguage);
    }

    public String language() {
        if (!StringUtils.hasText(locale)) {
            return null;
        }
        int dash = locale.indexOf('-');
        return (dash > 0 ? locale.substring(0, dash) : locale).toLowerCase(Locale.ROOT);
    }

    private static String firstAcceptLanguageTag(String acceptLanguage) {
        if (!StringUtils.hasText(acceptLanguage)) {
            return null;
        }
        try {
            List<Locale.LanguageRange> ranges = Locale.LanguageRange.parse(acceptLanguage);
            for (Locale.LanguageRange range : ranges) {
                String value = range.getRange();
                if (StringUtils.hasText(value) && !"*".equals(value)) {
                    return value;
                }
            }
        } catch (IllegalArgumentException ignored) {
            int comma = acceptLanguage.indexOf(',');
            return comma >= 0 ? acceptLanguage.substring(0, comma) : acceptLanguage;
        }
        return null;
    }

    private static String normalizeLocale(String value) {
        String text = trimToNull(value, 32);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String candidate = text.replace('_', '-');
        Locale parsed = Locale.forLanguageTag(candidate);
        if (StringUtils.hasText(parsed.getLanguage()) && !"und".equals(parsed.getLanguage())) {
            return parsed.toLanguageTag();
        }
        return candidate;
    }

    private static String normalizeCountry(String value) {
        String text = trimToNull(value, 2);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String country = text.toUpperCase(Locale.ROOT);
        if (!country.matches("^[A-Z]{2}$") || "XX".equals(country) || "T1".equals(country)) {
            return null;
        }
        return country;
    }

    private static String normalizeTimeZone(String value) {
        String text = trimToNull(value, 64);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            ZoneId.of(text);
            return text;
        } catch (DateTimeException ignored) {
            return null;
        }
    }

    private static String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private static String trimToNull(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > maxLength) {
            return null;
        }
        return trimmed;
    }
}
