package quarry.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import quarry.client.NoRender;

/** NoRender: игрок «не горит» только для отрисовки на своём экране (огонь перед лицом). */
@Mixin(Entity.class)
public class EntityFireMixin {
    @Inject(method = "isOnFire", at = @At("HEAD"), cancellable = true, require = 0)
    private void quarry$hideFire(CallbackInfoReturnable<Boolean> cir) {
        if (NoRender.hideFire() && (Object) this == Minecraft.getInstance().player) cir.setReturnValue(false);
    }
}
