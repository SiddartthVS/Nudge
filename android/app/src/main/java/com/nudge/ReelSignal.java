package com.nudge;

import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Decides whether the currently visible reel/short is actually a DIFFERENT one from the
 * last one we counted, instead of trusting every TYPE_VIEW_SCROLLED / content-changed event.
 *
 * WHY THIS EXISTS
 * ----------------
 * Android fires TYPE_VIEW_SCROLLED repeatedly while a finger is dragging the feed - including
 * while the drag is held in place, or released back onto the same reel. Counting those events
 * 1:1 (the original approach) makes the counter climb while nothing on screen has changed.
 *
 * THE FIX
 * -------
 * Ported from the open-source Curbox project's reel counter
 * (neth.iecal.curbox.trackers.ReelsCountTracker / hardcoded.ReelAppConfig), simplified from
 * Curbox's general-purpose node-selector scripting language down to plain
 * AccessibilityNodeInfo calls for exactly our four apps. No OCR, no ML, no video analysis -
 * just reading a small piece of on-screen text (caption/author/content node) that identifies
 * the reel currently on screen, and comparing it to the last one we saw:
 *
 *   1. Locate the reel viewer for the app. If it isn't on screen, we're not looking at a
 *      reel at all - no count, and we forget what we last saw so the next reel always counts.
 *   2. Read a small comparator string from the caption/author/content node's subtree.
 *   3. Compare it to the previous comparator with a word-overlap ratio. Two comparators that
 *      still share >=90% of their words are treated as "the same reel" - this is what absorbs
 *      a held/aborted scroll, since the caption/author on screen hasn't actually changed.
 *   4. A small per-app "recently seen" cache (last 50 reels) stops a double count if the user
 *      scrolls back up to a reel they were already just on.
 *
 * One instance of this class is owned by TrackerService and lives exactly as long as it does.
 */
final class ReelSignal {

    private static final String TAG = "NudgeTracker";

    /** Word-overlap below this ratio counts as a genuinely different reel. */
    private static final float SAME_REEL_OVERLAP_THRESHOLD = 0.90f;

    /** How many recent reels per app we remember, to avoid double-counting a revisit. */
    private static final int SEEN_CACHE_SIZE = 5;

    /** Bound on findByDescription's manual tree walk (Facebook has no stable resource id). */
    private static final int MAX_DESC_SEARCH_NODES = 3000;

    private interface Extractor {
        /**
         * Returns null when the current screen is not a reel screen, "" when it is a reel
         * screen but the comparator content hasn't loaded yet, or the comparator text itself.
         */
        String extract(AccessibilityNodeInfo root, String pkg);
    }

    private static final class PackageConfig {
        final int eventTypeMask;
        final Extractor extractor;

        PackageConfig(int eventTypeMask, Extractor extractor) {
            this.eventTypeMask = eventTypeMask;
            this.extractor = extractor;
        }
    }

    private static final Map<String, PackageConfig> CONFIGS = new HashMap<>();

    static {
        CONFIGS.put("com.instagram.android",
                new PackageConfig(AccessibilityEvent.TYPE_VIEW_SCROLLED, ReelSignal::extractInstagram));
        CONFIGS.put("com.google.android.youtube",
                new PackageConfig(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED, ReelSignal::extractYoutube));
        CONFIGS.put("com.facebook.katana",
                new PackageConfig(AccessibilityEvent.TYPE_VIEW_SCROLLED, ReelSignal::extractFacebook));
        CONFIGS.put("com.snapchat.android",
                new PackageConfig(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED, ReelSignal::extractSnapchat));
    }

    /** Last comparator text seen per app, "" if none yet. */
    private final Map<String, String> lastComparator = new HashMap<>();

    /** Per-app bounded cache of recently counted comparator texts. */
    private final Map<String, LinkedHashMap<String, Boolean>> seenCache = new HashMap<>();

    /** True if this event type is one we should react to for this app. */
    boolean isCandidateEvent(String pkg, int eventType) {
        PackageConfig cfg = CONFIGS.get(pkg);
        return cfg != null && (eventType & cfg.eventTypeMask) != 0;
    }

    /**
     * Reads the screen now and decides whether the visible reel is a new one.
     * Called both right after an event AND again once events stop (the "settle" check),
     * because the last event of a swipe often fires before the new reel's text is on screen.
     *
     * @return true if a genuinely new reel should be counted.
     */
    boolean isNewReel(String pkg, AccessibilityNodeInfo root) {
        PackageConfig cfg = CONFIGS.get(pkg);
        if (cfg == null || root == null) {
            return false;
        }

        String comparator;
        try {
            comparator = cfg.extractor.extract(root, pkg);
        } catch (Exception e) {
            Log.e(TAG, "Reel extraction failed for " + pkg, e);
            return false;
        }

        if (comparator == null) {
            // Reel viewer not found/visible. During a swipe the viewer's nodes can vanish for a
            // moment, so we deliberately KEEP the last reel instead of forgetting it. Forgetting
            // made the next reel look like the "first" one and skip its count.
            return false;
        }

        String currentText = comparator.trim();
        String previousText = lastComparator.containsKey(pkg) ? lastComparator.get(pkg) : "";

        if (currentText.isEmpty() || currentText.equals(previousText)) {
            // Reel screen visible but comparator hasn't loaded, or truly nothing changed.
            return false;
        }

        boolean substantial = isSubstantialTextChange(currentText, previousText);
        boolean counted = false;

        if (substantial) {
            LinkedHashMap<String, Boolean> seen = seenCache.get(pkg);
            if (seen == null) {
                seen = newSeenCache();
                seenCache.put(pkg, seen);
            }
            if (!seen.containsKey(currentText)) {
                seen.put(currentText, Boolean.TRUE);
                counted = true;
            }
        }

        // Keep tracking progressively-loading text even when it wasn't (yet) a substantial
        // change, so we compare against the fullest version once it settles.
        if (substantial || currentText.length() > previousText.length()) {
            lastComparator.put(pkg, currentText);
        }

        Log.i(TAG, "REEL CHECK | " + pkg + " | substantial=" + substantial + " | counted=" + counted
                + " | prev=\"" + abbreviate(previousText) + "\" | cur=\"" + abbreviate(currentText) + "\"");

        return counted;
    }

    private static String abbreviate(String s) {
        String flat = s.replace('\n', ' ');
        return flat.length() <= 40 ? flat : flat.substring(0, 40) + "...";
    }

    private static LinkedHashMap<String, Boolean> newSeenCache() {
        return new LinkedHashMap<String, Boolean>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                return size() > SEEN_CACHE_SIZE;
            }
        };
    }

    // --------------------------------------------------------------- per-app extractors

    private static String extractInstagram(AccessibilityNodeInfo root, String pkg) {
        AccessibilityNodeInfo viewer = firstById(root, pkg + ":id/clips_viewer_view_pager");
        AccessibilityNodeInfo controls = firstById(root, pkg + ":id/clips_ufi_component");
        boolean onScreen = isVisible(viewer) && isVisible(controls);
        safeRecycle(viewer);
        safeRecycle(controls);
        if (!onScreen) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        AccessibilityNodeInfo caption = firstById(root, pkg + ":id/clips_captions_component");
        if (caption != null) {
            sb.append(subtreeText(caption, 32, 20000));
            safeRecycle(caption);
        }
        AccessibilityNodeInfo author = firstById(root, pkg + ":id/clips_author_username");
        if (author != null) {
            sb.append(subtreeText(author, 32, 20000));
            safeRecycle(author);
        }
        return sb.toString();
    }

    private static String extractYoutube(AccessibilityNodeInfo root, String pkg) {
        AccessibilityNodeInfo viewer = firstById(root, pkg + ":id/reel_recycler");
        boolean onScreen = isVisible(viewer);
        safeRecycle(viewer);
        if (!onScreen) {
            return null;
        }

        AccessibilityNodeInfo content = firstById(root, pkg + ":id/reel_player_page_content");
        if (content == null) {
            return "";
        }
        String text = subtreeText(content, 32, 20000);
        safeRecycle(content);
        return cleanYoutubeComparator(text);
    }

    /**
     * YouTube's content node carries a lot of fixed chrome text (nav bar labels, "Video
     * Progress", etc.) alongside the caption. Strip the boilerplate so it doesn't drown out
     * genuine caption changes, matching Curbox's YouTube cleanser.
     */
    private static String cleanYoutubeComparator(String value) {
        String compact = value.replace("\n", "");
        if (compact.contains("PostPostPostlike") || compact.length() <= 15) {
            return "";
        }
        return compact.replace("Video Progress", "")
                .replace("Tap to watch live", "")
                .replace("Go to channel", "")
                .replace("soundVideo ProgressSearchMoreHomeHomeShortsShortsCreateSubscriptions", "")
                .replace("soundSearchMoreHomeHomeShortsShortsCreateSubscriptions", "");
    }

    private static String extractFacebook(AccessibilityNodeInfo root, String pkg) {
        AccessibilityNodeInfo reel = findByDescription(root, "Reels tab details", MAX_DESC_SEARCH_NODES);
        boolean onScreen = isVisible(reel);
        if (!onScreen) {
            safeRecycle(reel);
            return null;
        }

        AccessibilityNodeInfo parent = reel.getParent();
        safeRecycle(reel);
        if (parent == null) {
            return "";
        }
        String text = subtreeText(parent, 16, 4000);
        safeRecycle(parent);
        return text;
    }

    private static String extractSnapchat(AccessibilityNodeInfo root, String pkg) {
        AccessibilityNodeInfo viewer = firstById(root, pkg + ":id/spotlight_container");
        boolean onScreen = isVisible(viewer);
        safeRecycle(viewer);
        if (!onScreen) {
            return null;
        }

        AccessibilityNodeInfo content = firstById(root, pkg + ":id/opera_viewer");
        if (content == null) {
            return "";
        }
        String text = subtreeText(content, 32, 20000);
        safeRecycle(content);
        return text;
    }

    // ------------------------------------------------------------------ node tree helpers

    private static AccessibilityNodeInfo firstById(AccessibilityNodeInfo root, String id) {
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
        if (nodes == null || nodes.isEmpty()) {
            return null;
        }
        // Reel pagers keep neighbouring pages attached, so the same id can match the reel
        // above/below the one on screen. Taking nodes.get(0) blindly could read the wrong
        // reel's caption, so prefer the first node that is actually visible.
        AccessibilityNodeInfo chosen = null;
        for (AccessibilityNodeInfo node : nodes) {
            if (chosen == null && node != null && node.isVisibleToUser()) {
                chosen = node;
            } else {
                safeRecycle(node);
            }
        }
        return chosen;
    }

    /**
     * Bounded depth-first search for a node whose contentDescription matches exactly.
     * Facebook's Reels tab has no stable resource id, unlike the other three apps.
     * Rejected nodes are recycled; nodes on the path to a match are left for the framework to
     * reclaim (a handful of nodes at most, bounded by tree depth) rather than chasing a strict
     * recycle discipline the framework no longer requires on modern Android versions.
     */
    private static AccessibilityNodeInfo findByDescription(AccessibilityNodeInfo root, String desc, int maxNodes) {
        if (root == null) {
            return null;
        }
        int[] visited = {0};
        return dfsDescription(root, desc, maxNodes, visited);
    }

    private static AccessibilityNodeInfo dfsDescription(
            AccessibilityNodeInfo node, String desc, int maxNodes, int[] visited) {
        if (node == null || visited[0] >= maxNodes) {
            return null;
        }
        visited[0]++;

        CharSequence contentDescription = node.getContentDescription();
        if (contentDescription != null && desc.contentEquals(contentDescription)) {
            return node;
        }

        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            if (visited[0] >= maxNodes) {
                return null;
            }
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) {
                continue;
            }
            AccessibilityNodeInfo found = dfsDescription(child, desc, maxNodes, visited);
            if (found != null) {
                return found;
            }
            safeRecycle(child);
        }
        return null;
    }

    private static boolean isVisible(AccessibilityNodeInfo node) {
        return node != null && node.isVisibleToUser();
    }

    /**
     * Concatenates a node's own text/contentDescription with the same from its descendants,
     * depth- and length-bounded. Ported from Curbox's UiHiderRuntime#collectSubtreeText.
     */
    private static String subtreeText(AccessibilityNodeInfo node, int maxDepth, int maxChars) {
        StringBuilder out = new StringBuilder(Math.min(maxChars, 1024));
        appendNodeText(node, out, maxChars);
        if (out.length() >= maxChars || maxDepth == 0) {
            return out.toString();
        }
        visitChildrenText(node, 1, maxDepth, out, maxChars);
        return out.toString();
    }

    private static void visitChildrenText(
            AccessibilityNodeInfo parent, int depth, int maxDepth, StringBuilder out, int maxChars) {
        if (depth > maxDepth || out.length() >= maxChars) {
            return;
        }
        int childCount = parent.getChildCount();
        for (int i = 0; i < childCount; i++) {
            if (out.length() >= maxChars) {
                break;
            }
            AccessibilityNodeInfo child = parent.getChild(i);
            if (child == null) {
                continue;
            }
            try {
                appendNodeText(child, out, maxChars);
                if (depth < maxDepth && out.length() < maxChars) {
                    visitChildrenText(child, depth + 1, maxDepth, out, maxChars);
                }
            } finally {
                safeRecycle(child);
            }
        }
    }

    private static void appendNodeText(AccessibilityNodeInfo node, StringBuilder out, int maxChars) {
        CharSequence textCs = node.getText();
        String text = textCs == null ? null : textCs.toString();
        appendTextPart(text, out, maxChars);

        CharSequence descCs = node.getContentDescription();
        String desc = descCs == null ? null : descCs.toString();
        if (desc != null && !desc.equals(text)) {
            appendTextPart(desc, out, maxChars);
        }
    }

    private static void appendTextPart(String value, StringBuilder out, int maxChars) {
        if (value == null) {
            return;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || out.length() >= maxChars) {
            return;
        }
        if (out.length() > 0) {
            out.append('\n');
            if (out.length() >= maxChars) {
                return;
            }
        }
        int remaining = maxChars - out.length();
        if (remaining > 0) {
            out.append(trimmed, 0, Math.min(trimmed.length(), remaining));
        }
    }

    private static void safeRecycle(AccessibilityNodeInfo node) {
        if (node == null) {
            return;
        }
        try {
            //noinspection deprecation
            node.recycle();
        } catch (Exception ignored) {
        }
    }

    // ----------------------------------------------------------------- comparator similarity

    /**
     * True when the two comparator strings share less than 90% of their words - i.e. this is
     * genuinely a different reel, not the same one re-observed mid-drag.
     * Ported from Curbox's ReelsCountTracker#isSubstantialTextChange.
     */
    private static boolean isSubstantialTextChange(String currentText, String previousText) {
        if (currentText.isEmpty() || previousText.isEmpty()) {
            return true;
        }

        Map<String, Integer> currentWords = wordCounts(currentText);
        Map<String, Integer> previousWords = wordCounts(previousText);
        if (currentWords.isEmpty() || previousWords.isEmpty()) {
            return true;
        }

        boolean currentIsSmaller = currentWords.size() < previousWords.size();
        Map<String, Integer> smaller = currentIsSmaller ? currentWords : previousWords;
        Map<String, Integer> larger = currentIsSmaller ? previousWords : currentWords;

        int intersectionSize = 0;
        int totalSmaller = 0;
        for (Map.Entry<String, Integer> entry : smaller.entrySet()) {
            int count = entry.getValue();
            totalSmaller += count;
            Integer largerCount = larger.get(entry.getKey());
            intersectionSize += Math.min(count, largerCount == null ? 0 : largerCount);
        }

        if (totalSmaller == 0) {
            return true;
        }

        float overlapRatio = (float) intersectionSize / (float) totalSmaller;
        return overlapRatio < SAME_REEL_OVERLAP_THRESHOLD;
    }

    private static Map<String, Integer> wordCounts(String text) {
        Map<String, Integer> counts = new HashMap<>();
        int len = text.length();
        int start = -1;
        for (int i = 0; i < len; i++) {
            if (Character.isWhitespace(text.charAt(i))) {
                if (start != -1) {
                    String word = text.substring(start, i);
                    Integer existing = counts.get(word);
                    counts.put(word, existing == null ? 1 : existing + 1);
                    start = -1;
                }
            } else if (start == -1) {
                start = i;
            }
        }
        if (start != -1) {
            String word = text.substring(start, len);
            Integer existing = counts.get(word);
            counts.put(word, existing == null ? 1 : existing + 1);
        }
        return counts;
    }
}
