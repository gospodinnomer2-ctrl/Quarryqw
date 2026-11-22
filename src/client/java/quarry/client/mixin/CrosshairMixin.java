package quarry.client.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import quarry.client.Config;

/** Custom Crosshair: обычный прицел скрывается, вместо него рисуется наш. */
@Mixin(Gui.class)
public abstract class CrosshairMixin {
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true, require = 0)
    private void quarry$hideCrosshair(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
        if (Config.get("crosshair")) ci.cancel();
    }
}
