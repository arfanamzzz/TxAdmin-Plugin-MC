package dev.txadmin;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import io.papermc.paper.registry.event.RegistryEvents;
import io.papermc.paper.registry.keys.DialogKeys;
import io.papermc.paper.registry.keys.tags.DialogTagKeys;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;

import java.util.List;
import java.util.Set;

/**
 * Mendaftarkan dialog "Quick Actions" TxAdmin. Tombol bawaan Minecraft "Quick Actions" (default G)
 * membuka dialog ini. Tiap pemain bisa mengganti tombolnya ke tombol APA SAJA di
 * Options > Controls > Key Binds (Home, 0, ', F1, huruf, dll).
 */
public final class TxBootstrap implements PluginBootstrap {
    public static final Key QUICK = Key.key("txadmin:quick");

    private static ActionButton button(String label, String id) {
        return ActionButton.create(Component.text(label), null, 200, DialogAction.customClick(Key.key("txadmin:" + id), null));
    }

    @Override
    public void bootstrap(BootstrapContext context) {
        context.getLifecycleManager().registerEventHandler(RegistryEvents.DIALOG.compose(),
                event -> event.registry().register(DialogKeys.create(QUICK), builder -> builder
                        .base(DialogBase.builder(Component.text("txAdmin"))
                                .canCloseWithEscape(true)
                                .body(List.of(DialogBody.plainMessage(Component.text("Quick actions (admin only)"))))
                                .build())
                        .type(DialogType.multiAction(List.of(
                                button("Open txAdmin Menu", "menu"),
                                button("Toggle Noclip", "noclip"),
                                button("Player List", "players"),
                                button("God Mode", "god"),
                                button("Heal Myself", "heal"),
                                button("Keybind Settings", "keys")
                        )).columns(1).build())));

        context.getLifecycleManager().registerEventHandler(LifecycleEvents.TAGS.postFlatten(RegistryKey.DIALOG),
                event -> event.registrar().addToTag(DialogTagKeys.QUICK_ACTIONS, Set.of(DialogKeys.create(QUICK))));
    }
}
