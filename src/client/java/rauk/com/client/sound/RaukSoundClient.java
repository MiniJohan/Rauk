package rauk.com.client.sound;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import rauk.com.client.anim.RaukAnimator;
import rauk.com.client.fx.RaukEffects;
import rauk.com.client.model.RaukRig.V;
import rauk.com.entity.RaukEntity;
import rauk.com.sound.RaukSounds;

/**
 * Rauk's sounds on the client (design doc 8). Every sound is placed exactly where it happens
 * (the knuckles on the door, the foot on the floor) and carried the way sound carries in a
 * building: through open air, doorways, vents and shafts it comes round corners at full strength;
 * through solid walls and floors it is muffled, more for each wall. Doors don't muffle.
 *
 * <p>For that the client keeps, each tick, a map of how far sound has to travel through open
 * space from the listener to every cell within 20 blocks; a sound from a cell on the map takes
 * the open route, a sound from anywhere else goes through the walls in between.
 */
public final class RaukSoundClient {
	private static final int RANGE = 20, BUDGET = 12000;
	private static final RandomSource RANDOM = RandomSource.create();
	/** Steps through open space from the listener's cell to each cell, for the open route. */
	private static final Map<Long, Integer> field = new HashMap<>();
	private static BlockPos fieldFrom = BlockPos.ZERO;
	private static long fieldTick = -100;

	private RaukSoundClient() {
	}

	public static void init() {
		RaukEntity.clientEvents = RaukSoundClient::event;
		ClientTickEvents.END_CLIENT_TICK.register(RaukSoundClient::tick);
	}

	private static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level == null || mc.player == null) {
			return;
		}
		Vec3 ear = mc.gameRenderer.getMainCamera().getPosition();
		// Off screen it still moves, breathes and steps: its body is worked out even when not drawn.
		boolean near = false;
		for (Entity e : level.entitiesForRendering()) {
			if (e instanceof RaukEntity rauk && rauk.distanceToSqr(ear) < 64 * 64) {
				near = true;
				RaukAnimator animator = RaukAnimator.of(rauk);
				if (animator.lastUpdateTick < rauk.tickCount - 1) {
					animator.update(rauk, 0);
				}
			}
		}
		// The open-air map is only needed while there is something to hear.
		BlockPos cell = BlockPos.containing(ear);
		if (near && (!cell.equals(fieldFrom) || level.getGameTime() - fieldTick > 10)) {
			flood(level, cell);
			fieldTick = level.getGameTime();
		}
	}

	/** The open-air distance map: a breadth-first flood from the listener through every cell sound passes freely. */
	private static void flood(Level level, BlockPos from) {
		field.clear();
		fieldFrom = from;
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		field.put(from.asLong(), 0);
		queue.add(from);
		while (!queue.isEmpty() && field.size() < BUDGET) {
			BlockPos p = queue.poll();
			int d = field.get(p.asLong());
			for (Direction dir : Direction.values()) {
				BlockPos n = p.relative(dir);
				if (Math.abs(n.getX() - from.getX()) > RANGE || Math.abs(n.getY() - from.getY()) > RANGE
						|| Math.abs(n.getZ() - from.getZ()) > RANGE || field.containsKey(n.asLong()) || !open(level, n)) {
					continue;
				}
				field.put(n.asLong(), d + 1);
				queue.add(n);
			}
		}
	}

	/** Sound passes freely: anything but a full block, and any door, gate or trapdoor. */
	private static boolean open(Level level, BlockPos p) {
		BlockState state = level.getBlockState(p);
		return !state.isCollisionShapeFullBlock(level, p) || state.getBlock() instanceof DoorBlock
				|| state.getBlock() instanceof TrapDoorBlock || state.getBlock() instanceof FenceGateBlock;
	}

	// --- playing ----------------------------------------------------------------------------------

	/** Plays a sound at a point, carried round corners or muffled through walls as it would be. */
	public static void play(SoundEvent sound, Vec3 at, float volume, float pitch) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		Vec3 ear = mc.gameRenderer.getMainCamera().getPosition();
		BlockPos cell = BlockPos.containing(at);
		Integer steps = field.get(cell.asLong());
		if (steps == null) {
			for (Direction d : Direction.values()) {
				Integer s = field.get(cell.relative(d).asLong());
				if (s != null && (steps == null || s < steps)) {
					steps = s + 1;
				}
			}
		}
		float factor = 1;
		if (steps != null) {
			// The open route: a long way round is a little quieter than a straight line.
			int manhattan = Math.abs(cell.getX() - fieldFrom.getX()) + Math.abs(cell.getY() - fieldFrom.getY())
					+ Math.abs(cell.getZ() - fieldFrom.getZ());
			double detour = steps / (double) Math.max(1, manhattan);
			factor = (float) Math.clamp(1.4 / detour, 0.45, 1);
		} else {
			int walls = walls(mc.level, ear, at);
			factor = (float) Math.pow(0.32, Math.min(walls, 4));
			pitch *= walls > 0 ? 0.9f : 1;
		}
		if (factor < 0.02f) {
			return;
		}
		mc.getSoundManager().play(new SimpleSoundInstance(sound, SoundSource.HOSTILE, volume * factor, pitch, RANDOM, at.x, at.y, at.z));
	}

	/** Full blocks between two points, doors not counted. */
	private static int walls(Level level, Vec3 from, Vec3 to) {
		Set<Long> seen = new HashSet<>();
		int walls = 0;
		double length = from.distanceTo(to);
		Vec3 step = to.subtract(from).normalize().scale(0.4);
		Vec3 p = from;
		BlockPos end = BlockPos.containing(to), start = BlockPos.containing(from);
		for (double t = 0; t < length; t += 0.4, p = p.add(step)) {
			BlockPos b = BlockPos.containing(p);
			if (b.equals(start) || b.equals(end) || !seen.add(b.asLong())) {
				continue;
			}
			if (!open(level, b)) {
				walls++;
			}
		}
		return walls;
	}

	private static float vary(float pitch) {
		return pitch * (0.92f + 0.16f * RANDOM.nextFloat());
	}

	private static Vec3 vec(V v) {
		return new Vec3(v.x(), v.y(), v.z());
	}

	// --- what the server says happened --------------------------------------------------------

	private static void event(RaukEntity rauk, byte id) {
		Minecraft mc = Minecraft.getInstance();
		RaukAnimator animator = RaukAnimator.of(rauk);
		Vec3 head = animator.faceWorld.equals(V.ZERO) ? rauk.getEyePosition() : vec(animator.faceWorld);
		boolean mine = mc.player != null && rauk.grabbed() == mc.player;
		Vec3 action = rauk.actionPoint();
		switch (id) {
			case RaukSounds.EV_GRAB -> {
				play(RaukSounds.GRAB, head, 1.3f, vary(1));
				if (mine) {
					RaukEffects.shake(2.5);
				}
			}
			case RaukSounds.EV_BITE -> {
				play(RaukSounds.BITE, head, 1.8f, vary(0.9f));
				if (mine) {
					RaukEffects.flash(1.0);
					RaukEffects.shake(5);
				}
			}
			case RaukSounds.EV_SNAP, RaukSounds.EV_HOOK -> play(RaukSounds.SNAP, head, 1.3f, vary(1));
			case RaukSounds.EV_KNOCK -> knock(rauk, action);
			case RaukSounds.EV_TEAR -> play(RaukSounds.TEAR, action, 1.2f, vary(1));
			case RaukSounds.EV_CHEW -> play(RaukSounds.CHEW, action, 0.9f, vary(1));
			case RaukSounds.EV_PIN -> {
				play(RaukSounds.LAND, rauk.position(), 1.4f, vary(1));
				RaukEffects.shakeNear(rauk.getX(), rauk.getY(), rauk.getZ(), 2.0, 8);
			}
			case RaukSounds.EV_KILL_SILENT -> play(RaukSounds.BITE, head, 0.4f, vary(1.3f));
			case RaukSounds.EV_LAND -> play(RaukSounds.LAND, rauk.position(), 0.8f, vary(1.1f));
			case RaukSounds.EV_LAND_HEAVY -> {
				play(RaukSounds.LAND, rauk.position(), 1.8f, vary(0.85f));
				RaukEffects.shakeNear(rauk.getX(), rauk.getY(), rauk.getZ(), 3.0, 8);
			}
			default -> {
			}
		}
	}

	/** A knock, dry and unhurried, at the knuckles: hollow on wood and doors, harder on stone. */
	private static void knock(RaukEntity rauk, Vec3 at) {
		Level level = rauk.level();
		BlockPos center = BlockPos.containing(at);
		BlockState state = level.getBlockState(center);
		if (state.isAir()) {
			for (Direction d : Direction.values()) {
				BlockState s = level.getBlockState(center.relative(d));
				if (!s.isAir()) {
					state = s;
					break;
				}
			}
		}
		SoundType type = state.getSoundType();
		boolean wood = state.getBlock() instanceof DoorBlock || type == SoundType.WOOD || type == SoundType.NETHER_WOOD
				|| type == SoundType.BAMBOO_WOOD || type == SoundType.CHERRY_WOOD;
		play(wood ? RaukSounds.KNOCK_WOOD : RaukSounds.KNOCK_STONE, at, 1.6f, vary(1));
	}

	// --- what the body does ---------------------------------------------------------------------

	/**
	 * A hand or foot coming down. On the floor: nail clicks on tile and stone, creaks on wood, next
	 * to nothing on carpet, pounding at a gallop (and the floor shakes); on walls and ceilings,
	 * scratching on the plaster.
	 */
	public static void footfall(RaukEntity rauk, V at, boolean arm, double speed, boolean gallop) {
		if (rauk.isDeadOrDying()) {
			return;
		}
		Vec3 p = vec(at);
		if (rauk.surface() != Direction.DOWN) {
			if (RANDOM.nextFloat() < 0.7f) {
				play(RaukSounds.SCRATCH, p, 0.4f + 0.05f * (float) Math.min(speed, 4), vary(1));
			}
			return;
		}
		BlockState floor = rauk.level().getBlockState(BlockPos.containing(p.x, p.y - 0.2, p.z));
		BlockState on = rauk.level().getBlockState(BlockPos.containing(p.x, p.y + 0.05, p.z));
		SoundType type = (on.isAir() ? floor : on).getSoundType();
		if (gallop) {
			if (!arm) {
				play(RaukSounds.POUND, p, 1.1f, vary(1));
				RaukEffects.shakeNear(p.x, p.y, p.z, 0.7, 10);
			} else {
				play(hard(type) ? RaukSounds.CLICK : RaukSounds.STEP_SOFT, p, 0.6f, vary(1));
			}
			return;
		}
		if (type == SoundType.WOOL || type == SoundType.MOSS_CARPET) {
			play(RaukSounds.STEP_SOFT, p, 0.1f, vary(1));
		} else if (type == SoundType.WOOD || type == SoundType.NETHER_WOOD || type == SoundType.BAMBOO_WOOD
				|| type == SoundType.CHERRY_WOOD) {
			play(RaukSounds.STEP_SOFT, p, 0.25f, vary(1));
			if (RANDOM.nextFloat() < 0.35f) {
				play(RaukSounds.CREAK, p, 0.7f, vary(0.9f));
			}
		} else if (hard(type)) {
			play(RaukSounds.CLICK, p, 0.45f, vary(1));
		} else {
			play(RaukSounds.STEP_SOFT, p, 0.3f, vary(1));
		}
	}

	private static boolean hard(SoundType type) {
		return type == SoundType.STONE || type == SoundType.DEEPSLATE || type == SoundType.DEEPSLATE_BRICKS
				|| type == SoundType.DEEPSLATE_TILES || type == SoundType.POLISHED_DEEPSLATE || type == SoundType.TUFF
				|| type == SoundType.TUFF_BRICKS || type == SoundType.POLISHED_TUFF || type == SoundType.CALCITE
				|| type == SoundType.GLASS || type == SoundType.METAL || type == SoundType.COPPER || type == SoundType.NETHER_BRICKS
				|| type == SoundType.MUD_BRICKS || type == SoundType.BONE_BLOCK || type == SoundType.DRIPSTONE_BLOCK
				|| type == SoundType.AMETHYST || type == SoundType.LODESTONE || type == SoundType.ANCIENT_DEBRIS;
	}

	/** One breath: slow, deep and wet at rest, ragged panting after a burst. */
	public static void breath(RaukEntity rauk, V head, boolean pant, double tension) {
		if (rauk.isDeadOrDying()) {
			return;
		}
		if (pant) {
			play(RaukSounds.PANT, vec(head), 0.9f, vary(1));
		} else {
			play(RaukSounds.BREATHE, vec(head), (float) (0.55 - 0.25 * tension), vary(1));
		}
	}

	/** The breath caught and held: it is about to act. */
	public static void breathHeld(RaukEntity rauk, V head) {
		if (!rauk.isDeadOrDying()) {
			play(RaukSounds.BREATH_CATCH, vec(head), 0.7f, vary(1));
		}
	}

	/** The shoulders collapsing to get through a gap: joints popping. */
	public static void squeeze(RaukEntity rauk, V chest) {
		play(RaukSounds.POP, vec(chest), 0.9f, vary(1));
	}

	/** Skin dragging over the floor and the door frame, squeezing or crawling. */
	public static void drag(RaukEntity rauk, V at) {
		play(RaukSounds.DRAG, vec(at), 0.4f, vary(1));
	}

	/** A hard stop from a gallop: nails tearing at the floor, grit flying. */
	public static void skid(RaukEntity rauk, V at) {
		Vec3 p = vec(at);
		play(RaukSounds.SKID, p, 1.0f, vary(1));
		BlockState floor = rauk.level().getBlockState(BlockPos.containing(p.x, p.y - 0.2, p.z));
		if (!floor.isAir()) {
			for (int i = 0; i < 14; i++) {
				rauk.level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, floor), p.x + (RANDOM.nextDouble() - 0.5) * 1.2, p.y + 0.1,
						p.z + (RANDOM.nextDouble() - 0.5) * 1.2, (RANDOM.nextDouble() - 0.5) * 0.3, 0.15, (RANDOM.nextDouble() - 0.5) * 0.3);
			}
		}
	}
}
