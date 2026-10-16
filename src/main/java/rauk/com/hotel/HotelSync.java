package rauk.com.hotel;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import rauk.com.network.HotelStatePayload;

/**
 * Keeps every player's client told where the hotel is and whether its power is on: once when they
 * join, and again whenever either changes (checked once a second).
 */
public final class HotelSync {
	/** What each player was last told. */
	private static final Map<UUID, HotelStatePayload> SENT = new HashMap<>();

	private HotelSync() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 != 0) {
				return;
			}
			HotelData data = HotelData.get(server.overworld());
			HotelFrame frame = data.frame();
			HotelStatePayload state = frame == null ? new HotelStatePayload(false, 0, 0, false)
					: new HotelStatePayload(true, frame.origin().asLong(), frame.rotation().ordinal(), data.power());
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (!state.equals(SENT.get(player.getUUID())) && ServerPlayNetworking.canSend(player, HotelStatePayload.TYPE)) {
					ServerPlayNetworking.send(player, state);
					SENT.put(player.getUUID(), state);
				}
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SENT.remove(handler.player.getUUID()));
	}
}
