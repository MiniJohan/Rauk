package rauk.com.sound;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import rauk.com.Rauk;
import rauk.com.entity.RaukEntity;

/**
 * Rauk's sounds (design doc 8): silence and physical noise, no voice at all. Every
 * one of them is played by the client, which muffles it through walls and floors but not through
 * doors. The server only says what happened (an entity event); footsteps, scratching, breathing
 * and the pops of squeezing come from the client's animation itself.
 */
public final class RaukSounds {
	/** Entity events the server sends for the client to sound out. */
	public static final byte EV_GRAB = 100, EV_BITE = 101, EV_SNAP = 102, EV_KNOCK = 107, EV_TEAR = 108, EV_CHEW = 109,
			EV_HOOK = 110, EV_PIN = 111, EV_KILL_SILENT = 113, EV_LAND = 114, EV_LAND_HEAVY = 115;

	public static final SoundEvent KNOCK_WOOD = register("knock_wood"), KNOCK_STONE = register("knock_stone"),
			BREATHE = register("breathe"), BREATH_CATCH = register("breath_catch"), CREAK = register("creak"),
			CLICK = register("click"), STEP_SOFT = register("step_soft"), POUND = register("pound"), SCRATCH = register("scratch"),
			POP = register("pop"), DRAG = register("drag"), GRAB = register("grab"), BITE = register("bite"),
			TEAR = register("tear"), CHEW = register("chew"), LAND = register("land"), SNAP = register("snap"),
			PANT = register("pant"), SKID = register("skid");

	private RaukSounds() {
	}

	private static SoundEvent register(String name) {
		var id = Rauk.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static void init() {
	}

	/** Tells everyone near enough to hear it that this happened. */
	public static void event(RaukEntity rauk, byte id) {
		if (rauk.level() instanceof ServerLevel level) {
			level.broadcastEntityEvent(rauk, id);
		}
	}
}
