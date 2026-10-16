package rauk.com.hotel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.gameevent.GameEvent;
import rauk.com.block.hotel.HotelDoorBlock;

/**
 * Puzzle 4, the Collector's Shelves (hotel design 4.4). Wennberg's catalogue of the Cabinet lies
 * open on a library table: six specimens, each by its number, and a seventh with no entry. A volume
 * on each of the six lies about the library and the reading room, titled by name, not number. The
 * catalogue shelf in the east wall, by his study, holds them in their numbers, top row one to three
 * and bottom row four to six, as one reads. Shelve all six so, and the section of shelves beside it
 * swings open: his hidden study, with the first half of the release code, the key that takes the
 * service lift up to 4, and his book on No. 7.
 *
 * <p>Which specimen has which number comes from the world's seed.
 */
public final class CollectorsShelves {
	/** The six specimens: the name on its volume, its line in the catalogue, and the volume's pages. */
	record Specimen(String name, String entry, String[] pages) {
	}

	static final List<Specimen> SPECIMENS = List.of(
			new Specimen("The Visby Crane", "A crane from the old bishop's garden, stuffed with its wings open.", new String[] {
					"THE VISBY CRANE\n\nShot in the bishop's garden in 1902, the last of the cranes that nested on the town wall.",
					"I had it stuffed with its wings open, as if it were still deciding to fly. The staff say it turns its head at night. It does not."}),
			new Specimen("The Bog Woman of Lojsta", "Brown as a boot, her hands bound. Out of the peat in 1898.", new String[] {
					"THE BOG WOMAN OF LOJSTA\n\nCut out of the peat at Lojsta in 1898, two thousand years old, her hands bound in front of her.",
					"Her face is calm. Whatever they did to her, she had made her peace with it. I keep her in the dark, as she likes."}),
			new Specimen("The Two-Headed Calf", "From a farm at Hejde. The heads disagreed.", new String[] {
					"THE TWO-HEADED CALF\n\nBorn at Hejde in the spring of 1907. It lived eleven days.",
					"The farmer said the heads disagreed: one would feed, the other would not. In the end the one starved the other. Then itself."}),
			new Specimen("The Raukar Eel", "Taken under the sea stacks. Nine feet. Its teeth face backwards.", new String[] {
					"THE RAUKAR EEL\n\nTaken in a net under the sea stacks of Raukvik in 1909. Nine feet long.",
					"Its teeth face backwards, so that what it takes cannot pull free. The fishermen would not touch it. They said it came up out of"
							+ " the caves under the stacks."}),
			new Specimen("The Glass Hand", "A hand in a jar, from a ship that came in empty.", new String[] {
					"THE GLASS HAND\n\nA hand, in spirits, in a jar. From the barque Ingrid, which came into Slite in 1904 with her sails set and"
							+ " no one aboard.",
					"It was in the captain's cabin, closed tight round the jar's neck. I have not been able to open its fingers."}),
			new Specimen("The Sleeping Lynx", "Shot asleep in the forest at Faro. It never woke.", new String[] {
					"THE SLEEPING LYNX\n\nShot in the forest at Faro in 1905, asleep in the snow.",
					"It is not stuffed. It never decayed. It lies curled in its case as it was found, and it is warm to the touch."}));

	private CollectorsShelves() {
	}

	public static void init() {
		ServerTickEvents.END_WORLD_TICK.register(level -> {
			if (level.dimension() == Level.OVERWORLD && level.getGameTime() % 20 == 0) {
				check(level);
			}
		});
	}

	/** The specimens in their catalogue numbers in this world: the first is No. 1. */
	static List<Specimen> numbered(long seed) {
		List<Specimen> out = new ArrayList<>(SPECIMENS);
		Collections.shuffle(out, new Random(seed * 17 + 1911));
		return out;
	}

	/** Has someone shelved the catalogue right? Then the shelves beside it swing open. */
	private static void check(ServerLevel level) {
		HotelData data = HotelData.get(level);
		HotelFrame frame = data.frame();
		HotelPlan.Mark mark = HotelBuilder.plan().marks.get("catalogue_shelf");
		if (frame == null || mark == null || data.done(HotelProgress.SHELVES)) {
			return;
		}
		BlockPos pos = frame.toWorld(mark.x(), mark.y(), mark.z());
		if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof ChiseledBookShelfBlockEntity shelf)) {
			return;
		}
		List<Specimen> order = numbered(level.getSeed());
		for (int i = 0; i < 6; i++) {
			WrittenBookContent book = shelf.getItem(i).get(DataComponents.WRITTEN_BOOK_CONTENT);
			if (book == null || !book.title().raw().equals(order.get(i).name())) {
				return;
			}
		}
		open(level, frame, pos);
	}

	/** The catalogue complete: the section of shelves by it swings open on Wennberg's study. */
	private static void open(ServerLevel level, HotelFrame frame, BlockPos shelf) {
		HotelData.get(level).setDone(HotelProgress.SHELVES, true);
		HotelPlan.Mark door = HotelBuilder.plan().marks.get("hidden_study_door");
		if (door != null) {
			BlockPos at = frame.toWorld(door.x(), door.y(), door.z());
			HotelDoorBlock.unlock(level, at);
			HotelDoorBlock.swing(level, at, HotelDoorBlock.FULL, HotelDoorBlock.CAREFUL, null, true);
			level.playSound(null, at, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 0.8f, 0.5f);
			level.playSound(null, at, SoundEvents.WOODEN_DOOR_OPEN, SoundSource.BLOCKS, 1, 0.5f);
			level.gameEvent(null, GameEvent.BLOCK_OPEN, at);
		}
		for (Player p : level.players()) {
			if (p.distanceToSqr(shelf.getX(), shelf.getY(), shelf.getZ()) < 24 * 24) {
				p.displayClientMessage(Component.translatable("hotel.rauk.shelves.open"), true);
			}
		}
	}

	/** For each catalogue number in turn (from No. 1), the place of its specimen in the list: which volume goes there. */
	public static int[] numberedNames(long seed) {
		List<Specimen> order = numbered(seed);
		int[] out = new int[order.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = SPECIMENS.indexOf(order.get(i));
		}
		return out;
	}

	/** A volume of the catalogue, by its place in the list of specimens (not its number). */
	public static ItemStack volume(int i) {
		Specimen s = SPECIMENS.get(i);
		return NightLedger.book(s.name(), "H. Wennberg", List.of(s.pages()));
	}

	/** The catalogue itself, open on a library table. */
	public static ItemStack catalogue(ServerLevel level) {
		List<String> pages = new ArrayList<>();
		pages.add("THE CABINET\n\nA catalogue of the collection of H. Wennberg, kept below the Raukvik Grand.\n\nThe volumes stand on the catalogue"
				+ " shelf by my study in their numbers: the top row one to three, the bottom four to six, as one reads.");
		List<Specimen> order = numbered(level.getSeed());
		for (int i = 0; i < 6; i += 2) {
			pages.add("No. " + (i + 1) + "\n" + order.get(i).name() + "\n" + order.get(i).entry() + "\n\nNo. " + (i + 2) + "\n" + order.get(i + 1).name()
					+ "\n" + order.get(i + 1).entry());
		}
		pages.add("No. 7\n\nNot catalogued here. No. 7 is not a curiosity. It has its own book, and its own room.\n\nH.W.");
		return NightLedger.book("The Cabinet: A Catalogue", "H. Wennberg", pages);
	}

	/** Wennberg's own book on No. 7, on the desk in his hidden study. */
	public static ItemStack noSeven() {
		return NightLedger.book("No. 7", "H. Wennberg", List.of(
				"NO. 7\n\nBrought up out of the caves under the sea stacks by my quarrymen in the spring of 1911. They broke into a chamber the sea"
						+ " had never reached. It was asleep there.",
				"I paid the men to forget it. One would not. He is buried at Hejde.",
				"It does not age. It does not need to eat more than once a season, if it is not woken. It learns. It learned the doors in a week, the"
						+ " staff's names in a month, by their voices.",
				"It is not an animal. I do not know what it is. I built the Cabinet round it, and the hotel over the Cabinet, and the lockdown to"
						+ " keep it in if it ever woke hungry.",
				"The release is in two halves. The first is on the card in this desk. The second I keep upstairs, with the key, behind the clock."
						+ "\n\nIf you are reading this, I failed. Do not let it out with you.\n\nH.W."));
	}

	/** The card in his desk: the release code's first half. */
	public static ItemStack codeCard(ServerLevel level) {
		int[] code = LockdownCode.of(level.getSeed());
		return NightLedger.book("A Card", "H. Wennberg", List.of(
				"LOCKDOWN RELEASE\n\nFirst half:\n\n" + code[0] + "  " + code[1] + "  " + code[2] + "  " + code[3]
						+ "\n\nThe second half is with the key, behind the clock."));
	}
}
