package dev.jevcraft.inference;

import java.util.Map;

public sealed interface Answer permits Answer.Choice, Answer.Score, Answer.BooleanProbability {
    record Choice(String choice, Map<String, Double> probabilities, Double confidence) implements Answer {
        public Choice { probabilities = probabilities == null ? Map.of() : Map.copyOf(probabilities); }
    }
    record Score(double score, Map<String, Double> probabilities, Double confidence) implements Answer {
        public Score { probabilities = probabilities == null ? Map.of() : Map.copyOf(probabilities); }
    }
    record BooleanProbability(double probability) implements Answer {
        public BooleanProbability {
            if (probability < 0 || probability > 1) throw new IllegalArgumentException("probability outside [0,1]");
        }
    }
}
