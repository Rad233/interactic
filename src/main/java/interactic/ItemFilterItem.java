package interactic;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

public class ItemFilterItem extends Item {

    public ItemFilterItem() {
        super(new Settings().group(ItemGroup.MISC).maxCount(1));
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        final var playerStack = user.getStackInHand(hand);
        if (user.isSneaking()) {
            var enabled = playerStack.getOrCreateNbt().getBoolean("Enabled");
            enabled = !enabled;
            playerStack.getOrCreateNbt().putBoolean("Enabled", enabled);
        } else {
            if (world.isClient) return TypedActionResult.success(playerStack);
            final var inv = new FilterInventory(playerStack);
            final var factory = new NamedScreenHandlerFactory() {
                @Override
                public @NotNull ScreenHandler createMenu(int syncId, PlayerInventory playerInv, PlayerEntity player) {
                    return new ItemFilterScreenHandler(syncId, playerInv, inv);
                }

                @Override
                public Text getDisplayName() {
                    return getName();
                }
            };
            user.openHandledScreen(factory);
            final var buf = PacketByteBufs.create();
            buf.writeBoolean(inv.getFilterMode());
            ServerPlayNetworking.send((ServerPlayerEntity) user, new Identifier(InteracticInit.MOD_ID, "set_filter_mode"), buf);
        }
        return TypedActionResult.success(playerStack);
    }

    public static List<Item> getItemsInFilter(ItemStack stack) {
        final var invTag = stack.getOrCreateNbt().getList("Items", NbtElement.COMPOUND_TYPE);

        return invTag.stream()
                .map(s -> Registry.ITEM.getOrEmpty(Identifier.tryParse(((NbtCompound) s).getString("id"))).orElse(null))
                .filter(Objects::nonNull)
                .toList();
    }

    public static class FilterInventory implements Inventory {

        public final ItemStack filter;
        private final DefaultedList<ItemStack> items = DefaultedList.ofSize(9, ItemStack.EMPTY);

        public FilterInventory(ItemStack filter) {
            this.filter = filter;
            Inventories.readNbt(filter.getOrCreateNbt(), items);
        }

        public void setFilterMode(boolean mode) {
            filter.getOrCreateNbt().putBoolean("BlockMode", mode);
        }

        public boolean getFilterMode() {
            return filter.getOrCreateNbt().getBoolean("BlockMode");
        }

        @Override
        public int size() {
            return 9;
        }

        @Override
        public boolean isEmpty() {
            return items.stream().allMatch(ItemStack::isEmpty);
        }

        @Override
        public ItemStack getStack(int slot) {
            return items.get(slot);
        }

        @Override
        public ItemStack removeStack(int slot, int amount) {
            var stack = items.get(slot).copy();
            items.set(slot, ItemStack.EMPTY);
            return stack;
        }

        @Override
        public ItemStack removeStack(int slot) {
            var stack = items.get(slot).copy();
            items.set(slot, ItemStack.EMPTY);
            return stack;
        }

        @Override
        public void setStack(int slot, ItemStack stack) {
            items.set(slot, stack);
        }

        @Override
        public void markDirty() {
            Inventories.writeNbt(filter.getOrCreateNbt(), items);
        }

        @Override
        public boolean canPlayerUse(PlayerEntity player) {
            return player.getInventory().contains(filter);
        }

        @Override
        public void clear() {
            for (int i = 0; i < items.size(); i++) {
                items.set(i, ItemStack.EMPTY);
            }
        }
    }
}
