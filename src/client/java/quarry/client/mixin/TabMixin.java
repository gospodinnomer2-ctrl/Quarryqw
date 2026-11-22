package quarry.client.mixin;

import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import quarry.client.Config;
import quarry.client.Presence;

/** В списке игроков (TAB) рядом с ником тех, кто тоже играет с Quarry, появляется значок. */
@Mixin(PlayerTabOverlay.class)
public abstract class TabMixin {
    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true, require = 0)
    private void quarry$badge(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
        if (!Config.get("quarryusers") || !Presence.isUser(info.getProfile().getName())) return;
        Component badge = Component.literal("\u25C6 ").withStyle(Style.EMPTY.withColor(Config.accent() & 0x00FFFFFF));
        cir.setReturnValue(badge.copy().append(cir.getReturnValue()));
    }
}
