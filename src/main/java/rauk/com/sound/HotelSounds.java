package rauk.com.sound;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import rauk.com.Rauk;

/**
 * The hotel's own sounds (hotel design 6.4): what the building does by itself, all the time and
 * quietly. Built from vanilla samples (sounds.json), none of them ones Rauk's sounds use, so
 * nothing the building does sounds like it.
 */
public final class HotelSounds {
	/** The building settling: wood creaking in the walls and floors. */
	public static final SoundEvent CREAK = register("amb_creak");
	/** A pipe knocking somewhere: metal, dull and far off. */
	public static final SoundEvent PIPE = register("amb_pipe");
	/** Plaster trickling down behind the paper. */
	public static final SoundEvent SETTLE = register("amb_settle");
	/** Wind finding a gap in the shutters. */
	public static final SoundEvent WIND = register("amb_wind");
	/** The sea against the cliff, a boom through the walls. */
	public static final SoundEvent SEA = register("amb_sea");
	public static final SoundEvent DRIP = register("amb_drip");
	/** A loose shutter tapping. */
	public static final SoundEvent SHUTTER = register("amb_shutter");
	/** Burnt timbers groaning in the ruined wing. */
	public static final SoundEvent TIMBER = register("amb_timber");
	/** Ash and char settling. */
	public static final SoundEvent ASH = register("amb_ash");
	/** Loops: the low rumble below ground, the draught through the burned and the attic floors. */
	public static final SoundEvent RUMBLE = register("amb_rumble"), DRAUGHT = register("amb_draught");
	/** A bulb buzzing, now and then a flicker. */
	public static final SoundEvent LAMP = register("lamp_buzz");
	/** The generator running (puzzle 2): the engine's thump and the dynamo's whine, on B2. */
	public static final SoundEvent GENERATOR = register("generator");

	private HotelSounds() {
	}

	private static SoundEvent register(String name) {
		ResourceLocation id = Rauk.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	/** Loads the class, registering them. */
	public static void init() {
	}
}
