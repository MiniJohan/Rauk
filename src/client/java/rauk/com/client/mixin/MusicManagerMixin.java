package rauk.com.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.sounds.MusicManager;
import rauk.com.client.fx.HotelAtmosphere;

/** No music in the hotel (hotel design 6.4): what's playing stops as you go in, and nothing starts. */
@Mixin(MusicManager.class)
public abstract class MusicManagerMixin {
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void rauk$silence(CallbackInfo ci) {
		if (HotelAtmosphere.silence()) {
			((MusicManager) (Object) this).stopPlaying();
			ci.cancel();
		}
	}
}
