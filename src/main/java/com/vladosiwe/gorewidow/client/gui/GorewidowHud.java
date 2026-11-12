package com.vladosiwe.gorewidow.client.gui;

import com.vladosiwe.gorewidow.entity.GorewidowCare;
import com.vladosiwe.gorewidow.entity.GorewidowEntity;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
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

    private static void renderMount(GuiGraphics g, Font font, GorewidowEntity mount) {
        int w = 132;
        int h = 72;
        int x = 6;
        int y = 6;
        UiDraw.panel(g, x, y, w, h);
        g.drawString(font, mount.getDisplayName(), x + 5, y + 4, UiDraw.TEXT, false);
        String lvl = Component.translatable("gui.gorewidow.level", mount.getLevel()).getString();
        g.drawString(font, lvl, x + w - 5 - font.width(lvl), y + 4, UiDraw.TEXT_DIM, false);
        int by = y + 15;
        UiDraw.labelledBar(g, font, x + 5, by, w - 10, Component.translatable("stat.gorewidow.health").getString(),
                UiDraw.num(mount.getHealth()) + "/" + UiDraw.num(mount.getMaxHealth()), mount.getHealth() / mount.getMaxHealth(), UiDraw.HEALTH);
        UiDraw.labelledBar(g, font, x + 5, by + 14, w - 10, Component.translatable("stat.gorewidow.stamina").getString(),
                UiDraw.num(mount.getStamina()) + "/" + UiDraw.num(mount.getMaxStamina()), mount.getStamina() / mount.getMaxStamina(), UiDraw.STAMINA);
        UiDraw.labelledBar(g, font, x + 5, by + 28, w - 10, Component.translatable("stat.gorewidow.food").getString(),
                UiDraw.num(mount.getFood()) + "/" + UiDraw.num(mount.getMaxFood()), mount.getFood() / mount.getMaxFood(), UiDraw.FOOD);
        UiDraw.labelledBar(g, font, x + 5, by + 42, w - 10, Component.translatable("stat.gorewidow.weight").getString(),
                UiDraw.num(mount.getCarriedWeight()) + "/" + UiDraw.num(mount.getMaxWeight()),
                mount.getCarriedWeight() / mount.getMaxWeight(), mount.isEncumbered() ? UiDraw.HEALTH : UiDraw.WEIGHT);
    }

    private static void renderInfo(GuiGraphics g, Font font, GorewidowEntity widow, Player player, int screenW) {
        boolean tame = widow.isTame();
        boolean mine = tame && widow.isOwnedBy(player);
        float taming = widow.getTamingPercent();
        Player feeder = widow.getTameFeeder();
        boolean taming_ = !tame && (taming > 0 || feeder != null);

        Component wildHint = feeder == player ? Component.translatable("gui.gorewidow.hint_feeding")
                : Component.translatable("gui.gorewidow.hint_wild");
        int w = Math.max(190, tame ? 0 : font.width(wildHint) + 12);
        boolean baby = widow.isBaby();
        int lines = 5 + (taming_ ? 4 : 0) + (tame ? 2 : 1) + (baby ? 2 : 0);
        int h = 8 + lines * 11;
        int x = screenW / 2 - w / 2;
        int y = 6;
        UiDraw.panel(g, x, y, w, h);
        Component title = tame || widow.hasCustomName() ? widow.getDisplayName()
                : Component.translatable("gui.gorewidow.wild", widow.getDisplayName());
        g.drawString(font, title, x + 6, y + 5, tame ? UiDraw.TEXT : 0xFFFF8A80, false);
        String lvl = Component.translatable("gui.gorewidow.level", widow.getLevel()).getString();
        g.drawString(font, lvl, x + w - 6 - font.width(lvl), y + 5, UiDraw.TEXT_DIM, false);
        int cy = y + 17;
        UiDraw.labelledBar(g, font, x + 6, cy, w - 12, Component.translatable("stat.gorewidow.health").getString(),
                UiDraw.num(widow.getHealth()) + "/" + UiDraw.num(widow.getMaxHealth()), widow.getHealth() / widow.getMaxHealth(), UiDraw.HEALTH);
        cy += 16;

        // sex and lineage (and, for babies, growth, imprint and what it wants)
        com.vladosiwe.gorewidow.entity.GorewidowStats st = widow.getStats();
        Component sex = Component.translatable(widow.isFemale() ? "gui.gorewidow.female" : "gui.gorewidow.male");
        g.drawString(font, Component.translatable("gui.gorewidow.lineage", sex, st.generation(), st.mutations()), x + 6, cy,
                widow.isFemale() ? 0xFFFF9ACD : 0xFF8FC8FF, false);
        if (tame && st.imprint() > 0 && !baby) {
            String imp = Component.translatable("gui.gorewidow.imprint", Math.round(st.imprint() * 100)).getString();
            g.drawString(font, imp, x + w - 6 - font.width(imp), cy, UiDraw.XP, false);
        }
        cy += 11;
        if (baby) {
            g.drawString(font, Component.translatable("gui.gorewidow.baby", Math.round(widow.getMaturity() * 100), Math.round(st.imprint() * 100)),
                    x + 6, cy, UiDraw.XP, false);
            cy += 11;
            com.vladosiwe.gorewidow.entity.GorewidowNursery.Care care = widow.getCareRequest();
            if (care != com.vladosiwe.gorewidow.entity.GorewidowNursery.Care.NONE) {
                g.drawString(font, Component.translatable("gui.gorewidow.wants", care.label(), "!"), x + 6, cy, 0xFFFFD36B, false);
            }
            cy += 11;
        }

        // what it thinks of you, and what it is up to
        float trust = widow.getMind().trust(player);
        Component trustLine = Component.translatable(tame ? "gui.gorewidow.bond" : "gui.gorewidow.trust", Math.round(trust),
                com.vladosiwe.gorewidow.entity.GorewidowMind.trustLabel(trust));
        int trustColor = trust <= com.vladosiwe.gorewidow.entity.GorewidowMind.HOSTILE ? UiDraw.HEALTH
                : trust < com.vladosiwe.gorewidow.entity.GorewidowMind.CURIOUS ? 0xFFE0B070 : UiDraw.XP;
        g.drawString(font, trustLine, x + 6, cy, trustColor, false);
        String mood = widow.getMood().label().getString();
        g.drawString(font, mood, x + w - 6 - font.width(mood), cy, UiDraw.TEXT_DIM, false);
        cy += 11;

        if (taming_) {
            UiDraw.labelledBar(g, font, x + 6, cy, w - 12, Component.translatable("gui.gorewidow.taming").getString(),
                    String.format(java.util.Locale.ROOT, "%.1f%%", taming), taming / 100.0F, UiDraw.TAMING);
            cy += 16;
            float eff = widow.getTamingEffectiveness();
            UiDraw.labelledBar(g, font, x + 6, cy, w - 12, Component.translatable("gui.gorewidow.effectiveness").getString(),
                    String.format(java.util.Locale.ROOT, "%.1f%%", eff), eff / 100.0F, UiDraw.EFFECTIVENESS);
            cy += 16;
            int wild = widow.getStats().wildLevel();
            int remaining = (int) Math.ceil((100.0F - taming) / 100.0F * GorewidowCare.requiredVials(wild));
            g.drawString(font, Component.translatable("gui.gorewidow.taming_info", remaining,
                    GorewidowCare.bonusLevels(wild, eff)), x + 6, cy, UiDraw.TEXT_DIM, false);
            cy += 11;
        }
        if (tame) {
            LivingEntity owner = widow.getOwner();
            Component ownerName = owner != null ? owner.getDisplayName() : Component.literal("?");
            g.drawString(font, Component.translatable("gui.gorewidow.owner", ownerName), x + 6, cy, UiDraw.TEXT_DIM, false);
            String state = widow.getCommand().label().getString() + " · " + widow.getAggression().label().getString();
            g.drawString(font, state, x + w - 6 - font.width(state), cy, UiDraw.TEXT_DIM, false);
            cy += 11;
            Component hint = mine ? Component.translatable("gui.gorewidow.hint_owner") : Component.translatable("gui.gorewidow.hint_other");
            g.drawString(font, hint, x + 6, cy, 0xFF8FD18A, false);
        } else {
            g.drawString(font, wildHint, x + 6, cy, feeder == player ? UiDraw.TAMING : 0xFFE0B070, false);
        }
    }
}
