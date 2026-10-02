package org.lts.litematicaplus.materials;

import it.unimi.dsi.fastutil.objects.Reference2LongMap;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import org.lts.litematicaplus.LitematicaPlusConfig;

public final class MaterialTooltips {
    private static final int MAX_BOX_LINES = 3;

    private MaterialTooltips() {
    }

    public static void register() {
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
            if (!LitematicaPlusConfig.current().materialTooltips || Minecraft.getInstance().player == null
                    || stack.isEmpty()) {
                return;
            }
            MaterialCache.Snapshot snapshot = MaterialCache.get();
            if (snapshot.placement() == null || snapshot.entries().isEmpty()) {
                return;
            }
            lines.addAll(describe(snapshot, stack));
        });
    }

    static List<Component> describe(MaterialCache.Snapshot snapshot, ItemStack stack) {
        List<Component> lines = new ArrayList<>();
        String name = snapshot.placement().name();
        MaterialList.Entry entry = snapshot.entry(stack.getItem());
        if (entry != null && entry.missing() > 0L) {
            ItemStack icon = new ItemStack(entry.item());
            lines.add(entry.shortfall() > 0L
                    ? Component.translatable("litematicaplus.tooltip.material", name,
                            MaterialPanel.amount(entry.shortfall(), icon)).withStyle(ChatFormatting.GOLD)
                    : Component.translatable("litematicaplus.tooltip.material_done", name,
                            MaterialPanel.amount(entry.missing(), icon)).withStyle(ChatFormatting.DARK_GREEN));
        }
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents == null) {
            return lines;
        }
        Reference2LongOpenHashMap<Item> useful = new Reference2LongOpenHashMap<>();
        contents.nonEmptyItemCopyStream().forEach(inner -> {
            MaterialList.Entry needed = snapshot.entry(inner.getItem());
            if (needed != null && needed.shortfall() > 0L) {
                useful.addTo(inner.getItem(), (long) inner.getCount() * stack.getCount());
            }
        });
        if (useful.isEmpty()) {
            return lines;
        }
        List<Reference2LongMap.Entry<Item>> sorted = new ArrayList<>(useful.reference2LongEntrySet());
        sorted.sort((a, b) -> Long.compare(b.getLongValue(), a.getLongValue()));
        lines.add(Component.translatable("litematicaplus.tooltip.box", name).withStyle(ChatFormatting.GOLD));
        for (int i = 0; i < Math.min(MAX_BOX_LINES, sorted.size()); i++) {
            Reference2LongMap.Entry<Item> item = sorted.get(i);
            lines.add(Component.translatable("litematicaplus.tooltip.box_line",
                    new ItemStack(item.getKey()).getHoverName(), item.getLongValue()).withStyle(ChatFormatting.GRAY));
        }
        if (sorted.size() > MAX_BOX_LINES) {
            lines.add(Component.translatable("litematicaplus.tooltip.box_more", sorted.size() - MAX_BOX_LINES)
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        return lines;
    }
}
