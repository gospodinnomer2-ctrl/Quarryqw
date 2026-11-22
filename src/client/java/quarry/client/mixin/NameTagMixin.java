package quarry.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import quarry.client.Config;

/** NameTags: обычный ник над игроком скрывается, потому что вместо него рисуется наша плашка. */
@Mixin(EntityRenderer.class)
public abstract class NameTagMixin {
    @Inject(method = "renderNameTag", at = @At("HEAD"), cancellable = true, require = 0)
    private void quarry$hideNameTag(EntityRenderState state, Component name, PoseStack poseStack,
                                    MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (state instanceof PlayerRenderState && Config.get("nametags")) ci.cancel();
    }
}
