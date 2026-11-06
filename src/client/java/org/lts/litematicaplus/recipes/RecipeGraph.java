package org.lts.litematicaplus.recipes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RecipeGraph<T> {
    public enum Kind {
        CRAFTING,
        STONECUTTER,
        SMELTING
    }

    public record Input<T>(List<T> choices, int count) {
        public Input {
            choices = List.copyOf(choices);
        }
    }

    public record Option<T>(T output, int count, Kind kind, List<Input<T>> inputs) {
        public Option {
            inputs = List.copyOf(inputs);
        }

        public long inputCount() {
            long sum = 0L;
            for (Input<T> input : this.inputs) {
                sum += input.count();
            }
            return sum;
        }
    }

    private static final RecipeGraph<?> EMPTY = new RecipeGraph<>(Map.of(), 0);

    private final Map<T, List<Option<T>>> byOutput;
    private final int size;

    private RecipeGraph(Map<T, List<Option<T>>> byOutput, int size) {
        this.byOutput = byOutput;
        this.size = size;
    }

    @SuppressWarnings("unchecked")
    public static <T> RecipeGraph<T> empty() {
        return (RecipeGraph<T>) EMPTY;
    }

    public List<Option<T>> options(T item) {
        List<Option<T>> options = this.byOutput.get(item);
        return options == null ? List.of() : options;
    }

    public boolean producible(T item) {
        return this.byOutput.containsKey(item);
    }

    public int size() {
        return this.size;
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    public static final class Builder<T> {
        private final Map<T, Set<Option<T>>> byOutput = new HashMap<>();

        private Builder() {
        }

        public Builder<T> add(Option<T> option) {
            if (option == null || option.count() <= 0 || option.inputs().isEmpty()) {
                return this;
            }
            for (Input<T> input : option.inputs()) {
                if (input.count() <= 0 || input.choices().isEmpty()) {
                    return this;
                }
            }
            this.byOutput.computeIfAbsent(option.output(), ignored -> new LinkedHashSet<>()).add(option);
            return this;
        }

        public RecipeGraph<T> build() {
            Map<T, List<Option<T>>> result = new HashMap<>();
            int size = 0;
            for (Map.Entry<T, Set<Option<T>>> entry : this.byOutput.entrySet()) {
                result.put(entry.getKey(), List.copyOf(new ArrayList<>(entry.getValue())));
                size += entry.getValue().size();
            }
            return new RecipeGraph<>(result, size);
        }
    }
}
