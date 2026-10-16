package rauk.com.client.fx;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import rauk.com.network.LiftPayload;

/**
 * Riding a lift, as it feels (hotel design 3.2): a jolt as the car sets off and as it stops, and
 * all the way between a faint tremor, now and then a judder.
 */
public final class LiftRide {
	private static int ticks;

	private LiftRide() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(LiftPayload.TYPE, (payload, context) -> {
			ticks = payload.ticks();
			RaukEffects.shake(payload.jolt());
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (ticks > 0) {
				ticks--;
				RaukEffects.shake(0.12 + (client.level != null && client.level.random.nextInt(25) == 0 ? 0.5 : 0));
			}
		});
	}
}
