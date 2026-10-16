package rauk.com.client.fx;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import rauk.com.item.FlashlightItem;

/**
 * The flashlight's beam (hotel design 6.1). The game has no light that moves, so here, ten times
 * a second, the blocks the beam of every lit flashlight in sight falls on are worked out ray by ray,
 * brightest close up and fading to its reach, and the game is told those blocks are that bright
 * ({@code LevelRendererMixin} for the world, {@code EntityRendererMixin} for whatever stands in
 * the beam); the parts of the world whose light changed are drawn again. The beam stops at walls,
 * doors and furniture, and goes on through glass and lattice.
 *
 * <p>Not working yet (rauk-design 15): the blocks are worked out, but the parts of the world they
 * are in aren't drawn again with the beam's light, so nothing shows. (With it working, the dark
 * round the beam is to look darker as your eyes get used to it, 6.1.)
 */
public final class Flashlight {
	/** How often the beam is worked out again, in ticks. */
	private static final int EVERY = 2;
	/** The beam's light close up and at its reach, and how much dimmer its edge is than its middle. */
	private static final int NEAR = 15, FAR = 4;
	private static final double EDGE = 0.3, STEP = 0.3;
	/** The rays across the cone: rings at a third, two thirds and all of its width, and how many on each. */
	private static final double[] RINGS = {0, 1 / 3.0, 2 / 3.0, 1};
	private static final int[] RAYS = {1, 6, 12, 18};

	/** The light the beams add, by block (a {@link BlockPos#asLong} key); replaced whole, never changed, so the chunk builders can read it. */
	private static volatile Long2IntMap lit = new Long2IntOpenHashMap();
	private static int ticks;

	private Flashlight() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(Flashlight::tick);
	}

	/** The light a beam adds at a block, or 0. */
	public static int at(BlockPos pos) {
		Long2IntMap m = lit;
		return m.isEmpty() ? 0 : m.get(pos.asLong());
	}

	private static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level == null) {
			lit = new Long2IntOpenHashMap();
			return;
		}
		if (++ticks % EVERY != 0) {
			return;
		}
		Long2IntOpenHashMap next = new Long2IntOpenHashMap();
		Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
		for (Player p : level.players()) {
			if (p.position().distanceToSqr(camera) < 96 * 96 && FlashlightItem.carriesLit(p)) {
				beam(level, p, next);
			}
		}
		Long2IntMap was = lit;
		if (next.isEmpty() && was.isEmpty() || next.equals(was)) {
			return;
		}
		// Draw again every part of the world where a block's light changed, and its neighbours'
		// parts where a face it lights is across the border.
		LongSet sections = new LongOpenHashSet();
		for (Long2IntMap.Entry e : next.long2IntEntrySet()) {
			if (was.get(e.getLongKey()) != e.getIntValue()) {
				changed(e.getLongKey(), sections);
			}
		}
		for (Long2IntMap.Entry e : was.long2IntEntrySet()) {
			if (!next.containsKey(e.getLongKey())) {
				changed(e.getLongKey(), sections);
			}
		}
		lit = next;
		for (long s : sections) {
			mc.levelRenderer.setSectionDirty(SectionPos.x(s), SectionPos.y(s), SectionPos.z(s));
		}
	}

	private static void changed(long block, LongSet sections) {
		BlockPos pos = BlockPos.of(block);
		sections.add(SectionPos.asLong(pos));
		for (Direction d : Direction.values()) {
			sections.add(SectionPos.asLong(pos.relative(d)));
		}
	}

	/** One flashlight's beam: rays across its cone from the holder's eyes, and a little light spilled back round them. */
	private static void beam(ClientLevel level, Player holder, Long2IntOpenHashMap out) {
		Vec3 eye = holder.getEyePosition();
		Vec3 look = holder.getLookAngle();
		Vec3 right = look.cross(Math.abs(look.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
		Vec3 up = right.cross(look).normalize();
		double width = Math.tan(Math.toRadians(FlashlightItem.HALF_ANGLE));
		for (int ring = 0; ring < RINGS.length; ring++) {
			for (int i = 0; i < RAYS[ring]; i++) {
				double a = Math.PI * 2 * (i + ring * 0.5) / RAYS[ring], r = RINGS[ring] * width;
				Vec3 dir = look.add(right.scale(Math.cos(a) * r)).add(up.scale(Math.sin(a) * r)).normalize();
				march(level, eye, dir, RINGS[ring], out);
			}
		}
		put(out, BlockPos.containing(eye), 6);
		put(out, BlockPos.containing(holder.position()), 5);
	}

	/** Along one ray until something stops it, lighting each block it passes through. */
	private static void march(ClientLevel level, Vec3 from, Vec3 dir, double edge, Long2IntOpenHashMap out) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		long last = Long.MIN_VALUE;
		Vec3 prev = from;
		for (double t = STEP; t < FlashlightItem.RANGE; t += STEP) {
			Vec3 at = from.add(dir.scale(t));
			pos.set(Mth.floor(at.x), Mth.floor(at.y), Mth.floor(at.z));
			long key = pos.asLong();
			if (key == last) {
				prev = at;
				continue;
			}
			last = key;
			BlockState state = level.getBlockState(pos);
			if (!state.isAir() && !seeThrough(state)) {
				if (state.isSolidRender(level, pos)) {
					return;
				}
				// Something smaller than a block (a door, a chair): lit if the ray hits it, and the beam stops there.
				VoxelShape shape = state.getShape(level, pos);
				if (!shape.isEmpty() && shape.clip(prev, from.add(dir.scale(t + STEP * 3)), pos) != null) {
					put(out, pos, light(t, edge));
					return;
				}
			}
			put(out, pos, light(t, edge));
			prev = at;
		}
	}

	/** Glass, panes, bars and lattice let the beam through. */
	private static boolean seeThrough(BlockState state) {
		return state.is(BlockTags.IMPERMEABLE) || state.getBlock() instanceof IronBarsBlock
				|| BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().contains("lattice");
	}

	/** The beam's light at a distance along it, dimmer towards its edge. */
	private static int light(double t, double edge) {
		double near = 3;
		double f = t < near ? 1 : 1 - (t - near) / (FlashlightItem.RANGE - near);
		double v = (FAR + (NEAR - FAR) * f) * (1 - EDGE * edge);
		return Mth.clamp((int) Math.round(v), 0, 15);
	}

	private static void put(Long2IntOpenHashMap out, BlockPos pos, int light) {
		long k = pos.asLong();
		if (light > out.get(k)) {
			out.put(k, light);
		}
	}
}
