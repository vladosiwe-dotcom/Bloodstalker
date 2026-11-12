package com.vladosiwe.gorewidow.client.gui;

import com.vladosiwe.gorewidow.entity.GorewidowMind;
import com.vladosiwe.gorewidow.entity.GorewidowNursery;
import com.vladosiwe.gorewidow.entity.GorewidowStats;
import com.vladosiwe.gorewidow.entity.GorewidowEntity;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * In-world info like ARK's: a panel for the Gorewidow under the crosshair (level, health, taming
 * progress and effectiveness, owner, hints) and the mount's gauges while riding.
 */
public final class GorewidowHud {
    @Nullable
    private static GorewidowEntity lookedAt;

    private GorewidowHud() {}

    @Nullable
    public static GorewidowEntity lookedAt() {
        return lookedAt;
    }

    public static void setLookedAt(@Nullable GorewidowEntity widow) {
        lookedAt = widow;
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || mc.screen != null) {
            return;
        }
        Font font = mc.font;
        if (mc.player.getVehicle() instanceof GorewidowEntity mount) {
            renderMount(g, font, mount);
            return;
        }
        GorewidowEntity widow = lookedAt;
        if (widow == null || !widow.isAlive()) {
            return;
        }
        renderInfo(g, font, widow, mc.player, mc.getWindow().getGuiScaledWidth());
    }

    /** Everything is drawn at this scale: a small, unobtrusive panel. */
    private static final float SCALE = 0.75F;

    /** Rider gauges: three slim bars in the corner (weight appears only when nearly full). */
    private static void renderMount(GuiGraphics g, Font font, GorewidowEntity mount) {
        g.pose().pushPose();
        g.pose().translate(4, 4, 0);
        g.pose().scale(SCALE, SCALE, 1);
        float load = mount.getCarriedWeight() / mount.getMaxWeight();
        boolean showWeight = load > 0.8F;
        int w = 96;
        int h = 16 + 7 * (showWeight ? 4 : 3);
        UiDraw.panel(g, 0, 0, w, h);
        g.drawString(font, font.plainSubstrByWidth(mount.getDisplayName().getString(), w - 34), 4, 4, UiDraw.TEXT, false);
        String lvl = Component.translatable("gui.gorewidow.level", mount.getLevel()).getString();
        g.drawString(font, lvl, w - 4 - font.width(lvl), 4, UiDraw.TEXT_DIM, false);
        int y = 15;
        UiDraw.bar(g, 4, y, w - 8, 4, mount.getHealth() / mount.getMaxHealth(), UiDraw.HEALTH);
        UiDraw.bar(g, 4, y + 7, w - 8, 4, mount.getStamina() / mount.getMaxStamina(), UiDraw.STAMINA);
        UiDraw.bar(g, 4, y + 14, w - 8, 4, mount.getFood() / mount.getMaxFood(), UiDraw.FOOD);
        if (showWeight) {
            UiDraw.bar(g, 4, y + 21, w - 8, 4, load, mount.isEncumbered() ? UiDraw.HEALTH : UiDraw.WEIGHT);
        }
        g.pose().popPose();
    }

    /** Looked-at creature: name, level and sex, health, and one line with what matters right now. */
    private static void renderInfo(GuiGraphics g, Font font, GorewidowEntity widow, Player player, int screenW) {
        boolean tame = widow.isTame();
        float taming = widow.getTamingPercent();
        boolean taming_ = !tame && (taming > 0 || widow.getTameFeeder() != null);
        GorewidowStats st = widow.getStats();

        Component line;
        int lineColor;
        if (taming_) {
            line = Component.translatable("gui.gorewidow.short_taming", Math.round(taming), Math.round(widow.getTamingEffectiveness()));
            lineColor = UiDraw.TAMING;
        } else if (widow.isBaby() && widow.getCareRequest() != GorewidowNursery.Care.NONE) {
            line = Component.translatable("gui.gorewidow.short_wants", widow.getCareRequest().label());
            lineColor = 0xFFFFD36B;
        } else if (widow.isBaby()) {
            line = Component.translatable("gui.gorewidow.short_baby", Math.round(widow.getMaturity() * 100), Math.round(st.imprint() * 100));
            lineColor = UiDraw.XP;
        } else {
            float trust = widow.getMind().trust(player);
            line = GorewidowMind.trustLabel(trust).copy().append(" · ").append(widow.getMood().label());
            lineColor = trust <= GorewidowMind.HOSTILE ? UiDraw.HEALTH : trust < GorewidowMind.CURIOUS ? 0xFFE0B070 : UiDraw.XP;
        }

        int w = Math.max(110, font.width(line) + 10);
        int h = taming_ ? 37 : 31;
        g.pose().pushPose();
        g.pose().translate(screenW / 2.0F - w * SCALE / 2.0F, 4, 0);
        g.pose().scale(SCALE, SCALE, 1);
        UiDraw.panel(g, 0, 0, w, h);
        String sex = widow.isFemale() ? "\u2640" : "\u2642";
        String lvl = Component.translatable("gui.gorewidow.level", widow.getLevel()).getString();
        int right = font.width(sex) + font.width(lvl) + 10;
        String name = font.plainSubstrByWidth(widow.getDisplayName().getString(), w - right - 4);
        g.drawString(font, name, 4, 4, tame ? UiDraw.TEXT : 0xFFFF8A80, false);
        g.drawString(font, lvl, w - 4 - font.width(lvl), 4, UiDraw.TEXT_DIM, false);
        g.drawString(font, sex, w - 8 - font.width(lvl) - font.width(sex), 4, widow.isFemale() ? 0xFFFF9ACD : 0xFF8FC8FF, false);
        UiDraw.bar(g, 4, 14, w - 8, 4, widow.getHealth() / widow.getMaxHealth(), UiDraw.HEALTH);
        g.drawString(font, line, 4, 21, lineColor, false);
        if (taming_) {
            UiDraw.bar(g, 4, 31, w - 8, 3, taming / 100.0F, UiDraw.TAMING);
        }
        g.pose().popPose();
    }
}
