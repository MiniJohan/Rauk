package rauk.com.entity.mind;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import rauk.com.Rauk;
import rauk.com.entity.RaukEntity;
import rauk.com.entity.RaukPosture;
import rauk.com.entity.move.RaukPathfinder;
import rauk.com.hotel.HotelBuilder;
import rauk.com.hotel.HotelData;
import rauk.com.hotel.HotelFrame;
import rauk.com.hotel.HotelHeat;
import rauk.com.entity.move.SurfaceNode;
import rauk.com.item.FlashlightItem;
import rauk.com.network.RaukDebugPayload;
import rauk.com.sound.RaukSounds;

/**
 * Rauk's senses and memory (design doc 3 and 6.1): it knows only what these tell it.
 *
 * <ul>
 * <li><b>Hearing</b> from vanilla game events: sprinting 24 blocks, walking 12, breaking blocks and
 * fighting 32, doors 24, eating 10. Halved through walls and floors; doors barely muffle. Sneaking
 * makes no sound it can hear unless it is listening hard.</li>
 * <li><b>Listening</b> at a door or wall: nothing is muffled, and even sneaking carries 4 blocks.</li>
 * <li><b>Tap and listen</b>: two slow knocks, then for two seconds anything that moves within
 * 16 blocks gives itself away, sneaking or not.</li>
 * <li><b>Vision</b> by light and movement: in the dark it sees movement at 40 and stillness at 16, in
 * light 32 and 12. A light carried in the dark shows at 48. It sees through glass but not walls, and
 * best straight ahead; out of the corner of its eye, movement at 60% and stillness at 5.</li>
 * <li><b>Smell</b>: scent trails up to two days old, prey behind a closed door within 3 blocks of it,
 * and blood (a badly hurt player) at 32.</li>
 * </ul>
 *
 * <p>Everything it senses about a player goes into a {@link Belief}; noises it can't put to anyone go
 * on a list of things to look into; what it sees changed in its hotel goes to its {@link Territory};
 * and what it learns over time goes into each player's {@link PlayerProfile}.
 */
public final class RaukMind {
	public static final int ACTION_NONE = 0, ACTION_TAP = 1, ACTION_LISTEN = 2;
	private static final int TAPS = 2, TAP_GAP = 14, LISTEN_TICKS = 40;

	private final RaukEntity rauk;
	private final Map<UUID, Belief> beliefs = new HashMap<>();
	private final Map<UUID, PlayerProfile> profiles = new HashMap<>();
	public final Territory territory = new Territory();
	private final Deque<Noise> noises = new ArrayDeque<>();
	private final Map<UUID, Vec3> lastPositions = new HashMap<>(), velocities = new HashMap<>();
	/** The prey around it this tick (players, and creatures tagged as prey). */
	private List<LivingEntity> preys = List.of();
	/**
	 * Reaching with the right hand (a lever, a light, into a vent); the head darting out to snap;
	 * hooking a shield away; the head round a corner, the body hidden; feeding on a kill on the floor.
	 */
	public static final int ACTION_REACH = 3, ACTION_LUNGE = 4, ACTION_HOOK = 5, ACTION_PEEK = 6, ACTION_FEED = 7;
	/** Places it has been hurt (design doc 6.6): position and how bad, fading over a few in-game hours. */
	private final java.util.List<double[]> danger = new java.util.ArrayList<>();
	/** Where its current plan wants it, and what for, for the debug overlay. */
	@Nullable
	public Vec3 tacticSpot;
	public String tacticLabel = "";

	/** Listening hard at a door or wall. */
	private boolean listening;
	private int action = ACTION_NONE, actionTick;
	private Vec3 actionAt = Vec3.ZERO;
	private BlockPos tapBlock = BlockPos.ZERO;
	/** Whether the last tap-and-listen gave anyone away. */
	private boolean tapHeard;

	@Nullable
	private ScentData.Hit scent;
	private long lastNotice;
	/** What the stand-in brain is doing, for the debug overlay. */
	public String status = "";

	/** Something heard: where, what, how loud (0 to 1) where Rauk is, when, and who made it if known. */
	public record Noise(Vec3 pos, String label, double loudness, long time, @Nullable UUID source) {
	}

	private static final boolean DEBUG = Boolean.getBoolean("rauk.devshot");

	public RaukMind(RaukEntity rauk) {
		this.rauk = rauk;
	}

	private long now() {
		return rauk.level().getGameTime();
	}

	// --- every tick ---------------------------------------------------------------------------

	public void tick(ServerLevel level) {
		long now = now();
		preys = Prey.around(level, rauk.position(), 64);
		trackPlayers(level);
		if (rauk.tickCount % 5 == 0) {
			// Creatures that are prey leave scent too (players lay theirs every tick, everywhere).
			for (LivingEntity prey : preys) {
				if (!(prey instanceof Player)) {
					ScentData.get(level).lay(prey, now);
				}
			}
		}
		for (Belief b : beliefs.values()) {
			b.tick();
		}
		if (rauk.tickCount % 4 == 0) {
			look(level, now);
		}
		if (rauk.tickCount % 2 == 0) {
			territory.look(level, eye(), now);
		}
		if (rauk.tickCount % 10 == 0) {
			smell(level, now);
		}
		if (rauk.tickCount % 20 == 0) {
			territory.lookForItems(level, eye(), now);
			noises.removeIf(n -> now - n.time() > 400);
		}
		if (rauk.tickCount % 1200 == 0) {
			for (PlayerProfile p : profiles.values()) {
				p.fade(now);
			}
			danger.forEach(d -> d[3] *= 0.9);
			danger.removeIf(d -> d[3] < 1);
			beliefs.values().removeIf(b -> b.confidence < 0.01);
		}
		tickAction(level, now);
		if (listening) {
			pollMovement(level, 4, 0.6, "listening");
		}
		if (rauk.tickCount % 5 == 0) {
			RaukDebug.send(level, rauk, this);
		}
	}

	private void trackPlayers(ServerLevel level) {
		for (LivingEntity p : preys) {
			Vec3 last = lastPositions.put(p.getUUID(), p.position());
			Vec3 v = last == null ? Vec3.ZERO : p.position().subtract(last);
			if (v.lengthSqr() > 4) {
				// A teleport isn't movement it could have seen.
				v = Vec3.ZERO;
				velocities.put(p.getUUID(), Vec3.ZERO);
			}
			velocities.merge(p.getUUID(), v, (a, b) -> a.lerp(b, 0.5));
		}
	}

	/** How a prey has been moving lately, in blocks per tick. */
	public Vec3 velocity(Entity p) {
		return velocities.getOrDefault(p.getUUID(), Vec3.ZERO);
	}

	/** The prey around it right now. */
	public List<LivingEntity> preys() {
		return preys;
	}

	/**
	 * Where its eyes and ears are. The skull is carried well forward of the body on its long neck:
	 * high when it stands, low when it is on all fours, away from the surface when it clings. The
	 * neck can't reach through walls, so the head stops short of anything in the way.
	 */
	public Vec3 eye() {
		Direction surface = rauk.surface();
		if (surface != Direction.DOWN) {
			Vec3 c = rauk.getBoundingBox().getCenter();
			return c.add(-surface.getStepX() * 0.6, -surface.getStepY() * 0.6, -surface.getStepZ() * 0.6);
		}
		RaukPosture posture = rauk.posture();
		double height = switch (posture) {
			case UPRIGHT -> rauk.risen() ? 7.3 : 4.0;
			case QUADRUPED -> 2.4;
			case CRAWL -> 1.2;
			case SQUEEZE -> 1.0;
			case CLING -> 1.2;
		};
		double reach = switch (posture) {
			case UPRIGHT -> rauk.risen() ? 1.6 : 3.4;
			case QUADRUPED -> 3.2;
			case CRAWL -> 3.4;
			case SQUEEZE -> 2.0;
			case CLING -> 1.0;
		};
		Vec3 base = rauk.position().add(0, Math.min(height, 1.0), 0);
		Vec3 high = rauk.position().add(0, height, 0);
		Vec3 ahead = high.add(Vec3.directionFromRotation(0, rauk.getYHeadRot()).scale(reach));
		var hit = rauk.level().clip(new ClipContext(base, ahead, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, rauk));
		if (hit.getType() == HitResult.Type.MISS) {
			return ahead;
		}
		Vec3 back = ahead.subtract(base).normalize().scale(0.3);
		return hit.getLocation().subtract(back);
	}

	// --- hearing ------------------------------------------------------------------------------

	/** A game event within earshot: works out whether it carries this far and what it means. */
	public boolean hear(ServerLevel level, Holder<GameEvent> event, GameEvent.Context context, Vec3 at) {
		Entity source = context.sourceEntity();
		if (source == rauk) {
			return false;
		}
		if (source != null && source.getType().is(net.minecraft.tags.EntityTypeTags.UNDEAD)) {
			// The undead aren't food: it doesn't listen to them.
			return false;
		}
		if (source != null && event.is(GameEvent.ENTITY_DIE.key())) {
			// It heard its prey die: there is no one there to find any more.
			beliefs.remove(source.getUUID());
		}
		double range = range(event, source, context.affectedState());
		if (range <= 0) {
			return false;
		}
		Vec3 ear = eye();
		if (at.distanceTo(ear) < 2.5) {
			// Right at its face: the prey it is holding, or killing. Nothing to go and look for.
			return false;
		}
		boolean muffled = !listening && muffled(level, at, ear);
		if (muffled) {
			range *= 0.5;
		}
		double d = ear.distanceTo(at);
		if (DEBUG) {
			Rauk.LOGGER.info("hear {} from {} at {} range {} muffled {} distance {} -> {}", event.unwrapKey().map(k -> k.location().getPath()).orElse("?"),
					source == null ? "nothing" : source.getName().getString(), at, range, muffled, String.format("%.1f", d), d <= range);
		}
		if (d > range) {
			return false;
		}
		double loud = 1 - d / range;
		long now = now();
		String label = event.unwrapKey().map(k -> k.location().getPath()).orElse("noise");
		LivingEntity player = source instanceof LivingEntity l && Prey.is(l) ? l : null;
		noises.addLast(new Noise(at, label, loud, now, player == null ? null : player.getUUID()));
		if (noises.size() > 24) {
			noises.removeFirst();
		}

		if (player != null) {
			Belief b = belief(player.getUUID());
			double accuracy = 1 + d * 0.12 + (muffled ? 2 : 0);
			b.observe(player.position(), null, accuracy, 0.3 + 0.45 * loud, Belief.Sense.HEARING, now);
			profile(player.getUUID()).foundAt(player.blockPosition(), level.getMaxLocalRawBrightness(player.blockPosition()));
		}

		// Startle tricks wear off: the bell and the goat horn startle it the first time or two.
		String trick = trick(event, context.affectedState());
		boolean startle = trick != null && (player == null || profile(player.getUUID()).startledBy(trick));
		if (startle || (loud > 0.75 && rauk.tension() < 0.5 && (player == null || !sees(player.getUUID())))) {
			rauk.startle(at);
			lastNotice = now;
		} else if (loud > 0.15 && now - lastNotice > 10) {
			rauk.notice(at);
			lastNotice = now;
		}
		return true;
	}

	/**
	 * Something the whole building hears (the generator starting, puzzle 2): wherever it is in the
	 * hotel, it knows where that came from, and goes to see.
	 */
	public void hearFar(Vec3 at, String label) {
		long now = now();
		noises.addLast(new Noise(at, label, 1, now, null));
		if (noises.size() > 24) {
			noises.removeFirst();
		}
		rauk.notice(at);
		lastNotice = now;
	}

	/** How far a sound carries in open air, by what made it. Zero for sounds it ignores. */
	private static double range(Holder<GameEvent> event, @Nullable Entity source, @Nullable BlockState state) {
		if (event.is(GameEvent.STEP.key())) {
			if (source instanceof Player p) {
				return p.isSprinting() ? 24 : p.isDiscrete() || p.isSteppingCarefully() ? 0 : 12;
			}
			return source != null && Prey.is(source) ? 12 : 0;
		}
		if (event.is(GameEvent.BLOCK_DESTROY.key()) || event.is(GameEvent.ENTITY_DAMAGE.key())
				|| event.is(GameEvent.ENTITY_DIE.key())) {
			return 32;
		}
		if (event.is(GameEvent.EXPLODE.key()) || event.is(GameEvent.INSTRUMENT_PLAY.key())) {
			return 64;
		}
		if (event.is(GameEvent.BLOCK_OPEN.key()) || event.is(GameEvent.BLOCK_CLOSE.key())
				|| event.is(GameEvent.PROJECTILE_SHOOT.key()) || event.is(GameEvent.NOTE_BLOCK_PLAY.key())
				|| event.is(GameEvent.JUKEBOX_PLAY.key())) {
			return 24;
		}
		if (event.is(GameEvent.BLOCK_CHANGE.key())) {
			return state != null && state.getBlock() instanceof BellBlock ? 48 : 10;
		}
		if (event.is(GameEvent.PROJECTILE_LAND.key())) {
			return 20;
		}
		if (event.is(GameEvent.HIT_GROUND.key()) || event.is(GameEvent.SPLASH.key()) || event.is(GameEvent.BLOCK_PLACE.key())
				|| event.is(GameEvent.CONTAINER_OPEN.key()) || event.is(GameEvent.CONTAINER_CLOSE.key())
				|| event.is(GameEvent.PRIME_FUSE.key())) {
			return source instanceof Player || source instanceof Projectile || source == null ? 16 : 0;
		}
		if (event.is(GameEvent.SWIM.key()) || event.is(GameEvent.BLOCK_ACTIVATE.key())
				|| event.is(GameEvent.BLOCK_DEACTIVATE.key())) {
			return 14;
		}
		if (event.is(GameEvent.EAT.key()) || event.is(GameEvent.DRINK.key()) || event.is(GameEvent.FLUID_PLACE.key())
				|| event.is(GameEvent.FLUID_PICKUP.key())) {
			return 10;
		}
		if (event.is(GameEvent.ENTITY_INTERACT.key()) || event.is(GameEvent.SHEAR.key())) {
			return 8;
		}
		if (event.is(GameEvent.ITEM_INTERACT_START.key()) || event.is(GameEvent.EQUIP.key())) {
			return source instanceof Player ? 5 : 0;
		}
		return 0;
	}

	@Nullable
	private static String trick(Holder<GameEvent> event, @Nullable BlockState state) {
		if (event.is(GameEvent.INSTRUMENT_PLAY.key())) {
			return "horn";
		}
		if (event.is(GameEvent.BLOCK_CHANGE.key()) && state != null && state.getBlock() instanceof BellBlock) {
			return "bell";
		}
		return null;
	}

	/** Whether solid blocks (not doors) stand between the sound and its ears. */
	private boolean muffled(ServerLevel level, Vec3 from, Vec3 to) {
		var hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, rauk));
		if (hit.getType() == HitResult.Type.MISS) {
			return false;
		}
		BlockState state = level.getBlockState(hit.getBlockPos());
		if (RaukPathfinder.openable(state)) {
			// A door: try again from just past it.
			Vec3 dir = to.subtract(from).normalize();
			Vec3 past = hit.getLocation().add(dir.scale(1.0));
			return past.distanceTo(to) > 1 && from.distanceTo(past) < from.distanceTo(to) && muffled(level, past, to);
		}
		return true;
	}

	/** Anyone moving within range gives itself away: used while tapping and while listening hard. */
	private void pollMovement(ServerLevel level, double range, double sureness, String label) {
		Vec3 ear = eye();
		for (LivingEntity p : preys) {
			if (p.position().distanceTo(ear) > range) {
				continue;
			}
			Vec3 v = velocity(p);
			if (v.lengthSqr() > 0.004 * 0.004) {
				long now = now();
				belief(p.getUUID()).observe(p.position(), v.scale(20), 1.0, sureness, Belief.Sense.HEARING, now);
				if (now - lastNotice > 10) {
					rauk.notice(p.position());
					lastNotice = now;
				}
				noises.addLast(new Noise(p.position(), label, 1, now, p.getUUID()));
				tapHeard = true;
			}
		}
	}

	// --- vision -------------------------------------------------------------------------------

	private void look(ServerLevel level, long now) {
		Vec3 eye = eye();
		// Its eyes scan level even with its head down (feeding, sniffing): the cone doesn't tip far.
		Vec3 facing = Vec3.directionFromRotation(Mth.clamp(rauk.getXRot(), -35, 35), rauk.getYHeadRot());
		for (LivingEntity p : preys) {
			Vec3 target = p.getEyePosition();
			double d = target.distanceTo(eye);
			// A flashlight's beam in its face shows where it comes from a long way off.
			boolean beamed = FlashlightItem.pointedAt(p, eye);
			if (d > (beamed ? 64 : 48) || d < 0.01) {
				continue;
			}
			double cos = facing.dot(target.subtract(eye).scale(1 / d));
			boolean central = cos > Math.cos(Math.toRadians(55));
			if (cos < Math.cos(Math.toRadians(110)) && d > 3) {
				continue;
			}
			int light = level.getMaxLocalRawBrightness(BlockPos.containing(target));
			double bright = Mth.clamp((light - 4) / 8.0, 0, 1);
			Vec3 v = velocity(p);
			boolean moving = v.horizontalDistance() > 0.04 || Math.abs(v.y) > 0.1;
			// Movement it sees a long way off; standing still only hides you at a distance, never
			// right in front of it.
			double range = moving ? Mth.lerp(bright, 40, 32) : Mth.lerp(bright, 16, 12);
			if (light < 8 && carriesLight(p)) {
				range = Math.max(range, 48);
			}
			if (!central) {
				range = moving ? range * 0.6 : 5;
			}
			if (beamed) {
				range = 64;
			}
			if (p.hasEffect(MobEffects.INVISIBILITY)) {
				range = Math.min(range, 2.5);
			}
			if (DEBUG && rauk.tickCount % 20 == 0 && d < 24) {
				var hit = level.clip(new ClipContext(eye, p.getEyePosition(), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, rauk));
				Rauk.LOGGER.info("sight: {} at {} cos {} range {} light {} moving {} in sight {} (eye {}, ray stops at {} {})", p.getName().getString(),
						String.format("%.1f", d), String.format("%.2f", cos), String.format("%.1f", range), light, moving, inSight(level, eye, p), eye,
						hit.getBlockPos().toShortString(), level.getBlockState(hit.getBlockPos()));
			}
			if (d > range || !inSight(level, eye, p)) {
				continue;
			}
			Belief b = belief(p.getUUID());
			boolean wasLost = b.secondsSince(now) > 3 || b.confidence < 0.3;
			b.observe(p.position(), v.scale(20), central ? 0.3 : 1.5, central ? 1.0 : 0.6, Belief.Sense.SIGHT, now);
			profile(p.getUUID()).foundAt(p.blockPosition(), light);
			if (wasLost && now - lastNotice > 10) {
				rauk.notice(target);
				lastNotice = now;
			}
		}
	}

	/** Eye to eye, or eye to chest: through glass and leaves, but not walls. */
	private boolean inSight(ServerLevel level, Vec3 eye, LivingEntity p) {
		for (Vec3 to : new Vec3[] {p.getEyePosition(), p.position().add(0, 0.7, 0)}) {
			var hit = level.clip(new ClipContext(eye, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, rauk));
			if (hit.getType() == HitResult.Type.MISS) {
				return true;
			}
		}
		return false;
	}

	public static boolean carriesLight(LivingEntity p) {
		if (FlashlightItem.carriesLit(p)) {
			return true;
		}
		for (ItemStack stack : new ItemStack[] {p.getMainHandItem(), p.getOffhandItem()}) {
			if (stack.getItem() instanceof BlockItem item && item.getBlock().defaultBlockState().getLightEmission() > 0) {
				return true;
			}
		}
		return false;
	}

	/** Whether it can see this player right now. */
	public boolean sees(UUID player) {
		Belief b = beliefs.get(player);
		return b != null && b.lastSeen != Long.MIN_VALUE && now() - b.lastSeen < 7;
	}

	// --- smell --------------------------------------------------------------------------------

	private void smell(ServerLevel level, long now) {
		Vec3 eye = eye();
		Vec3 nose = new Vec3(eye.x, rauk.getY() + 0.3, eye.z);
		ScentData data = ScentData.get(level);
		ScentData.Hit hit = data.smell(nose, 2.4, now);
		if (hit == null) {
			hit = data.smell(rauk.position().add(0, 0.3, 0), 2.0, now);
		}
		scent = hit;
		if (hit != null) {
			profile(hit.player()).trailThrough(BlockPos.containing(hit.position()));
		}

		for (LivingEntity p : preys) {
			double d = p.position().distanceTo(eye);
			// Blood: a badly hurt player carries a long way.
			if (p.getHealth() < p.getMaxHealth() * 0.5f && d < 32) {
				Belief b = belief(p.getUUID());
				if (b.confidence < 0.45) {
					b.observe(p.position(), null, 2 + d * 0.2, 0.25, Belief.Sense.SMELL, now);
				}
			}
			// Through a closed door: it smells prey within 3 blocks on the other side.
			if (d < 6) {
				BlockPos door = closedDoorBetween(level, eye, p.position().add(0, 0.5, 0));
				if (door != null && p.position().distanceTo(Vec3.atCenterOf(door)) < 3.5
						&& eye.distanceTo(Vec3.atCenterOf(door)) < 3) {
					belief(p.getUUID()).observe(p.position(), null, 2.0, 0.5, Belief.Sense.SMELL, now);
				}
			}
		}
	}

	@Nullable
	private BlockPos closedDoorBetween(ServerLevel level, Vec3 from, Vec3 to) {
		var hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, rauk));
		if (hit.getType() == HitResult.Type.MISS) {
			return null;
		}
		BlockState state = level.getBlockState(hit.getBlockPos());
		return state.getBlock() instanceof DoorBlock || state.is(BlockTags.TRAPDOORS) ? hit.getBlockPos() : null;
	}

	/** The scent it is smelling right now, if any. */
	@Nullable
	public ScentData.Hit scent() {
		return scent;
	}

	// --- tap and listen -----------------------------------------------------------------------

	/**
	 * Knocks on a block twice, slowly, then goes still and listens: anything that moves within 16
	 * blocks in the next two seconds gives itself away.
	 */
	public void tap(BlockPos block, Vec3 at) {
		action = ACTION_TAP;
		actionTick = 0;
		actionAt = at;
		tapBlock = block.immutable();
		tapHeard = false;
		rauk.setAction(ACTION_TAP, at);
	}

	public boolean busy() {
		return action != ACTION_NONE;
	}

	/** Whether its last tap-and-listen gave anyone away. */
	public boolean tapHeard() {
		return tapHeard;
	}

	private void tickAction(ServerLevel level, long now) {
		if (action == ACTION_NONE) {
			return;
		}
		actionTick++;
		if (action == ACTION_TAP) {
			if (actionTick % TAP_GAP == 4 && actionTick / TAP_GAP < TAPS) {
				// The knock is the client's to play, at the knuckles, by what it knocks on.
				RaukSounds.event(rauk, RaukSounds.EV_KNOCK);
			}
			if (actionTick >= TAPS * TAP_GAP) {
				action = ACTION_LISTEN;
				actionTick = 0;
				rauk.setAction(ACTION_LISTEN, actionAt);
			}
		} else if (action == ACTION_LISTEN) {
			pollMovement(level, 16, 0.9, "tap: moved");
			if (actionTick >= LISTEN_TICKS) {
				action = ACTION_NONE;
				rauk.setAction(ACTION_NONE, actionAt);
			}
		}
	}

	public void setListening(boolean listening) {
		this.listening = listening;
	}

	public boolean listening() {
		return listening;
	}

	// --- beliefs, noises and profiles ---------------------------------------------------------

	public Map<UUID, Belief> beliefs() {
		return beliefs;
	}

	public Belief belief(UUID player) {
		return beliefs.computeIfAbsent(player, Belief::new);
	}

	public PlayerProfile profile(UUID player) {
		return profiles.computeIfAbsent(player, PlayerProfile::new);
	}

	/** The prey it is most sure about, or null if it knows of none. */
	@Nullable
	public Belief focus() {
		Belief best = null;
		for (Belief b : beliefs.values()) {
			if (b.confidence > 0.05 && (best == null || b.confidence > best.confidence)) {
				best = b;
			}
		}
		return best;
	}

	/** The latest noise it couldn't put to anyone, if recent. */
	@Nullable
	public Noise lastStrangeNoise(double withinSeconds) {
		long now = now();
		Noise last = null;
		for (Noise n : noises) {
			if (n.source() == null && now - n.time() <= withinSeconds * 20) {
				last = n;
			}
		}
		return last;
	}

	/** Forgets everything it believes and has heard (beliefs, noises, scent), keeping what it has learned. */
	public void forget() {
		beliefs.clear();
		noises.clear();
		scent = null;
		territory.anomalies.clear();
	}

	/** It was hurt here. */
	public void dangerAt(Vec3 pos, double amount) {
		for (double[] d : danger) {
			if (new Vec3(d[0], d[1], d[2]).distanceTo(pos) < 3) {
				d[3] += amount;
				return;
			}
		}
		danger.add(new double[] {pos.x, pos.y, pos.z, amount});
		if (danger.size() > 32) {
			danger.remove(0);
		}
	}

	/** How much it fears a place: the sum of the hurts near it, by distance. */
	public double danger(Vec3 pos) {
		double total = 0;
		for (double[] d : danger) {
			double dist = new Vec3(d[0], d[1], d[2]).distanceTo(pos);
			if (dist < 5) {
				total += d[3] * (1 - dist / 5);
			}
		}
		return total;
	}

	public void forgetNoisesNear(Vec3 pos, double radius) {
		noises.removeIf(n -> n.pos().distanceTo(pos) < radius);
	}

	// --- debug --------------------------------------------------------------------------------

	public RaukDebugPayload snapshot(ServerLevel level) {
		long now = now();
		List<RaukDebugPayload.Guess> guesses = new ArrayList<>();
		for (Belief b : beliefs.values()) {
			if (b.confidence < 0.02) {
				continue;
			}
			Entity p = level.getEntity(b.prey);
			String name = p == null ? b.prey.toString().substring(0, 8) : p.getName().getString();
			guesses.add(new RaukDebugPayload.Guess(name, b.position, (float) b.uncertainty, (float) b.confidence,
					b.lastSense.name().toLowerCase(), (float) b.secondsSince(now), sees(b.prey)));
		}
		List<RaukDebugPayload.Noise> heard = new ArrayList<>();
		for (Noise n : noises) {
			heard.add(new RaukDebugPayload.Noise(n.pos(), n.label(), (float) n.loudness(), (now - n.time()) / 20f));
		}
		List<RaukDebugPayload.Scent> trail = new ArrayList<>();
		ScentData data = ScentData.get(level);
		for (Map.Entry<UUID, ScentData.Trail> e : data.trails().entrySet()) {
			ScentData.Trail t = e.getValue();
			int added = 0;
			for (long i = t.count() - 1; t.has(i) && added < 300; i--) {
				long age = now - t.time(i);
				if (age > ScentData.LIFETIME) {
					break;
				}
				Vec3 p = t.point(i);
				if (p.distanceTo(rauk.position()) < 32) {
					boolean smelled = scent != null && scent.player().equals(e.getKey()) && scent.index() == i;
					trail.add(new RaukDebugPayload.Scent(t.strength(i) == 0 ? p.add(0, -10, 0) : p, age / 1200f, smelled));
					added++;
				}
			}
		}
		List<RaukDebugPayload.Mark> marks = new ArrayList<>();
		if (tacticSpot != null) {
			marks.add(new RaukDebugPayload.Mark(BlockPos.containing(tacticSpot), tacticLabel));
		}
		for (Territory.Anomaly a : territory.anomalies) {
			marks.add(new RaukDebugPayload.Mark(a.pos(), a.kind().name().toLowerCase().replace('_', ' ')));
		}
		for (Map.Entry<UUID, PlayerProfile> e : profiles.entrySet()) {
			BlockPos room = e.getValue().room();
			if (room != null) {
				marks.add(new RaukDebugPayload.Mark(room, "their room"));
			}
		}
		// The doorways its way goes through, and the warmest spaces on the heat map.
		for (Vec3 d : rauk.mover().doorways()) {
			marks.add(new RaukDebugPayload.Mark(BlockPos.containing(d), "doorway"));
		}
		HotelFrame frame = HotelData.get(level).frame();
		if (frame != null) {
			var plan = HotelBuilder.plan();
			for (double[] w : HotelHeat.get(level).warmest(null, now, 4)) {
				var a = plan.areas.get((int) w[0]);
				marks.add(new RaukDebugPayload.Mark(frame.toWorld((int) Math.floor(a.centerX()), a.y0() + 1, (int) Math.floor(a.centerZ())),
						"warm: " + a.name() + " (" + Math.round(w[1]) + ")"));
			}
		}
		List<Vec3> path = rauk.mover().pathPoints();
		String state = status + (listening ? " [listening]" : "") + (action == ACTION_TAP ? " [tapping]" : "")
				+ (action == ACTION_LISTEN ? " [listening after tap]" : "") + " | knows " + territory.knownCount() + " things";
		return new RaukDebugPayload(rauk.getId(), eye(), state, guesses, heard, trail, marks, path);
	}

	// --- saving ---------------------------------------------------------------------------------

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		ListTag list = new ListTag();
		for (Belief b : beliefs.values()) {
			list.add(b.save());
		}
		tag.put("beliefs", list);
		ListTag profileList = new ListTag();
		for (PlayerProfile p : profiles.values()) {
			profileList.add(p.save());
		}
		tag.put("profiles", profileList);
		tag.put("territory", territory.save());
		return tag;
	}

	public void load(CompoundTag tag) {
		beliefs.clear();
		profiles.clear();
		for (Tag t : tag.getList("beliefs", Tag.TAG_COMPOUND)) {
			Belief b = Belief.load((CompoundTag) t);
			beliefs.put(b.prey, b);
		}
		for (Tag t : tag.getList("profiles", Tag.TAG_COMPOUND)) {
			PlayerProfile p = PlayerProfile.load((CompoundTag) t);
			profiles.put(p.player, p);
		}
		territory.load(tag.getCompound("territory"));
	}
}
