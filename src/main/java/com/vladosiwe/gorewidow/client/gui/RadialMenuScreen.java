package com.vladosiwe.gorewidow.client.gui;

import com.vladosiwe.gorewidow.entity.AggressionMode;
import com.vladosiwe.gorewidow.entity.GorewidowCommand;
import com.vladosiwe.gorewidow.entity.GorewidowEntity;
import com.vladosiwe.gorewidow.network.CreatureActionPayload;
import com.vladosiwe.gorewidow.network.CreatureActionPayload.Action;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/** The option wheel for a tamed Gorewidow: point at an option and click (or press its number). */
public class RadialMenuScreen extends Screen {
    private record Option(Action action, Component label, boolean current) {}

    private static final int RADIUS = 92;
    private final GorewidowEntity widow;
    private final List<Option> options = new ArrayList<>();
    private int hovered = -1;

    public RadialMenuScreen(GorewidowEntity widow) {
        super(Component.translatable("gui.gorewidow.wheel"));
        this.widow = widow;
    }

    @Override
    protected void init() {
        this.options.clear();
        this.options.add(new Option(Action.RIDE, Component.translatable("gui.gorewidow.ride"), false));
        this.options.add(new Option(Action.INVENTORY, Component.translatable("gui.gorewidow.inventory"), false));
        GorewidowCommand command = this.widow.getCommand();
        this.options.add(new Option(Action.FOLLOW, GorewidowCommand.FOLLOW.label(), command == GorewidowCommand.FOLLOW));
        this.options.add(new Option(Action.STAY, GorewidowCommand.STAY.label(), command == GorewidowCommand.STAY));
        this.options.add(new Option(Action.WANDER, GorewidowCommand.WANDER.label(), command == GorewidowCommand.WANDER));
        AggressionMode mode = this.widow.getAggression();
        this.options.add(new Option(Action.AGGRESSIVE, AggressionMode.AGGRESSIVE.label(), mode == AggressionMode.AGGRESSIVE));
        this.options.add(new Option(Action.NEUTRAL, AggressionMode.NEUTRAL.label(), mode == AggressionMode.NEUTRAL));
        this.options.add(new Option(Action.PASSIVE, AggressionMode.PASSIVE.label(), mode == AggressionMode.PASSIVE));
        boolean cuddle = this.widow.getCareRequest() == com.vladosiwe.gorewidow.entity.GorewidowNursery.Care.CUDDLE;
        this.options.add(new Option(Action.PET, Component.translatable(cuddle ? "gui.gorewidow.cuddle" : "gui.gorewidow.pet"), cuddle));
        if (!this.widow.isBaby()) {
            this.options.add(new Option(Action.TOGGLE_MATING, Component.translatable("gui.gorewidow.mating"), this.widow.isMatingEnabled()));
        }
        this.options.add(new Option(Action.FOLLOW_DISTANCE, Component.translatable("gui.gorewidow.follow_distance", this.widow.getFollowDistance().label()), false));
        this.options.add(new Option(Action.TOGGLE_RIDERS, Component.translatable("gui.gorewidow.allow_riders"), this.widow.allowsOtherRiders()));
        if (this.widow.hasHarness()) {
            this.options.add(new Option(Action.DROP_HARNESS, Component.translatable("gui.gorewidow.unharness"), false));
        }
    }

    private float angleOf(int index) {
        return (float) (-Math.PI / 2 + index * Mth.TWO_PI / this.options.size());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int cx = this.width / 2;
        int cy = this.height / 2;
        double dx = mouseX - cx;
        double dy = mouseY - cy;
        this.hovered = -1;
        if (dx * dx + dy * dy > 26 * 26) {
            double a = Math.atan2(dy, dx) + Math.PI / 2;
            double step = Mth.TWO_PI / this.options.size();
            this.hovered = Math.floorMod((int) Math.round(a / step), this.options.size());
        }
        // ring
        for (int i = 0; i < 180; i++) {
            double a = i * Mth.TWO_PI / 180;
            int px = cx + (int) Math.round(Math.cos(a) * (RADIUS - 2));
            int py = cy + (int) Math.round(Math.sin(a) * (RADIUS - 2));
            g.fill(px, py, px + 2, py + 2, 0x908E1E28);
        }
        if (this.hovered >= 0) {
            float a = this.angleOf(this.hovered);
            for (int r = 26; r < RADIUS - 14; r += 3) {
                int px = cx + Math.round(Mth.cos(a) * r);
                int py = cy + Math.round(Mth.sin(a) * r);
                g.fill(px - 1, py - 1, px + 2, py + 2, 0xC0D8403A);
            }
        }
        for (int i = 0; i < this.options.size(); i++) {
            Option o = this.options.get(i);
            float a = this.angleOf(i);
            // labels sit on a wide ellipse so neighbours near the top and bottom do not overlap
            int ox = cx + Math.round(Mth.cos(a) * RADIUS * 1.5F);
            int oy = cy + Math.round(Mth.sin(a) * RADIUS);
            String text = (i < 10 ? (i + 1) % 10 + " " : "") + o.label().getString();
            int w = this.font.width(text) + 8;
            int x0 = ox - w / 2;
            int y0 = oy - 7;
            boolean hot = i == this.hovered;
            g.fill(x0, y0, x0 + w, y0 + 14, hot ? 0xE07A1A22 : UiDraw.PANEL);
            g.fill(x0, y0, x0 + w, y0 + 1, o.current() ? UiDraw.XP : UiDraw.TRIM);
            g.fill(x0, y0 + 13, x0 + w, y0 + 14, o.current() ? UiDraw.XP : UiDraw.TRIM);
            g.drawString(this.font, text, x0 + 4, y0 + 3, o.current() ? UiDraw.XP : UiDraw.TEXT, false);
        }
        // centre: identity and health
        UiDraw.panel(g, cx - 54, cy - 22, 108, 44);
        Component name = this.widow.getDisplayName();
        g.drawCenteredString(this.font, name, cx, cy - 17, UiDraw.TEXT);
        g.drawCenteredString(this.font, Component.translatable("gui.gorewidow.level", this.widow.getLevel()), cx, cy - 6, UiDraw.TEXT_DIM);
        UiDraw.bar(g, cx - 46, cy + 6, 92, 5, this.widow.getHealth() / this.widow.getMaxHealth(), UiDraw.HEALTH);
        UiDraw.bar(g, cx - 46, cy + 13, 92, 4, this.widow.getFood() / this.widow.getMaxFood(), UiDraw.FOOD);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0x60000000);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.hovered >= 0) {
            this.pick(this.hovered);
            return true;
        }
        this.onClose();
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        int digit = key - org.lwjgl.glfw.GLFW.GLFW_KEY_1;
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_0) {
            digit = 9;
        }
        if (digit >= 0 && digit < this.options.size() && digit < 10) {
            this.pick(digit);
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    private void pick(int index) {
        PacketDistributor.sendToServer(new CreatureActionPayload(this.widow.getId(), this.options.get(index).action()));
        this.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        if (!this.widow.isAlive() || this.minecraft == null || this.minecraft.player == null
                || this.minecraft.player.distanceTo(this.widow) > 10) {
            this.onClose();
        }
    }
}
