package org.lts.litematicaplus.recipes;

import it.unimi.dsi.fastutil.objects.Reference2LongMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lts.litematicaplus.LitematicaPlusConfig;
import org.lts.litematicaplus.materials.EnderChestMemory;
import org.lts.litematicaplus.materials.MaterialList;
import org.lts.litematicaplus.materials.MaterialReport;

public final class RawMaterials {
    public enum Scope {
        MISSING,
        ALL
    }

    public record Targets(Map<Item, Long> wanted, Map<Item, Long> stock) {
    }

    private RawMaterials() {
    }

    public static RawPlanner.Settings<Item> settings() {
        LitematicaPlusConfig config = LitematicaPlusConfig.current();
        Set<Item> forced = new LinkedHashSet<>();
        for (String id : config.rawForced) {
            Item item = item(id);
            if (item != null) {
                forced.add(item);
            }
        }
        Map<Item, Integer> choices = new HashMap<>();
        config.rawChoices.forEach((id, index) -> {
            Item item = item(id);
            if (item != null && index != null) {
                choices.put(item, index);
            }
        });
        return new RawPlanner.Settings<>(config.rawSmelting, config.rawUseStock, forced, choices);
    }

    public static Item item(String id) {
        Identifier key = id == null ? null : Identifier.tryParse(id);
        if (key == null) {
            return null;
        }
        return BuiltInRegistries.ITEM.getOptional(key).filter(item -> item != Items.AIR).orElse(null);
    }

    public static String id(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    public static Map<Item, Long> inventory() {
        Minecraft minecraft = Minecraft.getInstance();
        Map<Item, Long> counts = new HashMap<>();
        Inventory inventory = minecraft.player == null ? null : minecraft.player.getInventory();
        if (inventory == null) {
            return counts;
        }
        for (Reference2LongMap.Entry<Item> entry : MaterialList.countInventory(inventory).reference2LongEntrySet()) {
            counts.merge(entry.getKey(), entry.getLongValue(), RawPlanner::addSaturated);
        }
        if (EnderChestMemory.counted()) {
            for (Reference2LongMap.Entry<Item> entry : EnderChestMemory.counts().reference2LongEntrySet()) {
                counts.merge(entry.getKey(), entry.getLongValue(), RawPlanner::addSaturated);
            }
        }
        return counts;
    }

    public static Targets targets(List<MaterialList.Entry> entries, Scope scope) {
        Map<Item, Long> wanted = new LinkedHashMap<>();
        Map<Item, Long> stock = inventory();
        for (MaterialList.Entry entry : entries) {
            if (scope == Scope.MISSING) {
                long need = entry.shortfall();
                if (need > 0L) {
                    wanted.merge(entry.item(), need, RawPlanner::addSaturated);
                }
                long have = stock.getOrDefault(entry.item(), 0L);
                stock.put(entry.item(), Math.max(0L, have - entry.missing()));
            } else if (entry.total() > 0L) {
                wanted.merge(entry.item(), entry.total(), RawPlanner::addSaturated);
            }
        }
        return new Targets(wanted, stock);
    }

    public static RawPlanner.Plan<Item> plan(Targets targets) {
        return RawPlanner.plan(RecipeIndex.graph(), targets.wanted(), targets.stock(), settings());
    }

    public static Component station(RecipeGraph.Kind kind) {
        return Component.translatable("litematicaplus.raw.station." + kind.name().toLowerCase(Locale.ROOT));
    }

    public static String name(Item item) {
        return new ItemStack(item).getHoverName().getString();
    }

    public static String recipeText(RawPlanner.Choice<Item> choice) {
        StringBuilder text = new StringBuilder();
        List<RecipeGraph.Input<Item>> inputs = choice.option().inputs();
        for (int i = 0; i < inputs.size(); i++) {
            if (i > 0) {
                text.append(" + ");
            }
            text.append(inputs.get(i).count()).append(' ').append(name(choice.inputs().get(i)));
        }
        text.append(" -> ").append(choice.option().count()).append(" (").append(station(choice.option().kind()).getString())
                .append(')');
        return text.toString();
    }

    public static MaterialReport.RawSection section(RawPlanner.Plan<Item> plan) {
        List<MaterialReport.RawRow> raw = new ArrayList<>();
        for (RawPlanner.Raw<Item> entry : plan.raw()) {
            ItemStack stack = new ItemStack(entry.item());
            raw.add(new MaterialReport.RawRow(stack.getHoverName().getString(), id(entry.item()), entry.need(),
                    entry.fromStock(), entry.toGet(), stack.getMaxStackSize()));
        }
        List<MaterialReport.StepRow> steps = new ArrayList<>();
        for (RawPlanner.Step<Item> step : plan.steps()) {
            steps.add(new MaterialReport.StepRow(name(step.item()), id(step.item()), step.demand(), step.fromStock(),
                    step.crafts(), step.produced(), step.leftover(), recipeText(step.choice())));
        }
        return new MaterialReport.RawSection(raw, steps);
    }

    public static MaterialReport.RawSection sectionFor(List<MaterialList.Entry> entries, Scope scope) {
        if (RecipeIndex.graph().size() == 0) {
            return null;
        }
        RawPlanner.Plan<Item> plan = plan(targets(entries, scope));
        return plan.isEmpty() ? null : section(plan);
    }
}
