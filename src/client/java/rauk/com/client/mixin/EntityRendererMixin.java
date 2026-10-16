package rauk.com.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import rauk.com.client.fx.Flashlight;

/** Whatever stands in a flashlight's beam is lit by it, Rauk above all (hotel design 6.1). */
@Mixin(EntityRenderer.class)
public class EntityRendererMixin {
	@Inject(method = "getBlockLightLevel", at = @At("RETURN"), cancellable = true)
	private void rauk$beam(Entity entity, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
		int beam = Flashlight.at(pos);
		if (beam > cir.getReturnValueI()) {
			cir.setReturnValue(beam);
		}
	}
}
