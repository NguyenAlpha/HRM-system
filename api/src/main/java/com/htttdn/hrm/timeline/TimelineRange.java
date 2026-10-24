package com.htttdn.hrm.timeline;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** How far back a statistics chart goes and how wide each of its points is. */
public enum TimelineRange {
    /** The last 60 minutes, one point per minute. */
    HOUR(60, 60),
    /** The last 24 hours, one point per 15 minutes. */
    DAY(15 * 60, 96),
    /** The last 7 days, one point per hour. */
    WEEK(60 * 60, 168);

    private final long slotSeconds;
    private final int slots;

    TimelineRange(long slotSeconds, int slots) {
        this.slotSeconds = slotSeconds;
        this.slots = slots;
    }

    public long slotSeconds() {
        return slotSeconds;
    }

    public int slots() {
        return slots;
    }

    /** When each slot of the range begins, oldest first, the last one being the slot {@code now} falls in. */
    public List<Instant> slotStarts(Instant now) {
        long firstSlot = now.getEpochSecond() / slotSeconds - slots + 1;
        List<Instant> starts = new ArrayList<>(slots);
        for (int i = 0; i < slots; i++) {
            starts.add(Instant.ofEpochSecond((firstSlot + i) * slotSeconds));
        }
        return starts;
    }
}
