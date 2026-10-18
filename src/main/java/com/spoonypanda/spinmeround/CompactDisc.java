package com.spoonypanda.spinmeround;

import com.spoonypanda.spinmeround.playback.SongSelection;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** A reusable crank selector; its sound is supplied through ordinary item NBT. */
@Mod.EventBusSubscriber(modid = SpinMeRound.MOD_ID)
public final class CompactDisc extends Item {
    public static final String SOUND_TAG = "Sound";
    public static final String CUSTOM_SOUND_PREFIX = "sound:";
    public static final CompactDisc INSTANCE = new CompactDisc();

    private CompactDisc() {
        setRegistryName(SpinMeRound.MOD_ID, "compact_disc");
        setUnlocalizedName(SpinMeRound.MOD_ID + ".compact_disc");
        setCreativeTab(CreativeTabs.MISC);
        setMaxStackSize(1);
    }

    @SuppressWarnings("unused") // Forge invokes this handler through its event bus.
    @SubscribeEvent
    public static void register(RegistryEvent.Register<Item> event) {
        event.getRegistry().register(INSTANCE);
    }

    /** Require an explicit namespace and a sound event ID, not a file path. */
    public static String getSoundEventId(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (stack.getItem() != INSTANCE || tag == null) {
            return "";
        }
        String sound = tag.getString(SOUND_TAG);
        return sound.length() <= 256 && sound.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") ? sound : "";
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(@Nonnull ItemStack stack, @Nullable World world,
                               @Nonnull List<String> tooltip, @Nonnull ITooltipFlag flag) {
        String sound = getSoundEventId(stack);
        tooltip.add(sound.isEmpty() ? "Set the Sound NBT tag to a sound resource ID." : "Sound: " + sound);
        tooltip.add("Right-click an AE2 crank or Manual Mill to assign. Disc is not consumed.");
        double bpm = SongSelection.readBpm(stack);
        if (bpm > 0) {
            tooltip.add("BPM: " + bpm);
        }
    }
}
