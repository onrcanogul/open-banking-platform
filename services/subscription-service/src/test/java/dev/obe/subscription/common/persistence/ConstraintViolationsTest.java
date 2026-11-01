package dev.obe.subscription.common.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class ConstraintViolationsTest {

    private static final String CONSTRAINT = "UQ_SAMPLE";

    @Test
    void matchesNameWithSchemaPrefix() {
        assertThat(ConstraintViolations.isViolationOf(violationOf("OBE.UQ_SAMPLE"), CONSTRAINT)).isTrue();
    }

    @Test
    void doesNotMatchAnotherConstraint() {
        assertThat(ConstraintViolations.isViolationOf(violationOf("OBE.PK_SAMPLE"), CONSTRAINT)).isFalse();
    }

    @Test
    void doesNotMatchWhenNameIsUnknown() {
        assertThat(ConstraintViolations.isViolationOf(violationOf(null), CONSTRAINT)).isFalse();
    }

    @Test
    void doesNotMatchOtherCauses() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException("value too large");

        assertThat(ConstraintViolations.isViolationOf(ex, CONSTRAINT)).isFalse();
    }

    private DataIntegrityViolationException violationOf(String constraintName) {
        ConstraintViolationException cause =
                new ConstraintViolationException("violation", new SQLException(), constraintName);
        return new DataIntegrityViolationException("violation", cause);
    }
}
