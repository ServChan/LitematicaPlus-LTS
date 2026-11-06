package org.lts.litematicaplus.recipes;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RawPlanner {
    private static final int MAX_ITEMS = 8192;
    private static final int UNRANKED = Integer.MAX_VALUE;
    private static final int MAX_ATTEMPTS = 8;

    private RawPlanner() {
    }

    public record Settings<T>(boolean smelting, boolean useStock, Set<T> forcedRaw, Map<T, Integer> choices) {
        public Settings {
            forcedRaw = forcedRaw == null ? Set.of() : Set.copyOf(forcedRaw);
            choices = choices == null ? Map.of() : Map.copyOf(choices);
        }

        public static <T> Settings<T> defaults() {
            return new Settings<>(false, true, Set.of(), Map.of());
        }
    }

    public record Choice<T>(RecipeGraph.Option<T> option, List<T> inputs, int index, int alternatives) {
        public Choice {
            inputs = List.copyOf(inputs);
        }
    }

    public record Step<T>(T item, long demand, long fromStock, long crafts, long produced, long leftover, Choice<T> choice,
            boolean target) {
    }

    public record Raw<T>(T item, long need, long fromStock, long toGet, boolean forced, boolean producible,
            boolean target) {
    }

    public record Plan<T>(List<Raw<T>> raw, List<Step<T>> steps, Map<T, Set<T>> consumers) {
        public Plan {
            raw = List.copyOf(raw);
            steps = List.copyOf(steps);
            consumers = Map.copyOf(consumers);
        }

        public static <T> Plan<T> empty() {
            return new Plan<>(List.of(), List.of(), Map.of());
        }

        public boolean isEmpty() {
            return this.raw.isEmpty() && this.steps.isEmpty();
        }

        public Raw<T> rawOf(T item) {
            for (Raw<T> entry : this.raw) {
                if (entry.item().equals(item)) {
                    return entry;
                }
            }
            return null;
        }

        public Step<T> stepOf(T item) {
            for (Step<T> step : this.steps) {
                if (step.item().equals(item)) {
                    return step;
                }
            }
            return null;
        }

        public long toGet() {
            long sum = 0L;
            for (Raw<T> entry : this.raw) {
                sum = addSaturated(sum, entry.toGet());
            }
            return sum;
        }

        public Set<T> users(T item) {
            Set<T> seen = new LinkedHashSet<>();
            Deque<T> queue = new ArrayDeque<>();
            queue.add(item);
            while (!queue.isEmpty()) {
                Set<T> next = this.consumers.get(queue.poll());
                if (next == null) {
                    continue;
                }
                for (T consumer : next) {
                    if (seen.add(consumer)) {
                        queue.add(consumer);
                    }
                }
            }
            return seen;
        }
    }

    public static <T> Plan<T> plan(RecipeGraph<T> graph, Map<T, Long> targets, Map<T, Long> stock, Settings<T> settings) {
        Map<T, Long> wanted = new LinkedHashMap<>();
        for (Map.Entry<T, Long> entry : targets.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null && entry.getValue() > 0L) {
                wanted.merge(entry.getKey(), entry.getValue(), RawPlanner::addSaturated);
            }
        }
        if (wanted.isEmpty()) {
            return Plan.empty();
        }
        Context<T> context = new Context<>(graph, settings == null ? Settings.defaults() : settings);
        context.rank(wanted.keySet());
        Map<T, Long> available = stock == null ? Map.of() : stock;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            Context.Built<T> built = context.build(wanted, available);
            if (built.cycle() == null) {
                return built.plan();
            }
            context.breakAt(built.cycle());
        }
        return context.build(wanted, available).plan();
    }

    static long addSaturated(long a, long b) {
        long sum = a + b;
        return ((a ^ sum) & (b ^ sum)) < 0L ? Long.MAX_VALUE : sum;
    }

    static long multiplySaturated(long a, long b) {
        long high = Math.multiplyHigh(a, b);
        long low = a * b;
        return high != 0L || low < 0L ? Long.MAX_VALUE : low;
    }

    private static final class Context<T> {
        record Allowed<T>(RecipeGraph.Option<T> option, List<List<T>> choices) {
        }

        record Built<T>(Plan<T> plan, T cycle) {
        }

        private final RecipeGraph<T> graph;
        private final Settings<T> settings;
        private final Map<T, Integer> rank = new HashMap<>();
        private final Map<T, List<Allowed<T>>> allowed = new HashMap<>();
        private final Map<T, Double> cost = new HashMap<>();
        private final Map<T, Choice<T>> chosen = new HashMap<>();
        private final Set<T> decided = new HashSet<>();
        private final Set<T> visiting = new HashSet<>();
        private final Set<T> broken = new HashSet<>();

        Context(RecipeGraph<T> graph, Settings<T> settings) {
            this.graph = graph;
            this.settings = settings;
        }

        void rank(Collection<T> roots) {
            List<T> reachable = new ArrayList<>();
            Set<T> seen = new HashSet<>();
            Deque<T> queue = new ArrayDeque<>();
            for (T root : roots) {
                if (seen.add(root)) {
                    queue.add(root);
                }
            }
            while (!queue.isEmpty() && reachable.size() < MAX_ITEMS) {
                T item = queue.poll();
                reachable.add(item);
                for (RecipeGraph.Option<T> option : this.graph.options(item)) {
                    for (RecipeGraph.Input<T> input : option.inputs()) {
                        for (T choice : input.choices()) {
                            if (seen.add(choice)) {
                                queue.add(choice);
                            }
                        }
                    }
                }
            }
            for (T item : reachable) {
                this.rank.put(item, this.graph.options(item).isEmpty() ? 0 : UNRANKED);
            }
            boolean changed = true;
            for (int round = 0; changed && round <= reachable.size(); round++) {
                changed = false;
                for (T item : reachable) {
                    int current = this.rank.get(item);
                    if (current == 0) {
                        continue;
                    }
                    int best = UNRANKED;
                    for (RecipeGraph.Option<T> option : this.graph.options(item)) {
                        int deepest = 0;
                        boolean usable = true;
                        for (RecipeGraph.Input<T> input : option.inputs()) {
                            int lowest = UNRANKED;
                            for (T choice : input.choices()) {
                                if (!choice.equals(item)) {
                                    lowest = Math.min(lowest, this.rank.getOrDefault(choice, 0));
                                }
                            }
                            if (lowest == UNRANKED) {
                                usable = false;
                                break;
                            }
                            deepest = Math.max(deepest, lowest);
                        }
                        if (usable) {
                            best = Math.min(best, deepest + 1);
                        }
                    }
                    if (best < current) {
                        this.rank.put(item, best);
                        changed = true;
                    }
                }
            }
            this.rank.replaceAll((item, value) -> value == UNRANKED ? 0 : value);
        }

        void breakAt(T item) {
            this.broken.add(item);
            this.allowed.clear();
            this.cost.clear();
            this.chosen.clear();
            this.decided.clear();
        }

        private int rankOf(T item) {
            return this.rank.getOrDefault(item, 0);
        }

        private boolean forced(T item) {
            return this.settings.forcedRaw().contains(item) || this.broken.contains(item);
        }

        List<Allowed<T>> allowed(T item) {
            List<Allowed<T>> cached = this.allowed.get(item);
            if (cached != null) {
                return cached;
            }
            List<Allowed<T>> result = new ArrayList<>();
            if (!forced(item)) {
                int own = rankOf(item);
                for (RecipeGraph.Option<T> option : this.graph.options(item)) {
                    if (option.kind() == RecipeGraph.Kind.SMELTING && !this.settings.smelting()) {
                        continue;
                    }
                    boolean compression = option.inputCount() > option.count();
                    List<List<T>> choices = new ArrayList<>();
                    for (RecipeGraph.Input<T> input : option.inputs()) {
                        List<T> kept = new ArrayList<>();
                        for (T choice : input.choices()) {
                            int other = rankOf(choice);
                            if (!choice.equals(item) && (other < own || other == own && compression)) {
                                kept.add(choice);
                            }
                        }
                        if (kept.isEmpty()) {
                            choices = null;
                            break;
                        }
                        choices.add(List.copyOf(kept));
                    }
                    if (choices != null) {
                        result.add(new Allowed<>(option, List.copyOf(choices)));
                    }
                }
            }
            List<Allowed<T>> immutable = List.copyOf(result);
            this.allowed.put(item, immutable);
            return immutable;
        }

        double cost(T item) {
            Double known = this.cost.get(item);
            if (known != null) {
                return known;
            }
            if (forced(item)) {
                return 1.0;
            }
            if (!this.visiting.add(item)) {
                return Double.POSITIVE_INFINITY;
            }
            double best = Double.POSITIVE_INFINITY;
            for (Allowed<T> entry : allowed(item)) {
                best = Math.min(best, unitCost(entry));
            }
            this.visiting.remove(item);
            double result = Double.isInfinite(best) ? 1.0 : best;
            this.cost.put(item, result);
            return result;
        }

        private double unitCost(Allowed<T> entry) {
            double sum = 0.0;
            List<RecipeGraph.Input<T>> inputs = entry.option().inputs();
            for (int i = 0; i < inputs.size(); i++) {
                double cheapest = Double.POSITIVE_INFINITY;
                for (T choice : entry.choices().get(i)) {
                    cheapest = Math.min(cheapest, cost(choice));
                }
                sum += inputs.get(i).count() * cheapest;
                if (Double.isInfinite(sum)) {
                    return sum;
                }
            }
            return sum / entry.option().count();
        }

        Choice<T> choose(T item) {
            if (this.decided.contains(item)) {
                return this.chosen.get(item);
            }
            Choice<T> choice = decide(item);
            this.decided.add(item);
            if (choice != null) {
                this.chosen.put(item, choice);
            }
            return choice;
        }

        private Choice<T> decide(T item) {
            List<Allowed<T>> options = allowed(item);
            if (options.isEmpty()) {
                return null;
            }
            double[] costs = new double[options.size()];
            int best = -1;
            for (int i = 0; i < options.size(); i++) {
                costs[i] = unitCost(options.get(i));
                if (Double.isInfinite(costs[i])) {
                    continue;
                }
                if (best < 0 || better(options.get(i), costs[i], options.get(best), costs[best])) {
                    best = i;
                }
            }
            if (best < 0) {
                return null;
            }
            Integer requested = this.settings.choices().get(item);
            if (requested != null && requested >= 0 && requested < options.size()
                    && !Double.isInfinite(costs[requested])) {
                best = requested;
            }
            Allowed<T> entry = options.get(best);
            List<T> inputs = new ArrayList<>();
            for (List<T> choices : entry.choices()) {
                T pick = choices.get(0);
                double pickCost = cost(pick);
                for (T choice : choices) {
                    double value = cost(choice);
                    if (value < pickCost) {
                        pick = choice;
                        pickCost = value;
                    }
                }
                inputs.add(pick);
            }
            return new Choice<>(entry.option(), inputs, best, options.size());
        }

        private static <T> boolean better(Allowed<T> candidate, double candidateCost, Allowed<T> current, double currentCost) {
            if (candidateCost < currentCost - 1.0E-9) {
                return true;
            }
            if (candidateCost > currentCost + 1.0E-9) {
                return false;
            }
            return candidate.option().kind().ordinal() < current.option().kind().ordinal();
        }

        Built<T> build(Map<T, Long> wanted, Map<T, Long> stock) {
            Map<T, Integer> state = new HashMap<>();
            List<T> post = new ArrayList<>();
            for (T root : wanted.keySet()) {
                if (!state.containsKey(root)) {
                    T cycle = visit(root, state, post);
                    if (cycle != null) {
                        return new Built<>(Plan.empty(), cycle);
                    }
                }
            }
            Map<T, Long> demand = new HashMap<>(wanted);
            Map<T, Set<T>> consumers = new HashMap<>();
            List<Raw<T>> raw = new ArrayList<>();
            List<Step<T>> steps = new ArrayList<>();
            for (int index = post.size() - 1; index >= 0; index--) {
                T item = post.get(index);
                long need = demand.getOrDefault(item, 0L);
                if (need <= 0L) {
                    continue;
                }
                long have = this.settings.useStock() ? Math.max(0L, stock.getOrDefault(item, 0L)) : 0L;
                long fromStock = Math.min(have, need);
                long rest = need - fromStock;
                Choice<T> choice = choose(item);
                boolean target = wanted.containsKey(item);
                if (choice == null) {
                    raw.add(new Raw<>(item, need, fromStock, rest, this.settings.forcedRaw().contains(item),
                            this.graph.producible(item), target));
                    continue;
                }
                int output = choice.option().count();
                long crafts = rest / output + (rest % output == 0L ? 0L : 1L);
                long produced = multiplySaturated(crafts, output);
                steps.add(new Step<>(item, need, fromStock, crafts, produced, Math.max(0L, produced - rest), choice, target));
                List<RecipeGraph.Input<T>> inputs = choice.option().inputs();
                for (int i = 0; i < inputs.size(); i++) {
                    T input = choice.inputs().get(i);
                    demand.merge(input, multiplySaturated(crafts, inputs.get(i).count()), RawPlanner::addSaturated);
                    consumers.computeIfAbsent(input, ignored -> new LinkedHashSet<>()).add(item);
                }
            }
            raw.sort(Comparator.comparingLong((Raw<T> entry) -> entry.toGet()).reversed());
            return new Built<>(new Plan<>(raw, steps, consumers), null);
        }

        private T visit(T item, Map<T, Integer> state, List<T> post) {
            Deque<Object[]> stack = new ArrayDeque<>();
            state.put(item, 1);
            stack.push(new Object[] {item, inputsOf(item), 0});
            while (!stack.isEmpty()) {
                Object[] frame = stack.peek();
                @SuppressWarnings("unchecked")
                List<T> inputs = (List<T>) frame[1];
                int next = (Integer) frame[2];
                if (next >= inputs.size()) {
                    stack.pop();
                    @SuppressWarnings("unchecked")
                    T done = (T) frame[0];
                    state.put(done, 2);
                    post.add(done);
                    continue;
                }
                frame[2] = next + 1;
                T child = inputs.get(next);
                Integer childState = state.get(child);
                if (childState == null) {
                    state.put(child, 1);
                    stack.push(new Object[] {child, inputsOf(child), 0});
                } else if (childState == 1) {
                    return child;
                }
            }
            return null;
        }

        private List<T> inputsOf(T item) {
            Choice<T> choice = choose(item);
            return choice == null ? List.of() : choice.inputs();
        }
    }
}
