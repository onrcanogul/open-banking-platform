package dev.obe.disbursement.infrastructure.batch;

import dev.obe.disbursement.application.result.FindingResult;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Reads what the job wrote. It uses the same plain SQL, as the table has no entity. */
@Component
public class FindingQuery {

    private static final String FIND_LATEST = """
            SELECT disbursement_no, mismatch_type, our_status, core_transfer, correlation_id, found_at
            FROM disbursement_mismatch
            ORDER BY found_at DESC, disbursement_no
            OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
            """;

    private final JdbcTemplate jdbc;

    FindingQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<FindingResult> latest(int page, int size) {
        return jdbc.query(FIND_LATEST, (row, number) -> new FindingResult(row.getString("disbursement_no"),
                row.getString("mismatch_type"), row.getString("our_status"), row.getInt("core_transfer") == 1,
                row.getString("correlation_id"),
                row.getObject("found_at", LocalDateTime.class).toInstant(ZoneOffset.UTC)), (long) page * size, size);
    }
}
