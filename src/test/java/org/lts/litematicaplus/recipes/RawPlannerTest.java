package org.lts.litematicaplus.recipes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RawPlannerTest {
    private static RecipeGraph.Option<String> craft(String output, int count, Object... inputs) {
        return option(RecipeGraph.Kind.CRAFTING, output, count, inputs);
    }

    private static RecipeGraph.Option<String> option(RecipeGraph.Kind kind, String output, int count, Object... inputs) {
        List<RecipeGraph.Input<String>> list = new java.util.ArrayList<>();
        for (int i = 0; i < inputs.length; i += 2) {
            Object choices = inputs[i];
            List<String> items = choices instanceof String single ? List.of(single) : castList(choices);
            list.add(new RecipeGraph.Input<>(items, (Integer) inputs[i + 1]));
        }
        return new RecipeGraph.Option<>(output, count, kind, list);
    }

    @SuppressWarnings("unchecked")
    private static List<String> castList(Object value) {
        return (List<String>) value;
    }

    private static RecipeGraph<String> wood() {
        return RecipeGraph.<String>builder()
                .add(craft("planks", 4, List.of("log", "wood", "stripped_log"), 1))
                .add(craft("wood", 3, "log", 4))
                .add(craft("stripped_log", 1, "log", 1))
                .add(craft("slab", 6, "planks", 3))
                .add(craft("stairs", 4, "planks", 6))
                .add(craft("stick", 4, "planks", 2))
                .add(craft("fence", 3, "planks", 4, "stick", 2))
                .build();
    }

    private static Map<String, Long> map(Object... pairs) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            result.put((String) pairs[i], ((Number) pairs[i + 1]).longValue());
        }
        return result;
    }

    @Test
    void slabsBecomePlanksAndPlanksBecomeLogs() {
        RawPlanner.Plan<String> plan = RawPlanner.plan(wood(), map("slab", 100), Map.of(), RawPlanner.Settings.defaults());
        RawPlanner.Step<String> slabs = plan.stepOf("slab");
        assertEquals(17L, slabs.crafts());
        assertEquals(2L, slabs.leftover());
        RawPlanner.Step<String> planks = plan.stepOf("planks");
        assertEquals(51L, planks.demand());
        assertEquals(13L, planks.crafts());
        assertEquals("log", planks.choice().inputs().get(0));
        assertEquals(13L, plan.rawOf("log").toGet());
        assertEquals(1, plan.raw().size());
        assertEquals(Set.of("planks", "slab"), plan.users("log"));
    }

    @Test
    void demandIsSummedBeforeRounding() {
        RawPlanner.Plan<String> plan = RawPlanner.plan(wood(), map("slab", 3, "stairs", 1, "fence", 3),
                Map.of(), RawPlanner.Settings.defaults());
        assertEquals(3L + 6L + 4L + 2L, plan.stepOf("planks").demand());
        assertEquals(4L, plan.stepOf("planks").crafts());
        assertEquals(4L, plan.rawOf("log").toGet());
        assertTrue(plan.steps().indexOf(plan.stepOf("stick")) < plan.steps().indexOf(plan.stepOf("planks")));
    }

    @Test
    void stockIsUsedAtEveryStep() {
        RawPlanner.Plan<String> plan = RawPlanner.plan(wood(), map("slab", 100), map("planks", 11, "log", 3),
                RawPlanner.Settings.defaults());
        RawPlanner.Step<String> planks = plan.stepOf("planks");
        assertEquals(11L, planks.fromStock());
        assertEquals(10L, planks.crafts());
        RawPlanner.Raw<String> log = plan.rawOf("log");
        assertEquals(10L, log.need());
        assertEquals(3L, log.fromStock());
        assertEquals(7L, log.toGet());
        RawPlanner.Plan<String> ignored = RawPlanner.plan(wood(), map("slab", 100), map("planks", 11, "log", 3),
                new RawPlanner.Settings<>(false, false, Set.of(), Map.of()));
        assertEquals(13L, ignored.rawOf("log").toGet());
    }

    @Test
    void stonecutterWinsWhenItIsCheaper() {
        RecipeGraph<String> graph = RecipeGraph.<String>builder()
                .add(craft("stone_stairs", 4, "stone", 6))
                .add(option(RecipeGraph.Kind.STONECUTTER, "stone_stairs", 1, "stone", 1))
                .add(craft("stone_slab", 6, "stone", 3))
                .add(option(RecipeGraph.Kind.STONECUTTER, "stone_slab", 2, "stone", 1))
                .build();
        RawPlanner.Plan<String> plan = RawPlanner.plan(graph, map("stone_stairs", 8, "stone_slab", 12), Map.of(),
                RawPlanner.Settings.defaults());
        assertEquals(RecipeGraph.Kind.STONECUTTER, plan.stepOf("stone_stairs").choice().option().kind());
        assertEquals(RecipeGraph.Kind.CRAFTING, plan.stepOf("stone_slab").choice().option().kind());
        assertEquals(14L, plan.rawOf("stone").toGet());
        RawPlanner.Plan<String> chosen = RawPlanner.plan(graph, map("stone_stairs", 8), Map.of(),
                new RawPlanner.Settings<>(false, true, Set.of(), Map.of("stone_stairs", 0)));
        RawPlanner.Step<String> stairs = chosen.stepOf("stone_stairs");
        assertEquals(RecipeGraph.Kind.CRAFTING, stairs.choice().option().kind());
        assertEquals(2, stairs.choice().alternatives());
        assertEquals(12L, chosen.rawOf("stone").toGet());
    }

    @Test
    void reversibleRecipesDoNotLoop() {
        RecipeGraph<String> graph = RecipeGraph.<String>builder()
                .add(craft("iron_block", 1, "iron_ingot", 9))
                .add(craft("iron_ingot", 9, "iron_block", 1))
                .add(craft("iron_ingot", 1, "iron_nugget", 9))
                .add(craft("iron_nugget", 9, "iron_ingot", 1))
                .add(option(RecipeGraph.Kind.SMELTING, "iron_ingot", 1, "raw_iron", 1))
                .add(craft("slime_block", 1, "slime_ball", 9))
                .add(craft("slime_ball", 9, "slime_block", 1))
                .build();
        RawPlanner.Plan<String> plan = RawPlanner.plan(graph, map("iron_block", 2, "iron_nugget", 5, "slime_block", 1),
                Map.of(), RawPlanner.Settings.defaults());
        assertEquals(19L, plan.rawOf("iron_ingot").toGet());
        assertTrue(plan.rawOf("iron_ingot").producible());
        assertEquals(9L, plan.rawOf("slime_ball").toGet());
        assertNull(plan.rawOf("iron_block"));
        RawPlanner.Plan<String> smelted = RawPlanner.plan(graph, map("iron_block", 1), Map.of(),
                new RawPlanner.Settings<>(true, true, Set.of(), Map.of()));
        assertEquals(9L, smelted.rawOf("raw_iron").toGet());
        RawPlanner.Plan<String> ingots = RawPlanner.plan(graph, map("iron_ingot", 4), Map.of(), RawPlanner.Settings.defaults());
        assertEquals(4L, ingots.rawOf("iron_ingot").toGet());
        assertTrue(ingots.steps().isEmpty());
    }

    @Test
    void anOddShortRecipeDoesNotHideTheUsualOne() {
        RecipeGraph<String> graph = RecipeGraph.<String>builder()
                .add(craft("planks", 4, "log", 1))
                .add(craft("stick", 4, "planks", 2))
                .add(craft("stick", 1, "bamboo", 2))
                .add(craft("torch", 4, "coal", 1, "stick", 1))
                .build();
        RawPlanner.Plan<String> plan = RawPlanner.plan(graph, map("torch", 16), Map.of(), RawPlanner.Settings.defaults());
        assertEquals("planks", plan.stepOf("stick").choice().inputs().get(0));
        assertNull(plan.rawOf("bamboo"));
        assertEquals(1L, plan.rawOf("log").toGet());
        assertEquals(4L, plan.rawOf("coal").toGet());
    }

    @Test
    void dyedVariantsComeFromTheUndyedOne() {
        RecipeGraph<String> graph = RecipeGraph.<String>builder()
                .add(craft("white_wool", 1, "string", 4))
                .add(craft("red_wool", 1, "red_dye", 1, List.of("white_wool", "blue_wool"), 1))
                .add(craft("blue_wool", 1, "blue_dye", 1, List.of("white_wool", "red_wool"), 1))
                .build();
        RawPlanner.Plan<String> plan = RawPlanner.plan(graph, map("red_wool", 3, "blue_wool", 2), Map.of(),
                RawPlanner.Settings.defaults());
        assertEquals("white_wool", plan.stepOf("red_wool").choice().inputs().get(1));
        assertEquals("white_wool", plan.stepOf("blue_wool").choice().inputs().get(1));
        assertEquals(20L, plan.rawOf("string").toGet());
    }

    @Test
    void forcedRawStopsExpansion() {
        RawPlanner.Plan<String> plan = RawPlanner.plan(wood(), map("slab", 12), Map.of(),
                new RawPlanner.Settings<>(false, true, Set.of("planks"), Map.of()));
        RawPlanner.Raw<String> planks = plan.rawOf("planks");
        assertNotNull(planks);
        assertTrue(planks.forced());
        assertEquals(6L, planks.toGet());
        assertNull(plan.rawOf("log"));
    }

    @Test
    void itemsWithoutRecipesAreRawTargets() {
        RawPlanner.Plan<String> plan = RawPlanner.plan(wood(), map("dirt", 64, "slab", 0), Map.of(),
                RawPlanner.Settings.defaults());
        assertEquals(1, plan.raw().size());
        assertTrue(plan.rawOf("dirt").target());
        assertTrue(plan.steps().isEmpty());
        assertTrue(RawPlanner.plan(wood(), Map.of(), Map.of(), RawPlanner.Settings.defaults()).isEmpty());
    }

    @Test
    void hugeNumbersSaturate() {
        RawPlanner.Plan<String> plan = RawPlanner.plan(wood(), map("fence", Long.MAX_VALUE / 2), Map.of(),
                RawPlanner.Settings.defaults());
        assertTrue(plan.rawOf("log").toGet() > 0L);
        assertEquals(Long.MAX_VALUE, RawPlanner.multiplySaturated(Long.MAX_VALUE, 3L));
        assertEquals(Long.MAX_VALUE, RawPlanner.addSaturated(Long.MAX_VALUE, 1L));
    }
}
