package rauk.com.entity.mind;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import rauk.com.entity.RaukEntity;

/** Who has the debug overlay on ({@code /rauk debug}), and sending them Rauk's mind. */
public final class RaukDebug {
	private static final Set<UUID> WATCHERS = new HashSet<>();

	private RaukDebug() {
	}

	/** Turns the overlay on or off for a player; returns whether it is now on. */
	public static boolean toggle(ServerPlayer player) {
		if (!WATCHERS.remove(player.getUUID())) {
			WATCHERS.add(player.getUUID());
			return true;
		}
		return false;
	}

	static void send(ServerLevel level, RaukEntity rauk, RaukMind mind) {
		if (WATCHERS.isEmpty()) {
			return;
		}
		var payload = mind.snapshot(level);
		for (ServerPlayer p : level.players()) {
			if (WATCHERS.contains(p.getUUID()) && p.distanceTo(rauk) < 96 && ServerPlayNetworking.canSend(p, payload.type())) {
				ServerPlayNetworking.send(p, payload);
			}
		}
	}
}
