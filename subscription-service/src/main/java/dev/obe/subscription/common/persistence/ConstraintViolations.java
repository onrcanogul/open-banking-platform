package dev.obe.subscription.common.persistence;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

public final class ConstraintViolations {

    private ConstraintViolations() {
    }

    /** Oracle reports the name with a schema prefix, so the match is on the end of the name. */
    public static boolean isViolationOf(DataIntegrityViolationException ex, String constraintName) {
        return ex.getCause() instanceof ConstraintViolationException violation
                && violation.getConstraintName() != null
                && violation.getConstraintName().toUpperCase().endsWith(constraintName);
    }
}
