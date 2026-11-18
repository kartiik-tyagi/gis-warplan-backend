package com.gis.warplan.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DatabaseNameTest {

    @Test
    void acceptsAPlainIdentifier() {
        assertThat(DatabaseName.requireIdentifier("gis_warplan")).isEqualTo("gis_warplan");
    }

    @Test
    void rejectsEmptyUnsafeAndNullNames() {
        assertThatThrownBy(() -> DatabaseName.requireIdentifier(null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> DatabaseName.requireIdentifier(""))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> DatabaseName.requireIdentifier("gis_warplan;drop"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> DatabaseName.requireIdentifier("1gis_warplan"))
                .isInstanceOf(IllegalStateException.class);
    }
}
