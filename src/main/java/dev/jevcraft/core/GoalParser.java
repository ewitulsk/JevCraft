package dev.jevcraft.core;

import java.util.*;
import java.util.regex.*;

public final class GoalParser {
    public enum Kind { ACQUIRE, MOVE, CRAFT, DEPOSIT, FOLLOW, DEFEND, BUILD, EXPLORE, WAIT, INTERACT, STOP, CLARIFY }
    public record Goal(Kind kind, int quantity, String subject, String destination, List<String> clauses) {}
    private static final Pattern QUANTITY = Pattern.compile("(?<!\\w)(\\d{1,4})(?!\\w)");
    private static final Pattern QUOTED = Pattern.compile("\"([^\"]{1,256})\"");
    public Goal parse(String input) {
        String normalized = Objects.requireNonNull(input).strip();
        if (normalized.isEmpty()) return new Goal(Kind.CLARIFY, 0, "", "", List.of());
        List<String> clauses = Arrays.stream(normalized.split("(?i)\\s+(?:and then|then|and)\\s+"))
                .map(String::strip).filter(s -> !s.isEmpty()).toList();
        String lower = normalized.toLowerCase(Locale.ROOT);
        Kind kind = lower.matches("^(stop|cancel|halt).*" ) ? Kind.STOP
                : contains(lower, "put ", "deposit", "store ") ? Kind.DEPOSIT
                : contains(lower, "craft", "make ") ? Kind.CRAFT
                : contains(lower, "mine", "collect", "get ", "gather") ? Kind.ACQUIRE
                : contains(lower, "build", "wall", "floor", "roof", "bridge") ? Kind.BUILD
                : contains(lower, "follow") ? Kind.FOLLOW
                : contains(lower, "defend", "protect") ? Kind.DEFEND
                : contains(lower, "go to", "move to", "walk to") ? Kind.MOVE
                : contains(lower, "explore", "find ") ? Kind.EXPLORE
                : contains(lower, "wait") ? Kind.WAIT : Kind.INTERACT;
        Matcher quantity = QUANTITY.matcher(normalized);
        int amount = quantity.find() ? Integer.parseInt(quantity.group(1)) : 1;
        Matcher quoted = QUOTED.matcher(normalized);
        String subject = quoted.find() ? quoted.group(1) : normalized;
        String destination = "";
        int destinationIndex = lower.lastIndexOf(" in ");
        if (destinationIndex < 0) destinationIndex = lower.lastIndexOf(" into ");
        if (destinationIndex >= 0) destination = normalized.substring(destinationIndex).replaceFirst("(?i)^\\s*(?:in|into)\\s+", "").strip();
        return new Goal(kind, amount, subject, destination, clauses);
    }
    private static boolean contains(String text, String... values) { return Arrays.stream(values).anyMatch(text::contains); }
}
