package org.lts.litematicaplus.compat;

import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.network.chat.Component;
import org.lts.litematicaplus.LitematicaPlusConfig;
import org.lts.litematicaplus.render.ExternalMap;
import org.lts.litematicaplus.render.LodDepth;
import org.lts.litematicaplus.render.SectionReady;
import org.lts.litematicaplus.render.VoxyLod;
import org.lts.litematicaplus.share.SyncSession;
import org.lts.litematicaplus.share.SyncmaticaClient;
import org.lts.litematicaplus.world.WorldCache;

public enum Addon {
    SYNCMATICA("", List.of(new Setting("syncmatica", config -> config.syncmaticaEnabled,
            (config, value) -> config.syncmaticaEnabled = value))),
    VOXYMAP("voxymap", List.of(new Setting("world_cache", config -> config.useWorldCache,
            (config, value) -> config.useWorldCache = value))),
    VOXY("voxy", List.of(new Setting("ghosts_loaded_only", config -> config.ghostsOnlyLoaded,
            (config, value) -> config.ghostsOnlyLoaded = value), new Setting("world_cache",
            config -> config.useWorldCache, (config, value) -> config.useWorldCache = value))),
    IRIS("iris", List.of(new Setting("ghosts_loaded_only", config -> config.ghostsOnlyLoaded,
            (config, value) -> config.ghostsOnlyLoaded = value))),
    SODIUM("sodium", List.of()),
    JADE("jade", List.of()),
    STACK_CALCULATOR("stackcalculator", List.of(new Setting("material_panel", config -> config.materialPanel,
            (config, value) -> config.materialPanel = value))),
    INVENTORY_PROFILES("inventoryprofilesnext", List.of(new Setting("material_helper", config -> config.materialHelper,
            (config, value) -> config.materialHelper = value))),
    MOD_MENU("modmenu", List.of()),
    AXIOM("axiom", List.of(new Setting("axiom_editor_pause", config -> config.axiomEditorPause,
            (config, value) -> config.axiomEditorPause = value))),
    LITEMATICA("litematica", List.of());

    public enum State {
        CONFLICT,
        FAILED,
        ACTIVE,
        STANDBY,
        OFF,
        MISSING
    }

    public record Setting(String option, Predicate<LitematicaPlusConfig> getter,
            BiConsumer<LitematicaPlusConfig, Boolean> setter) {
        public boolean value() {
            return this.getter.test(LitematicaPlusConfig.current());
        }

        public void set(boolean value) {
            LitematicaPlusConfig copy = LitematicaPlusConfig.currentCopy();
            this.setter.accept(copy, value);
            LitematicaPlusConfig.save(copy);
        }
    }

    private final String modId;
    private final List<Setting> settings;

    Addon(String modId, List<Setting> settings) {
        this.modId = modId;
        this.settings = settings;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String modId() {
        return this.modId;
    }

    public List<Setting> settings() {
        return this.settings;
    }

    public boolean builtIn() {
        return this.modId.isEmpty();
    }

    public boolean installed() {
        return builtIn() || FabricLoader.getInstance().isModLoaded(this.modId);
    }

    public boolean listed() {
        return this != LITEMATICA || installed();
    }

    public String version() {
        if (builtIn()) {
            return "";
        }
        return FabricLoader.getInstance().getModContainer(this.modId).map(ModContainer::getMetadata)
                .map(metadata -> metadata.getVersion().getFriendlyString()).orElse("");
    }

    public Component title() {
        return Component.translatable("litematicaplus.addon." + key() + ".name");
    }

    public Component about() {
        return Component.translatable("litematicaplus.addon." + key() + ".about");
    }

    public State state() {
        if (this == SYNCMATICA) {
            SyncmaticaClient client = SyncmaticaClient.get();
            if (client.conflict()) {
                return State.CONFLICT;
            }
            if (!LitematicaPlusConfig.current().syncmaticaEnabled) {
                return State.OFF;
            }
            if (client.available()) {
                return State.ACTIVE;
            }
            return SyncSession.INCOMPATIBLE_VERSION.equals(client.serverVersion()) ? State.FAILED : State.STANDBY;
        }
        if (!installed()) {
            return State.MISSING;
        }
        return switch (this) {
            case VOXYMAP -> ExternalMap.available() ? State.ACTIVE : State.FAILED;
            case VOXY -> VoxyLod.failed() || LodDepth.failed() ? State.FAILED : State.ACTIVE;
            case SODIUM -> SectionReady.available() ? State.ACTIVE : State.FAILED;
            case JADE -> Addons.jadeRegistered() ? State.ACTIVE : State.FAILED;
            case STACK_CALCULATOR -> StackCalculatorBridge.available() ? State.ACTIVE : State.FAILED;
            case INVENTORY_PROFILES -> InventoryLocks.available() ? State.ACTIVE : State.FAILED;
            case AXIOM -> AxiomBridge.available() ? State.ACTIVE : State.FAILED;
            case LITEMATICA -> State.CONFLICT;
            default -> State.ACTIVE;
        };
    }

    public Component status() {
        State state = state();
        String base = "litematicaplus.addon." + key() + ".status.";
        if (state == State.MISSING) {
            return Component.translatable("litematicaplus.addon.status.missing");
        }
        return switch (this) {
            case SYNCMATICA -> {
                SyncmaticaClient client = SyncmaticaClient.get();
                yield switch (state) {
                    case CONFLICT -> Component.translatable(base + "conflict");
                    case OFF -> Component.translatable(base + "off");
                    case ACTIVE -> Component.translatable(base + "active", client.serverVersion(),
                            client.placements().size());
                    case FAILED -> Component.translatable(base + "rejected", client.serverVersion());
                    default -> client.phase() == SyncSession.Phase.FEATURES || client.phase() == SyncSession.Phase.VERSION
                            ? Component.translatable(base + "connecting") : Component.translatable(base + "no_server");
                };
            }
            case VOXY -> state == State.FAILED ? Component.translatable(base + "failed")
                    : Component.translatable(FabricLoader.getInstance().isModLoaded("iris") ? base + "active_iris"
                    : base + "active", Component.translatable(WorldCache.available()
                    ? "litematicaplus.addon.voxy.cache_on" : "litematicaplus.addon.voxy.cache_off"));
            case LITEMATICA -> Component.translatable(base + "conflict", version());
            case AXIOM -> state == State.FAILED ? Component.translatable(base + "failed", version())
                    : Component.translatable(AxiomBridge.active() ? base + "active" : base + "standby", version());
            case IRIS, MOD_MENU -> Component.translatable(base + "active");
            default -> Component.translatable(base + (state == State.ACTIVE ? "active" : "failed"));
        };
    }
}
