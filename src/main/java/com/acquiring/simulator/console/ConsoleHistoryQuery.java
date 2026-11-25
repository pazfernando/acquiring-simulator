package com.acquiring.simulator.console;

import java.util.Objects;

/**
 * The two ways a history page can be requested (D-6/R-2): start a fixed new snapshot, or continue an
 * opaque signed cursor.
 */
public sealed interface ConsoleHistoryQuery
        permits ConsoleHistoryQuery.NewSnapshot, ConsoleHistoryQuery.Continue {

    /** Start at a fixed snapshot and cover everything after {@code afterSequence}. */
    record NewSnapshot(long afterSequence) implements ConsoleHistoryQuery {

        public NewSnapshot {
            if (afterSequence < 0) {
                throw new IllegalArgumentException("afterSequence cannot be negative");
            }
        }
    }

    /** Continue the page chain from a signed cursor. */
    record Continue(String opaqueCursor) implements ConsoleHistoryQuery {

        public Continue {
            Objects.requireNonNull(opaqueCursor, "opaqueCursor");
            if (opaqueCursor.isEmpty()) {
                throw new IllegalArgumentException("opaqueCursor cannot be empty");
            }
        }
    }
}
