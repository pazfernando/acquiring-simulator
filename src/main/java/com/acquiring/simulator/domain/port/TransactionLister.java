package com.acquiring.simulator.domain.port;

import com.acquiring.simulator.domain.model.AuthorizationRecord;
import java.time.Instant;
import java.util.List;

/**
 * Bounded, read-only listing of journal authorizations for the presenter history view.
 *
 * <p>The authoritative record remains the journal row; this port only discovers which operations
 * exist so their detail can be read through {@link AuthorizationRepository}. It returns no payment
 * decision of its own and performs no write.</p>
 *
 * <p>Production listing needs an indexed read model; the local presenter implementation performs a
 * bounded scan and is documented as a demo limitation, never as approved capacity.</p>
 */
public interface TransactionLister {

    /**
     * Read up to {@code maxRecords} authorization records received no earlier than {@code since}.
     *
     * @param since inclusive lower bound on {@code firstReceivedAt}
     * @param maxRecords positive hard bound for one page
     * @return immutable records in no guaranteed order
     */
    List<AuthorizationRecord> recentAuthorizations(Instant since, int maxRecords);
}
