package com.timetables.apisncf;

import java.time.LocalDateTime;

/** A journey between two stations: when it leaves the first one and reaches the last one. */
public record Journey(LocalDateTime departure, LocalDateTime arrival) {
}
