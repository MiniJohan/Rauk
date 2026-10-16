package rauk.com.client.fx;

import java.util.concurrent.CompletableFuture;

import org.jetbrains.annotations.Nullable;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import rauk.com.hotel.HotelBuilder;
import rauk.com.hotel.HotelFrame;
import rauk.com.hotel.HotelPlan;
import rauk.com.hotel.HotelPlan.Style;
import rauk.com.network.HotelStatePayload;
import rauk.com.sound.HotelSounds;

/**
 * The hotel's atmosphere, wherever you are in it (hotel design 6.3, 6.4):
 * <ul>
 * <li><b>Darkness stays dark</b>: inside, the brightness setting is ignored (the lightmap is made as
 * at its lowest), so everyone sees the hotel as it's meant to be seen.</li>
 * <li><b>Fog</b>: a faint haze into the dark, thicker in the burned wing (ash), the flooded
 * basement and the Cabinet (damp) and the crawlspaces (dust).</li>
 * <li><b>Sound</b>: no music in here; each part of the building sounds like itself, quietly, all
 * the time: creaks, pipes, plaster, wind at the shutters, the sea below, drips, a loose shutter,
 * burnt timbers and ash; a low rumble below ground and a draught through the burned and attic
 * floors. Never anything that sounds like Rauk.</li>
 * </ul>
 * The server says where the hotel is ({@link HotelStatePayload}); which space you're in comes from
 * the plan.
 */
public final class HotelAtmosphere {
	private static final RandomSource RANDOM = RandomSource.create();

	@Nullable
	private static HotelFrame frame;
	private static boolean power;
	private static volatile boolean planReady;
	private static boolean inside;
	@Nullable
	private static HotelPlan.Area area;
	/** 0 outside, 1 inside, eased: the fog thickens as you go in and lifts as you come out. */
	private static float mix;
	/** The fog as it is now, eased toward what the space you're in wants: start, end, colour. */
	private static float start = 4, end = 40, red = 0.025f, green = 0.025f, blue = 0.03f;
	@Nullable
	private static Bed rumble, draught;
	/** Off only to compare with plain Minecraft (the dev screenshots). */
	private static boolean enabled = true;

	private HotelAtmosphere() {
	}

	public static void setEnabled(boolean on) {
		enabled = on;
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(HotelStatePayload.TYPE, (payload, context) -> {
			frame = payload.present() ? new HotelFrame(BlockPos.of(payload.origin()), Rotation.values()[payload.rotation()]) : null;
			power = payload.power();
			if (frame != null && !planReady) {
				// The plan is worked out once, off the render thread (in single player the server has already).
				CompletableFuture.runAsync(() -> {
					HotelBuilder.plan();
					planReady = true;
				});
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			frame = null;
			inside = false;
			area = null;
			mix = 0;
		});
		ClientTickEvents.END_CLIENT_TICK.register(HotelAtmosphere::tick);
	}

	/** Whether the brightness setting is to be ignored right now. */
	public static boolean darkness() {
		return enabled && inside;
	}

	/** Whether the music is to be quiet: always, in the hotel. */
	public static boolean silence() {
		return enabled && inside;
	}

	/** Whether the hotel's fog is in the air at all, and how much (0 to 1). */
	public static float fog() {
		return enabled ? mix : 0;
	}

	public static float fogStart() {
		return start;
	}

	public static float fogEnd() {
		return end;
	}

	public static float fogRed() {
		return red;
	}

	public static float fogGreen() {
		return green;
	}

	public static float fogBlue() {
		return blue;
	}

	/** Whether the hotel's power is on (the server says). */
	public static boolean power() {
		return power;
	}

	private static void tick(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null || frame == null || mc.level.dimension() != Level.OVERWORLD) {
			inside = false;
		} else {
			BlockPos at = BlockPos.containing(player.getEyePosition());
			inside = frame.inBuilding(at);
			int[] here = frame.toPlan(at);
			// The courtyard is open to the sky: out there it's as light (and as clear) as anywhere outside.
			if (inside && HotelPlan.inCourtyard(here[0], here[1], here[2])) {
				inside = false;
			}
			if (inside && planReady) {
				int[] c = frame.toPlan(at);
				HotelPlan plan = HotelBuilder.plan();
				HotelPlan.Area a = plan.owner(c[0], c[1], c[2]);
				if (a == null) {
					a = plan.owner(c[0], c[1] - 1, c[2]);
				}
				if (a != null) {
					area = a;
				}
			}
		}
		mix = approach(mix, inside ? 1 : 0, 0.04f);
		float[] want = fogFor(area);
		start = approach(start, want[0], 0.3f);
		end = approach(end, want[1], 0.6f);
		red = approach(red, want[2], 0.002f);
		green = approach(green, want[3], 0.002f);
		blue = approach(blue, want[4], 0.002f);
		sounds(mc, player);
	}

	/**
	 * The fog a space wants: where it starts and ends, and its colour. Ash in the burned wing,
	 * damp below ground and in the Cabinet, dust in the crawlspaces, otherwise a faint haze into the
	 * dark.
	 */
	private static float[] fogFor(@Nullable HotelPlan.Area a) {
		if (a == null) {
			return new float[] {4, 40, 0.025f, 0.025f, 0.03f};
		}
		Style s = a.style();
		HotelPlan.Level lv = a.level();
		if (s == Style.RUINED) {
			return new float[] {0, 18, 0.085f, 0.078f, 0.072f};
		}
		if (s == Style.WET || s == Style.DEN || lv == HotelPlan.Level.B2) {
			return new float[] {0, 15, 0.045f, 0.06f, 0.065f};
		}
		if (s == Style.HIDDEN) {
			return new float[] {0, 12, 0.05f, 0.045f, 0.04f};
		}
		if (s == Style.WRECKED) {
			return new float[] {2, 26, 0.06f, 0.055f, 0.05f};
		}
		if (s == Style.MACHINE || s == Style.SERVICE || s == Style.KITCHEN || lv == HotelPlan.Level.B1) {
			return new float[] {2, 28, 0.04f, 0.04f, 0.042f};
		}
		return new float[] {4, 40, 0.025f, 0.025f, 0.03f};
	}

	// --- sound --------------------------------------------------------------------------------

	private static void sounds(Minecraft mc, @Nullable LocalPlayer player) {
		HotelPlan.Area a = inside && enabled ? area : null;
		Style s = a == null ? null : a.style();
		HotelPlan.Level lv = a == null ? null : a.level();
		boolean below = lv == HotelPlan.Level.B1 || lv == HotelPlan.Level.B2;
		// The beds under everything: a low rumble below ground, a draught where the fire went through
		// and up under the roof.
		rumble = bed(mc, rumble, HotelSounds.RUMBLE, below ? 1 : 0);
		draught = bed(mc, draught, HotelSounds.DRAUGHT, s == Style.RUINED ? 1 : lv == HotelPlan.Level.L4 || lv == HotelPlan.Level.R ? 0.5f : 0);
		if (a == null || player == null || mc.isPaused()) {
			return;
		}
		boolean high = lv == HotelPlan.Level.L3 || lv == HotelPlan.Level.L4 || lv == HotelPlan.Level.R;
		boolean raining = mc.level != null && mc.level.isRaining();
		float windy = (high ? 0.6f : 1) * (raining ? 0.6f : 1);
		// Seconds between, on average, for each of the building's sounds here (0: not here).
		float creak, pipe, settle, wind, drip, shutter, timber = 0, ash = 0;
		switch (s) {
			case RUINED -> {
				creak = 12;
				pipe = 0;
				settle = 0;
				wind = 8;
				drip = 0;
				shutter = 20;
				timber = 8;
				ash = 6;
			}
			case WRECKED, RUNDOWN -> {
				creak = 10;
				pipe = 30;
				settle = 18;
				wind = 18;
				drip = 20;
				shutter = 25;
			}
			case WORN -> {
				creak = 14;
				pipe = 40;
				settle = 30;
				wind = 25;
				drip = 40;
				shutter = 45;
			}
			case SERVICE, KITCHEN, MACHINE -> {
				creak = 16;
				pipe = 10;
				settle = 30;
				wind = 40;
				drip = 14;
				shutter = 0;
			}
			case WET, DEN -> {
				creak = 30;
				pipe = 18;
				settle = 0;
				wind = 0;
				drip = 4;
				shutter = 0;
			}
			case HIDDEN -> {
				creak = 8;
				pipe = 25;
				settle = 10;
				wind = 0;
				drip = 0;
				shutter = 0;
			}
			// The good rooms are quiet: the false calm.
			default -> {
				creak = 25;
				pipe = 60;
				settle = 50;
				wind = 35;
				drip = 0;
				shutter = 0;
			}
		}
		float sea = lv == HotelPlan.Level.B2 ? 12 : lv == HotelPlan.Level.B1 ? 18 : lv == HotelPlan.Level.L1 ? 45 : 0;
		Vec3 ear = player.getEyePosition();
		maybe(mc, ear, HotelSounds.CREAK, creak);
		maybe(mc, ear, HotelSounds.PIPE, pipe);
		maybe(mc, ear, HotelSounds.SETTLE, settle);
		maybe(mc, ear, HotelSounds.WIND, wind * windy);
		maybe(mc, ear, HotelSounds.DRIP, drip);
		maybe(mc, ear, HotelSounds.SHUTTER, shutter * windy);
		maybe(mc, ear, HotelSounds.TIMBER, timber);
		maybe(mc, ear, HotelSounds.ASH, ash);
		maybe(mc, ear, HotelSounds.SEA, sea);
	}

	/** Now and then (every so many seconds on average), somewhere a few blocks off: a sound of the building. */
	private static void maybe(Minecraft mc, Vec3 ear, SoundEvent event, float everySeconds) {
		if (everySeconds <= 0 || RANDOM.nextFloat() >= 1 / (everySeconds * 20)) {
			return;
		}
		double angle = RANDOM.nextDouble() * Math.PI * 2, distance = 3 + RANDOM.nextDouble() * 9;
		double x = ear.x + Math.cos(angle) * distance, y = ear.y - 2 + RANDOM.nextDouble() * 5, z = ear.z + Math.sin(angle) * distance;
		mc.getSoundManager().play(new SimpleSoundInstance(event, SoundSource.AMBIENT, 1, 0.92f + RANDOM.nextFloat() * 0.16f, RANDOM, x, y, z));
	}

	/** Keeps a bed sound going toward a volume (0 to 1 of its own), starting it when wanted and letting it fade out. */
	@Nullable
	private static Bed bed(Minecraft mc, @Nullable Bed bed, SoundEvent event, float target) {
		if ((bed == null || bed.isStopped()) && target > 0) {
			bed = new Bed(event);
			mc.getSoundManager().play(bed);
		}
		if (bed != null) {
			bed.target = target;
		}
		return bed;
	}

	/** A looping sound under everything, fading in and out as you go from one part of the hotel to another. */
	private static final class Bed extends AbstractTickableSoundInstance {
		float target, level;

		Bed(SoundEvent event) {
			super(event, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
			looping = true;
			delay = 0;
			volume = 0.001f;
			relative = true;
			attenuation = SoundInstance.Attenuation.NONE;
		}

		@Override
		public void tick() {
			level = approach(level, target, 0.01f);
			volume = Math.max(level, 0.001f);
			if (target <= 0 && level <= 0.001f) {
				stop();
			}
		}

		@Override
		public boolean canStartSilent() {
			return true;
		}
	}

	private static float approach(float from, float to, float step) {
		return from < to ? Math.min(to, from + step) : Math.max(to, from - step);
	}
}
