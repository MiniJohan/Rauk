package rauk.com.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import rauk.com.client.fx.RaukEffects;

/** Rauk's camera shake goes in where vanilla tilts the view when you are hurt. */
@Mixin(GameRenderer.class)
public class GameRendererMixin {
	@Inject(method = "bobHurt", at = @At("HEAD"))
	private void rauk$shake(PoseStack pose, float partialTick, CallbackInfo ci) {
		RaukEffects.apply(pose);
	}
}
