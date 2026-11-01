package dev.obe.subscription.application.port.out;

/** Allows one undelivered events query per period for each YOS. */
public interface UndeliveredQueryLimiter {

    /** Returns 0 when the query may run, or else the seconds to wait before asking again. */
    long acquire(String yosCode);

    /** Gives the allowance back, for a query that failed and so returned nothing to the YOS. */
    void release(String yosCode);
}
