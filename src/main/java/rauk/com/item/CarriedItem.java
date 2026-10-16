package rauk.com.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Something found in the hotel and carried to where it's needed (hotel design 4): the service
 * lift's crank, the staff keyring, the fuses, the keys. A line under its name says what it is, in
 * the hotel's voice. Dropped, it never goes away (the hotel keeps it, see {@code HotelProgress}).
 */
public class CarriedItem extends Item {
	public CarriedItem(Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
		lines.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
	}
}
