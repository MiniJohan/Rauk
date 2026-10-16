package rauk.com.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import rauk.com.client.fx.Flashlight;

/** The world as it's drawn: a block in a flashlight's beam is as bright as the beam (hotel design 6.1). */
@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
	@Inject(method = "getLightColor(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)I",
			at = @At("RETURN"), cancellable = true)
	private static void rauk$beam(BlockAndTintGetter level, BlockState state, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
		int beam = Flashlight.at(pos);
		if (beam > 0) {
			int packed = cir.getReturnValueI();
			if (beam > LightTexture.block(packed)) {
				cir.setReturnValue(LightTexture.pack(beam, LightTexture.sky(packed)));
			}
		}
	}
}
