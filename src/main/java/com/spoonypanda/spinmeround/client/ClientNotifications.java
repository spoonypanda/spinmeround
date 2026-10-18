package com.spoonypanda.spinmeround.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Displays short mod notices above the action bar used by other mods. */
public final class ClientNotifications {
    private static final int DISPLAY_TICKS = 60;
    private static final int FADE_TICKS = 20;
    private static final int LINES_ABOVE_ACTION_BAR = 4;
    private static final int ACTION_BAR_TEXT_BOTTOM_OFFSET = 72;
    private static String messageText = "";
    private static int remainingTicks;
    private final Minecraft minecraft = Minecraft.getMinecraft();

    public static void show(String text) {
        messageText = text;
        remainingTicks = DISPLAY_TICKS;
    }

    @SuppressWarnings("unused") // Forge invokes this handler through its event bus.
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (minecraft.world == null || minecraft.player == null) {
            messageText = "";
            remainingTicks = 0;
        } else if (remainingTicks > 0 && !minecraft.isGamePaused()) {
            remainingTicks--;
        }
    }

    @SuppressWarnings("unused") // Forge invokes this handler through its event bus.
    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL || remainingTicks <= 0
                || minecraft.player == null || minecraft.gameSettings.hideGUI) {
            return;
        }
        int opacity = Math.min(255, (int) ((remainingTicks - event.getPartialTicks()) * 255 / FADE_TICKS));
        if (opacity <= 8) {
            return;
        }
        int x = (event.getResolution().getScaledWidth() - minecraft.fontRenderer.getStringWidth(messageText)) / 2;
        int y = Math.max(4, event.getResolution().getScaledHeight() - ACTION_BAR_TEXT_BOTTOM_OFFSET
                - LINES_ABOVE_ACTION_BAR * minecraft.fontRenderer.FONT_HEIGHT);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        minecraft.fontRenderer.drawStringWithShadow(messageText, x, y, 0xFFFFFF | (opacity << 24));
        GlStateManager.disableBlend();
    }
}
