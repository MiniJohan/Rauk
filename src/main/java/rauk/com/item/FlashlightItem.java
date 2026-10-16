package rauk.com.item;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import rauk.com.registry.RaukItems;

/**
 * The flashlight (hotel design 6.1 and 8, step 4d): a 1930s electric torch, nickel-plated, its
 * switch on the side. A click turns it on or off. Held lit, in either hand, it throws a beam about
 * twenty blocks ahead in a narrow cone (the client lights the blocks it falls on), it makes the
 * hotel round you look darker, and Rauk sees it from far down a dark corridor, further still with
 * the beam in its face.
 */
public class FlashlightItem extends Item {
	/** How far the beam reaches, and its half-angle in degrees. */
	public static final double RANGE = 22, HALF_ANGLE = 20;
	private static final double COS = Math.cos(Math.toRadians(HALF_ANGLE));

	public FlashlightItem(Properties properties) {
		super(properties.stacksTo(1).component(RaukItems.LIT, false));
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		boolean on = !lit(stack);
		stack.set(RaukItems.LIT, on);
		level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.PLAYERS, 0.3f,
				on ? 1.7f : 1.4f);
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
	}

	public static boolean lit(ItemStack stack) {
		return stack.getItem() instanceof FlashlightItem && Boolean.TRUE.equals(stack.get(RaukItems.LIT));
	}

	/** Whether someone is holding a lit flashlight, in either hand. */
	public static boolean carriesLit(LivingEntity e) {
		return lit(e.getMainHandItem()) || lit(e.getOffhandItem());
	}

	/** Whether someone's lit flashlight is pointed at a point: inside its cone, however far (its glare carries further than its light). */
	public static boolean pointedAt(LivingEntity holder, Vec3 point) {
		if (!carriesLit(holder)) {
			return false;
		}
		Vec3 to = point.subtract(holder.getEyePosition());
		double d = to.length();
		return d < 1 || holder.getLookAngle().dot(to.scale(1 / d)) > COS;
	}
}
