package rauk.com.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.material.FogType;
import rauk.com.client.fx.HotelAtmosphere;

/**
 * The hotel's haze (hotel design 6.4): inside, the terrain fog closes in and darkens to the colour
 * of the space you're in, easing in as you enter and out as you leave. Not under water or lava,
 * and not over blindness or darkness, which bring their own.
 */
@Mixin(FogRenderer.class)
public class FogRendererMixin {
	@Shadow
	private static float fogRed;
	@Shadow
	private static float fogGreen;
	@Shadow
	private static float fogBlue;

	@Inject(method = "setupColor", at = @At("TAIL"))
	private static void rauk$colour(Camera camera, float partialTick, ClientLevel level, int renderDistance, float bossDarken, CallbackInfo ci) {
		float mix = HotelAtmosphere.fog();
		if (mix <= 0 || camera.getFluidInCamera() != FogType.NONE) {
			return;
		}
		fogRed = Mth.lerp(mix, fogRed, HotelAtmosphere.fogRed());
		fogGreen = Mth.lerp(mix, fogGreen, HotelAtmosphere.fogGreen());
		fogBlue = Mth.lerp(mix, fogBlue, HotelAtmosphere.fogBlue());
		RenderSystem.clearColor(fogRed, fogGreen, fogBlue, 0.0F);
	}

	@Inject(method = "setupFog", at = @At("TAIL"))
	private static void rauk$haze(Camera camera, FogRenderer.FogMode mode, float farPlane, boolean thick, float partialTick, CallbackInfo ci) {
		float mix = HotelAtmosphere.fog();
		if (mix <= 0 || mode != FogRenderer.FogMode.FOG_TERRAIN || camera.getFluidInCamera() != FogType.NONE) {
			return;
		}
		if (camera.getEntity() instanceof LivingEntity living && (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS))) {
			return;
		}
		RenderSystem.setShaderFogStart(Mth.lerp(mix, RenderSystem.getShaderFogStart(), HotelAtmosphere.fogStart()));
		RenderSystem.setShaderFogEnd(Mth.lerp(mix, RenderSystem.getShaderFogEnd(), Math.min(farPlane, HotelAtmosphere.fogEnd())));
		RenderSystem.setShaderFogShape(FogShape.SPHERE);
	}
}
