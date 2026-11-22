package quarry.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import quarry.client.Config;

/** Custom Hands: сдвиг и размер рук и предмета от первого лица. Матрица всегда сохраняется и восстанавливается парой push/pop. */
@Mixin(ItemInHandRenderer.class)
public abstract class HandsMixin {
    @Inject(method = "renderHandsWithItems", at = @At("HEAD"), require = 0)
    private void quarry$handsBegin(float partial, PoseStack poseStack, MultiBufferSource.BufferSource buffers,
                                   LocalPlayer player, int light, CallbackInfo ci) {
        poseStack.pushPose();
        if (Config.get("hands")) {
            poseStack.translate(Config.setting("hands", "x") / 100f, Config.setting("hands", "y") / 100f,
                    Config.setting("hands", "z") / 100f);
            float s = Config.setting("hands", "scale") / 100f;
            poseStack.scale(s, s, s);
        }
    }

    @Inject(method = "renderHandsWithItems", at = @At("RETURN"), require = 0)
    private void quarry$handsEnd(float partial, PoseStack poseStack, MultiBufferSource.BufferSource buffers,
                                 LocalPlayer player, int light, CallbackInfo ci) {
        poseStack.popPose();
    }
}
