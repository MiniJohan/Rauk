package rauk.com.hotel;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import rauk.com.block.hotel.FurnitureBlock;
import rauk.com.entity.RaukEntity;
import rauk.com.registry.RaukEntities;
import rauk.com.registry.RaukItems;

/**
 * Puzzle 3, the Last Waltz (hotel design 4.3). The bandleader's letter, in his room in the burned
 * wing on 3, tells how the band kept playing while it stood at the edge of the ballroom and
 * listened; his music is on the stand there, written out for a page-turner who couldn't read
 * music, by white keys counted from the left. The ballroom's grand piano has eight broad keys, an
 * octave from C. Play the waltz through without stopping and the drawer in the front of the
 * bandstand springs open: in it, Wennberg's study key, which he dropped running past the band.
 *
 * <p>The music carries: when someone starts to play, every Rauk in the hotel hears it and comes.
 * Arriving, if it can't see who's playing, now and then it stops at the edge of the room and stands
 * listening for as long as the music lasts, as it did for the band (the bandleader's trick, not to
 * be counted on). When the music stops, it comes on.
 */
public final class LastWaltz {
	/** The waltz, by white keys from the left (1 is C, 8 the C above). */
	static final int[] WALTZ = {3, 5, 5, 2, 4, 4, 1, 3, 5, 8, 5, 3};
	/** The keys' notes, in note-block steps from F sharp: C D E F G A B C. */
	private static final int[] NOTES = {6, 8, 10, 11, 13, 15, 17, 18};
	/** Silence long enough to lose the tune, and to count as the music having stopped, in ticks. */
	private static final int LOST = 100, STOPPED = 70;
	/** How often it stops to listen, a time each the music starts. Tests may set it. */
	public static double spell = 0.35;

	private static final Deque<Integer> played = new ArrayDeque<>();
	private static long lastNote = -100_000;
	private static Vec3 at = Vec3.ZERO;
	private static int session;

	private LastWaltz() {
	}

	/** Whether music is playing in the hotel now. */
	public static boolean playing(long now) {
		return now - lastNote < STOPPED;
	}

	/** Where it's being played. */
	public static Vec3 where() {
		return at;
	}

	/** Which time of playing this is: a new one each time it starts after a silence. */
	public static int session() {
		return session;
	}

	/** A key of the ballroom's piano struck. */
	static InteractionResult key(ServerLevel level, BlockPos pos, BlockState state, Player player, BlockHitResult hit) {
		FurnitureBlock block = (FurnitureBlock) state.getBlock();
		double across = block.across(pos, state, hit.getLocation());
		if (across < 0.5 || across > 31.5) {
			return InteractionResult.PASS;
		}
		// The keys run from the pianist's left at the piece's high end.
		int key = 8 - Math.min(7, (int) ((across - 0.5) / (31.0 / 8)));
		long now = level.getGameTime();
		float pitch = (float) Math.pow(2, (NOTES[key - 1] - 12) / 12.0);
		level.playSound(null, pos, SoundEvents.NOTE_BLOCK_HARP.value(), SoundSource.RECORDS, 2, pitch);
		Vec3 above = hit.getLocation().add(0, 0.6, 0);
		level.sendParticles(ParticleTypes.NOTE, above.x, above.y, above.z, 0, NOTES[key - 1] / 24.0, 0, 0, 1);
		level.gameEvent(player, GameEvent.NOTE_BLOCK_PLAY, pos);
		if (now - lastNote >= STOPPED) {
			// The music starts: it carries all through the building, and it comes.
			session++;
			for (RaukEntity rauk : level.getEntities(RaukEntities.RAUK, r -> true)) {
				rauk.mind().hearFar(hit.getLocation(), "music");
			}
		}
		if (now - lastNote >= LOST) {
			played.clear();
		}
		lastNote = now;
		at = hit.getLocation();
		played.addLast(key);
		while (played.size() > WALTZ.length) {
			played.removeFirst();
		}
		if (played.size() == WALTZ.length && Arrays.equals(played.stream().mapToInt(Integer::intValue).toArray(), WALTZ)) {
			played.clear();
			openDrawer(level, player);
		}
		return InteractionResult.SUCCESS;
	}

	/** The last note of the waltz: under the bandstand, a catch lets go. */
	private static void openDrawer(ServerLevel level, Player player) {
		HotelData data = HotelData.get(level);
		HotelFrame frame = data.frame();
		HotelPlan.Mark mark = HotelBuilder.plan().marks.get("bandstand_drawer");
		if (frame == null || mark == null) {
			return;
		}
		BlockPos pos = frame.toWorld(mark.x(), mark.y(), mark.z());
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof FurnitureBlock drawer) || drawer.lookOf(state).look() != 0) {
			return;
		}
		drawer.setLook(level, pos, state, 1);
		data.setDone(HotelProgress.WALTZ, true);
		level.playSound(null, pos, SoundEvents.CHISELED_BOOKSHELF_PICKUP_ENCHANTED, SoundSource.BLOCKS, 1, 0.6f);
		level.playSound(null, pos, SoundEvents.WOODEN_TRAPDOOR_OPEN, SoundSource.BLOCKS, 1, 0.8f);
		level.gameEvent(player, GameEvent.BLOCK_OPEN, pos);
		player.displayClientMessage(Component.translatable("hotel.rauk.waltz.drawer_opens"), true);
	}

	/** The drawer in the bandstand: shut, it doesn't move; open, Wennberg's key is in it. */
	static InteractionResult drawer(ServerLevel level, BlockPos pos, BlockState state, Player player) {
		FurnitureBlock drawer = (FurnitureBlock) state.getBlock();
		int look = drawer.lookOf(state).look();
		if (look == 1) {
			drawer.setLook(level, pos, state, 2);
			ItemStack key = new ItemStack(RaukItems.STUDY_KEY);
			if (!player.getInventory().add(key)) {
				player.drop(key, false);
			}
			level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 0.6f, 1.6f);
		}
		player.displayClientMessage(Component.translatable("hotel.rauk.waltz.drawer_" + look), true);
		return InteractionResult.SUCCESS;
	}

	/** The bandleader's letter, on the desk in his room. */
	public static ItemStack letter() {
		return NightLedger.book("A Letter, Unsent", "A. Lindahl", List.of(
				"Dear Margit,\n\nIf this reaches you, I am sorry for all of it. We were playing the last waltz when the screaming started out in the hall.",
				"It came into the ballroom out of the dark at the far end. The dancers ran. We did not. I could not make my hands stop. And it stopped. It"
						+ " stood at the edge of the floor, in the dark, and did not move.",
				"As long as we played, it stood there and listened. When Erik's string broke and we faltered, it moved. So we played. Three hours, the"
						+ " same waltz, round and round.",
				"Mr. Wennberg ran past the stand and dropped his key, the one to his study upstairs. I locked it in the drawer in the bandstand: his"
						+ " toy for the anniversary, which opens only to the last waltz.",
				"The music is on my stand. If anyone comes after us: play it. It will open the drawer. It will bring it, too. It does not always stop"
						+ " to listen.\n\nYour Axel"));
	}

	/** The music, on the stand in his room. */
	public static ItemStack music() {
		StringBuilder tune = new StringBuilder();
		for (int i = 0; i < WALTZ.length; i++) {
			tune.append(i % 3 == 0 ? (i == 0 ? "" : "\n") : "   ").append(WALTZ[i]);
		}
		return NightLedger.book("The Last Waltz", "A. Lindahl", List.of(
				"THE LAST WALTZ\nfor the Raukvik Grand\n\nA. Lindahl, 1931\n\nFor Edvin, who turns the pages and can't read a note: count the white"
						+ " keys from the left.",
				tune + "\n\nOnce through, without stopping."));
	}
}
