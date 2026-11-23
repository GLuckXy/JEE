package com.aiinterview.common;

import java.time.*;
import java.util.UUID;

public final class Values {
    private Values() {}
    public static String id() { return UUID.randomUUID().toString(); }
    public static LocalDateTime now() { return LocalDateTime.now(ZoneOffset.UTC); }
}
