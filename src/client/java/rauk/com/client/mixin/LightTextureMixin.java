package rauk.com.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.renderer.LightTexture;
import rauk.com.client.fx.HotelAtmosphere;

/**
 * Inside the hotel the brightness setting is ignored (hotel design 6.3): the lightmap is made as if
 * it were at its lowest, so darkness stays dark for everyone.
 */
@Mixin(LightTexture.class)
public class LightTextureMixin {
	@WrapOperation(method = "updateLightTexture", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;"))
	private Object rauk$darkness(OptionInstance<?> option, Operation<Object> original) {
		Object value = original.call(option);
		if (HotelAtmosphere.darkness() && option == Minecraft.getInstance().options.gamma()) {
			return 0.0;
		}
		return value;
	}
}
