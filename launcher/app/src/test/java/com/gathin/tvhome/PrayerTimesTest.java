package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** JAKIM's dates and times, read the way the prayer row shows them. */
public class PrayerTimesTest {

    @Test
    public void readsMalayAndEnglishMonths() {
        assertTrue(PrayerTimes.sameDay("31-Okt-2026", 31, 9, 2026));
        assertTrue(PrayerTimes.sameDay("01-Jan-2026", 1, 0, 2026));
        assertTrue(PrayerTimes.sameDay("15-Ogos-2026", 15, 7, 2026));
        assertTrue(PrayerTimes.sameDay("25-Dis-2026", 25, 11, 2026));
        assertTrue(PrayerTimes.sameDay("03-Mac-2026", 3, 2, 2026));
        assertFalse(PrayerTimes.sameDay("31-Okt-2026", 31, 9, 2025));
        assertFalse(PrayerTimes.sameDay("garbage", 1, 0, 2026));
    }

    @Test
    public void timesAndClockFormat() {
        assertEquals(13 * 60 + 3, PrayerTimes.minutes("13:03:00"));
        assertEquals(-1, PrayerTimes.minutes("25:00:00"));
        assertEquals("1:03", PrayerTimes.format(13 * 60 + 3, false));
        assertEquals("13:03", PrayerTimes.format(13 * 60 + 3, true));
        assertEquals("12:05", PrayerTimes.format(5, false));
        // until(Context, int) now uses translated Android resources. The old until(int)
        // API no longer exists; its Malay-only assertions cannot run in this plain JVM suite.
    }

    @Test
    public void nextPrayerAndHijri() {
        PrayerTimes.Day day = new PrayerTimes.Day(new int[]{353, 421, 783, 984, 1141, 1211}, "1448-05-20");
        assertEquals(0, PrayerTimes.next(day, 300));
        assertEquals(2, PrayerTimes.next(day, 700));
        assertEquals(-1, PrayerTimes.next(day, 1211));
        assertEquals("20 Jamadilawal 1448", PrayerTimes.hijri("1448-05-20"));
        assertEquals("", PrayerTimes.hijri("1448-13-01"));
    }
}
