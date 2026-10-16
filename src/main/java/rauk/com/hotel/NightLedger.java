package rauk.com.hotel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import rauk.com.registry.RaukItems;

/**
 * Puzzle 1, the Night Ledger (hotel design 4.1). The night clerk kept the guest ledger going on
 * the night of the gala: the day's comings and goings in a neat hand, then after the shutters came
 * down at 23:47, four guests who "checked out", written as he heard of them, not in the order they
 * went, his hand getting worse. A sheet folded into the cover says the safe is the order they went,
 * first to last, the last figure of each room. Read the ledger on the reception desk, put the four
 * in order of their times (past midnight comes after 23:59), and dial the safe behind the desk.
 * In it: the service lift's crank, the staff keyring, and his last entry.
 *
 * <p>Each world has its own night: the guests, their rooms, their times and the order he wrote
 * them in come from the world's seed, so the combination can't be looked up.
 */
public final class NightLedger {
	/** One line of the ledger: when, which room, who, and what he wrote. */
	record Entry(String time, String room, String guest, String note) {
	}

	/** A world's ledger: the day's entries, the four of the night as written, and the safe's figures. */
	record Ledger(List<Entry> day, List<Entry> night, int[] combination) {
	}

	private static final String[] GUESTS = {"Dr. F. Lund", "Miss A. Berg", "Mr. G. Hallstrom", "Countess E. von Rosen", "Mr. T. Ahlgren",
			"Prof. S. Malm", "Miss V. Lindgren", "Capt. O. Nystrom", "Mrs. H. Ekman", "Mr. J. Sandell", "Mme. L. Duval", "Herr K. Becker"};
	private static final String[][] DAY = {{"10:15", "Dep. Mr. R. Carlisle, London. Paid in full."}, {"13:40", "Arr. Baron and Baroness Uggla."},
			{"16:05", "Arr. Mr. and Mrs. Wikander, Visby. Late train."}, {"18:30", "Dep. Mrs. P. Strand. Called away. Forgot her umbrella."}};
	/** What he wrote of each of the four, the first neatly, the last hardly at all. */
	private static final String[] NOTES = {"Checked out %s. Seen going up the round stair. Did not come down.",
			"Checked out %s. The porter found the key on the landing.", "checked out %s", "checkd out %s - i heard it"};

	private static List<String> rooms;

	private NightLedger() {
	}

	/** The guest rooms on 2 and 3, as the hotel has them. */
	private static synchronized List<String> rooms() {
		if (rooms == null) {
			List<String> out = new ArrayList<>();
			for (HotelPlan.Area a : HotelBuilder.plan().areas) {
				if (a.name().matches("Room [23]\\d\\d")) {
					out.add(a.name().substring(5));
				}
			}
			Collections.sort(out);
			rooms = out;
		}
		return rooms;
	}

	/** A world's night. */
	static Ledger of(long seed) {
		Random random = new Random(seed * 31 + 1931);
		List<String> pool = new ArrayList<>(rooms());
		Collections.shuffle(pool, random);
		List<String> guests = new ArrayList<>(List.of(GUESTS));
		Collections.shuffle(guests, random);
		// Four times after the shutters, at least one before midnight and one after.
		List<Integer> after;
		do {
			after = new ArrayList<>();
			while (after.size() < 4) {
				int m = 3 + random.nextInt(105);
				if (!after.contains(m)) {
					after.add(m);
				}
			}
			Collections.sort(after);
		} while (after.get(0) >= 13 || after.get(3) < 13);
		// The order they went: rooms and guests in time order.
		List<Entry> went = new ArrayList<>();
		int[] combination = new int[4];
		for (int i = 0; i < 4; i++) {
			String room = pool.get(i);
			combination[i] = room.charAt(room.length() - 1) - '0';
			went.add(new Entry(clock(23 * 60 + 47 + after.get(i)), room, guests.get(i), ""));
		}
		// The order he wrote them in: any but the right one or its reverse.
		List<Integer> order;
		do {
			order = new ArrayList<>(List.of(0, 1, 2, 3));
			Collections.shuffle(order, random);
		} while (order.equals(List.of(0, 1, 2, 3)) || order.equals(List.of(3, 2, 1, 0)));
		List<Entry> night = new ArrayList<>();
		for (int k = 0; k < 4; k++) {
			Entry e = went.get(order.get(k));
			night.add(new Entry(e.time(), e.room(), e.guest(), String.format(NOTES[k], e.time())));
		}
		List<Entry> day = new ArrayList<>();
		for (int k = 0; k < DAY.length; k++) {
			day.add(new Entry(DAY[k][0], pool.get(4 + k), "", DAY[k][1]));
		}
		return new Ledger(day, night, combination);
	}

	private static String clock(int minutes) {
		int m = Math.floorMod(minutes, 24 * 60);
		return String.format("%02d:%02d", m / 60, m % 60);
	}

	/** The safe's four figures in this world. */
	public static int[] combination(ServerLevel level) {
		return of(level.getSeed()).combination();
	}

	/** The ledger as it lies open on the reception desk. */
	public static ItemStack ledger(ServerLevel level) {
		Ledger l = of(level.getSeed());
		List<String> pages = new ArrayList<>();
		pages.add("Folded into the cover, a sheet in the night clerk's hand:\n\n\"If anyone finds this: the safe is the order they went, first to last."
				+ " The last figure of each room.\"\n\nE. Holm");
		pages.add("THE RAUKVIK GRAND\n\nGuest Ledger\n\nMidsummer's Eve\n23 June 1931\n\nNight clerk on duty:\nE. Holm");
		for (int i = 0; i < l.day().size(); i += 2) {
			StringBuilder page = new StringBuilder();
			for (Entry e : l.day().subList(i, Math.min(i + 2, l.day().size()))) {
				page.append(e.time()).append("   Rm ").append(e.room()).append('\n').append(e.note()).append("\n\n");
			}
			pages.add(page.toString().strip());
		}
		pages.add("19:30\nThe gala. 200 covers. Every room let.\n\n23:47\nThe shutters. All of them at once. Mr. Wennberg's orders, the porter says."
				+ " No one is to leave.\n\nI will write them as I hear.");
		for (int i = 0; i < 4; i += 2) {
			StringBuilder page = new StringBuilder();
			for (Entry e : l.night().subList(i, i + 2)) {
				page.append("Rm ").append(e.room()).append("  ").append(e.guest()).append('\n').append(e.note()).append("\n\n");
			}
			pages.add(page.toString().strip());
		}
		pages.add("01:12  -");
		return book("Guest Ledger", "E. Holm", pages);
	}

	/** What the night clerk wrote last, and left in the safe with the keys. */
	public static ItemStack lastEntry() {
		return book("The Night Clerk's Last Entry", "E. Holm", List.of(
				"Behind the desk, 01:30.\n\nI have put the porter's keys in the safe, and the crank off the service lift's winding gear. It came up that shaft."
						+ " Without the crank the car stays where it is.",
				"The porter shut the cold-room door on its hand. I heard the bone go. Then he locked himself in, and I have not heard him since."
						+ " The laundry is locked too. The keys are with this.",
				"Mr. Wennberg went up to his rooms with the lockdown key. The lift will not go up to 4 without his own key. He has not come down.",
				"If the lights ever come on again, it will be the generator, down in the boiler room. Do not trust the lights. It knows what they"
						+ " are for.\n\nE.H."));
	}

	/** The safe opened and emptied: its crank, its keyring and the clerk's last entry go to whoever opened it. */
	public static void takeFromSafe(ServerPlayer player) {
		for (ItemStack stack : new ItemStack[] {new ItemStack(RaukItems.LIFT_CRANK), new ItemStack(RaukItems.STAFF_KEYRING), lastEntry()}) {
			if (!player.getInventory().add(stack)) {
				player.drop(stack, false);
			}
		}
		HotelData.get(player.serverLevel()).setDone(HotelProgress.NIGHT_LEDGER, true);
	}

	static ItemStack book(String title, String author, List<String> pages) {
		ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);
		List<Filterable<Component>> content = new ArrayList<>();
		for (String page : pages) {
			content.add(Filterable.passThrough(Component.literal(page)));
		}
		stack.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough(title), author, 0, content, true));
		return stack;
	}
}
