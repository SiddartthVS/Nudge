package com.nudge;

import java.util.Random;

/**
 * The short lines the HUD shows instead of the reel count while an app is blocked (see
 * BlockState and TrackerService.updateHud). Hardcoded on purpose: no network, no settings.
 *
 * Keep every line short (12 characters or fewer) - the HUD shows it on a single line at a small
 * text size, so a long line would run off the edge of the screen. To add or change a line, edit
 * MESSAGES below; nothing else needs to change.
 */
final class HudMessages {

    private static final String[] MESSAGES = {
            "Got u!",
            "No reels!",
            "Go study!",
            "Nice try!",
            "Not today!",
            "Nope!",
            "Focus up!",
            "Busted!",
            "Eyes up!",
            "Not now!",
            "Go outside!",
            "Do it later!",
    };

    private static final Random RANDOM = new Random();

    /** Index of the line handed out last, so the same line never shows twice in a row. */
    private static int lastIndex = -1;

    private HudMessages() {
    }

    /** A random line, never the same one as the previous call. */
    static synchronized String next() {
        int index = RANDOM.nextInt(MESSAGES.length);
        if (index == lastIndex) {
            // Step forward by 1..(length-1) places, which can never land back on lastIndex.
            index = (index + 1 + RANDOM.nextInt(MESSAGES.length - 1)) % MESSAGES.length;
        }
        lastIndex = index;
        return MESSAGES[index];
    }
}
