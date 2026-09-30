package org.lts.litematicaplus.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import org.lts.litematicaplus.gui.HubScreen;

public final class LitematicaPlusModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return HubScreen::settings;
    }
}
