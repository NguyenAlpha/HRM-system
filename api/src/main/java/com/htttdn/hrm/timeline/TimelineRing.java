package com.htttdn.hrm.timeline;

import java.util.Arrays;

/**
 * A few counters per time slot over one {@link TimelineRange}, in memory. The slots form a ring of
 * fixed size, so memory stays constant however long the backend runs; a slot is cleared when its
 * time comes round again.
 */
public final class TimelineRing {

    private final long slotSeconds;
    private final long[] slotIds;
    /** {@code counters[counter][slot]}. */
    private final long[][] counters;

    public TimelineRing(TimelineRange range, int counterCount) {
        slotSeconds = range.slotSeconds();
        slotIds = new long[range.slots()];
        counters = new long[counterCount][range.slots()];
        Arrays.fill(slotIds, -1);
    }

    /** Adds one amount per counter to the slot {@code epochSecond} falls in. */
    public synchronized void add(long epochSecond, long... amounts) {
        long slotId = epochSecond / slotSeconds;
        int index = (int) (slotId % slotIds.length);
        if (slotIds[index] != slotId) {
            slotIds[index] = slotId;
            for (long[] counter : counters) {
                counter[index] = 0;
            }
        }
        for (int counter = 0; counter < amounts.length; counter++) {
            counters[counter][index] += amounts[counter];
        }
    }

    /**
     * Adds every slot of the range ending with the one {@code nowSecond} falls in to
     * {@code into[counter][i]}, oldest first. A null row skips that counter.
     */
    public synchronized void addTo(long nowSecond, long[][] into) {
        long firstSlot = nowSecond / slotSeconds - slotIds.length + 1;
        for (int i = 0; i < slotIds.length; i++) {
            long slotId = firstSlot + i;
            int index = (int) (slotId % slotIds.length);
            if (slotIds[index] != slotId) {
                continue;
            }
            for (int counter = 0; counter < counters.length; counter++) {
                if (into[counter] != null) {
                    into[counter][i] += counters[counter][index];
                }
            }
        }
    }
}
