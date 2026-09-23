package com.nudge;

import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Decides whether the reel currently on screen is genuinely a NEW one, instead of trusting
 * every scroll/content-change event. See Overview.md, section 1, for the full explanation of
 * why events alone aren't enough and how the comparison works.
 *
 * In short: for each app, read a small piece of on-screen text that identifies the current
 * reel (its caption/author/content node), then compare it to the last text seen. If less than
 * 90% of the words match, it's a new reel. Ported and simplified from the open-source Curbox
 * project (neth.iecal.curbox.trackers.ReelsCountTracker).
 *
 * One instance is owned by TrackerService and lives exactly as long as it does.
 */
final class ReelSignal {

    private static final String TAG = "NudgeTracker";

    /** Below this word-overlap ratio, two comparators count as different reels. */
    private static final float SAME_REEL_OVERLAP_THRESHOLD = 0.90f;

    /** How many recent reels per app we remember, so scrolling back doesn't double-count. */
    private static final int SEEN_CACHE_SIZE = 5;

    /** Safety cap on Facebook's manual tree search (it has no stable resource id to search by). */
    private static final int MAX_DESC_SEARCH_NODES = 3000;

    private interface Extractor {
        /**
         * null = not on a reel screen. "" = on a reel screen but the text hasn't loaded yet.
         * Otherwise, the comparator text for whatever reel is currently visible.
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

    // One entry per monitored app: which event type to react to, and how to read its screen.
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

    /** Last comparator text seen per app ("" if none yet). */
    private final Map<String, String> lastComparator = new HashMap<>();

    /** Per-app bounded cache of recently counted comparator texts, to avoid double-counting. */
    private final Map<String, LinkedHashMap<String, Boolean>> seenCache = new HashMap<>();

    /** True if this event type is one we care about for this app. */
    boolean isCandidateEvent(String pkg, int eventType) {
        PackageConfig cfg = CONFIGS.get(pkg);
        return cfg != null && (eventType & cfg.eventTypeMask) != 0;
    }

    /**
     * Reads the screen right now and decides whether the visible reel is a new one.
     * Called both right after an event and again once events settle - see TrackerService's
     * "settle check" for why a single call isn't always enough.
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
            // Reel viewer not visible right now. Deliberately keep the last comparator instead
            // of clearing it, since the viewer can briefly disappear mid-swipe.
            return false;
        }

        String currentText = comparator.trim();
        String previousText = lastComparator.containsKey(pkg) ? lastComparator.get(pkg) : "";

        if (currentText.isEmpty() || currentText.equals(previousText)) {
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

        // Keep tracking text even when it wasn't (yet) a substantial change, so we compare
        // against the fullest version once a slowly-loading caption settles.
        if (substantial || currentText.length() > previousText.length()) {
            lastComparator.put(pkg, currentText);
        }

        Log.i(TAG, "REEL CHECK | " + pkg + " | substantial=" + substantial + " | counted=" + counted
                + " | prev=\"" + abbreviate(previousText) + "\" | cur=\"" + abbreviate(currentText) + "\"");

        return counted;
    }

    /** Shortens a comparator for logging so logcat stays readable. */
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

    /** Instagram: only a reel screen if both the pager and its caption bar are visible. */
    private static String extractInstagram(AccessibilityNodeInfo root, String pkg) {
        AccessibilityNodeInfo viewer = firstById(root, pkg + ":id/clips_viewer_view_pager");
        AccessibilityNodeInfo controls = firstById(root, pkg + ":id/clips_ufi_component");
        boolean onScreen = isVisible(viewer) && isVisible(controls);
        safeRecycle(viewer);
        safeRecycle(controls);
        if (!onScreen) {
            return null;
        }

        // Comparator = caption text + author username.
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

    /** YouTube Shorts: comparator is the content node's text, with fixed UI chrome stripped. */
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

    /** Strips YouTube's fixed nav/menu text so it doesn't drown out real caption changes. */
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

    /** Facebook Reels: no stable id, so search by content-description instead. */
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

    /** Snapchat Spotlight: comparator is the content viewer's text. */
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

    /**
     * Finds the first VISIBLE node with this resource id. A pager can keep neighbouring
     * (off-screen) pages attached, so the first match in the list isn't always the one
     * actually on screen.
     */
    private static AccessibilityNodeInfo firstById(AccessibilityNodeInfo root, String id) {
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
        if (nodes == null || nodes.isEmpty()) {
            return null;
        }
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

    /** Bounded depth-first search for a node with an exact content-description match. */
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

    /** Collects a node's own text/description plus its descendants', depth- and length-bounded. */
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

    /** recycle() is deprecated but harmless to call; wrapped since it can throw on some OEMs. */
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

    /** True when less than 90% of the two texts' words overlap - i.e. genuinely a new reel. */
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

    /** Splits on whitespace and counts occurrences of each word. */
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
