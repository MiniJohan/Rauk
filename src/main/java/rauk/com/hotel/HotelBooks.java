package rauk.com.hotel;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.LecternMenu;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

/**
 * The hotel's things to read where they lie (hotel design 5): opened as a book on the page, the
 * way a book on a lectern is, so it stays where it is for the next one to read.
 */
public final class HotelBooks {
	private HotelBooks() {
	}

	/** Opens something to read, by its name (the "read:" kind of the piece it is). */
	public static void read(ServerPlayer player, String what) {
		ItemStack book = switch (what) {
			case "night_ledger" -> NightLedger.ledger(player.serverLevel());
			case "engineers_log" -> Generator.log();
			case "bandleader_letter" -> LastWaltz.letter();
			case "last_waltz" -> LastWaltz.music();
			case "cabinet_catalogue" -> CollectorsShelves.catalogue(player.serverLevel());
			case "no_seven" -> CollectorsShelves.noSeven();
			case "code_card" -> CollectorsShelves.codeCard(player.serverLevel());
			case "wennberg_journal" -> StoppedClock.journal();
			case "wennberg_last_pages" -> StoppedClock.lastPages();
			case "code_card_2" -> StoppedClock.codeCard(player.serverLevel());
			default -> ItemStack.EMPTY;
		};
		if (book.isEmpty()) {
			return;
		}
		SimpleContainer pages = new SimpleContainer(1);
		pages.setItem(0, book);
		player.level().playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8f, 1);
		player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new LecternMenu(id, pages, new SimpleContainerData(1)), book.getHoverName()));
	}
}
