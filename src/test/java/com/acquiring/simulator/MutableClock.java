package com.acquiring.simulator;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Controllable clock so window and timeout boundaries need no wall-clock sleeps. */
public final class MutableClock extends Clock {

    private volatile Instant current;
    private final ZoneId zone;

    public MutableClock(Instant start) {
        this(start, ZoneOffset.UTC);
    }

    private MutableClock(Instant start, ZoneId zone) {
        this.current = start;
        this.zone = zone;
    }

    public void setInstant(Instant instant) {
        this.current = instant;
    }

    public void advance(Duration duration) {
        this.current = this.current.plus(duration);
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId newZone) {
        return new MutableClock(current, newZone);
    }

    @Override
    public Instant instant() {
        return current;
    }
}
