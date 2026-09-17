package dev.jevcraft.inference;

import java.util.List;
import java.util.Map;

public sealed interface Question permits Question.Choice, Question.Score, Question.BooleanProbability {
    String instructions();

    record Choice(String instructions, Map<String, Object> criteria) implements Question {
        public Choice {
            if (instructions == null || instructions.isBlank()) throw new IllegalArgumentException("instructions required");
            criteria = Map.copyOf(criteria);
            if (criteria.isEmpty() || criteria.size() > 255) throw new IllegalArgumentException("choice needs 1..255 criteria");
        }
    }

    record Score(String instructions, List<Object> criteria) implements Question {
        public Score {
            if (instructions == null || instructions.isBlank()) throw new IllegalArgumentException("instructions required");
            criteria = List.copyOf(criteria);
            if (criteria.size() < 2) throw new IllegalArgumentException("score needs at least two levels");
        }
    }

    record BooleanProbability(String instructions, Map<String, Object> criteria) implements Question {
        public BooleanProbability {
            if (instructions == null || instructions.isBlank()) throw new IllegalArgumentException("instructions required");
            criteria = criteria == null ? Map.of() : Map.copyOf(criteria);
        }
    }
}
