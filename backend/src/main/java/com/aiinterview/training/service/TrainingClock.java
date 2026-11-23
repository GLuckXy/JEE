package com.aiinterview.training.service;

import static com.aiinterview.training.persistence.TrainingRows.Session;

final class TrainingClock {
    static final long LEASE_MS = 12_000;
    // Allow five minutes of ISI audio plus conversion and protocol setup before releasing an abandoned ticket.
    static final long ASR_LIMIT_MS = 375_000;

    private TrainingClock() {}

    static boolean running(Session session, long now) {
        return session.activeAt > 0 && now < session.heartbeatAt + LEASE_MS;
    }

    static long interval(Session session, long now) {
        if (session.activeAt == 0) return 0;
        return Math.max(0, Math.min(now, session.heartbeatAt + LEASE_MS) - session.activeAt);
    }

    static void stop(Session session, long now) {
        long elapsed = interval(session, now);
        session.effectiveMs += elapsed;
        session.roundMs += elapsed;
        session.activeAt = 0;
    }

    static void heartbeat(Session session, long now) {
        if (!running(session, now)) {
            stop(session, now);
            session.activeAt = now;
        }
        session.heartbeatAt = now;
    }

    static void closeAsr(Session session, long now) {
        if (session.asrTicket == null) return;
        session.asrMs += Math.max(0, Math.min(now - session.asrAt, ASR_LIMIT_MS));
        session.asrAt = 0;
        session.asrTicket = null;
    }
}
