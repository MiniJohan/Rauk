package rauk.com.entity.mind;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gameevent.EntityPositionSource;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.level.gameevent.PositionSource;
import net.minecraft.world.phys.Vec3;
import rauk.com.entity.RaukEntity;

/**
 * Rauk's ears: vanilla's game events (the same ones sculk sensors and the Warden use) within 32
 * blocks, handed to its mind to judge how loud each one is where it is.
 */
public final class RaukHearing implements GameEventListener {
	public static final int RADIUS = 32;

	private final RaukEntity rauk;
	private final PositionSource source;

	public RaukHearing(RaukEntity rauk) {
		this.rauk = rauk;
		this.source = new EntityPositionSource(rauk, 2.0f);
	}

	@Override
	public PositionSource getListenerSource() {
		return source;
	}

	@Override
	public int getListenerRadius() {
		return RADIUS;
	}

	@Override
	public boolean handleGameEvent(ServerLevel level, Holder<GameEvent> event, GameEvent.Context context, Vec3 at) {
		return rauk.mind().hear(level, event, context, at);
	}
}
