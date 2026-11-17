package com.drdo.jayant.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DatabaseNameTest {

    @Test
    void acceptsAPlainIdentifier() {
        assertThat(DatabaseName.requireIdentifier("jayant")).isEqualTo("jayant");
    }

    @Test
    void rejectsEmptyUnsafeAndNullNames() {
        assertThatThrownBy(() -> DatabaseName.requireIdentifier(null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> DatabaseName.requireIdentifier(""))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> DatabaseName.requireIdentifier("jayant;drop"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> DatabaseName.requireIdentifier("1jayant"))
                .isInstanceOf(IllegalStateException.class);
    }
}
