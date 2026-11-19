package com.gis.warplan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.gis.warplan.web.BadRequestException;

class SvgIconSanitizerTest {

    @Test
    void keepsThemeTokensAndLockedColours() {
        String clean = SvgIconSanitizer.sanitize("""
                <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24">
                  <rect x="2" y="3" width="10" height="8" fill="var(--wp-primary)" data-wp-fill="primary"/>
                  <polyline points="1,2 3,4 8,1" fill="none" data-wp-fill="locked" stroke="var(--wp-error)" data-wp-stroke="error" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
                  <text x="12" y="20" font-size="4" font-family="Roboto, sans-serif" text-anchor="middle" fill="#ff00aa" data-wp-fill="locked">HQ</text>
                </svg>
                """);

        assertThat(clean).contains("var(--wp-primary)");
        assertThat(clean).contains("data-wp-fill=\"primary\"");
        assertThat(clean).contains("var(--wp-error)");
        assertThat(clean).contains("data-wp-stroke=\"error\"");
        assertThat(clean).contains("fill=\"#ff00aa\"");
        assertThat(clean).contains(">HQ<");
        assertThat(clean).doesNotContain("<script");
    }

    @Test
    void addsTheRoleForATokenPaint() {
        String clean = SvgIconSanitizer.sanitize(
                "<svg viewBox=\"0 0 16 16\"><circle cx=\"8\" cy=\"8\" r=\"6\" fill=\"var(--wp-primary)\"/></svg>");

        assertThat(clean).contains("data-wp-fill=\"primary\"");
        assertThat(clean).contains("var(--wp-primary)");
    }

    @Test
    void rejectsScriptsHandlersAndUnknownTokens() {
        assertThatThrownBy(() -> SvgIconSanitizer.sanitize("<svg><script>alert(1)</script></svg>"))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> SvgIconSanitizer.sanitize("<svg><rect width=\"4\" height=\"4\" onclick=\"alert(1)\"/></svg>"))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> SvgIconSanitizer.sanitize("<svg><rect width=\"4\" height=\"4\" fill=\"var(--wp-nope)\"/></svg>"))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> SvgIconSanitizer.sanitize("<!DOCTYPE svg [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]><svg>&xxe;</svg>"))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> SvgIconSanitizer.sanitize("<svg><image href=\"https://example.test/a.png\"/></svg>"))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> SvgIconSanitizer.sanitize("<svg><foreignObject width=\"4\" height=\"4\"/></svg>"))
                .isInstanceOf(BadRequestException.class);
    }
}
