package com.vladosiwe.gorewidow.client.gui;

import com.vladosiwe.gorewidow.entity.AggressionMode;
import com.vladosiwe.gorewidow.entity.GorewidowCommand;
import com.vladosiwe.gorewidow.entity.GorewidowEntity;
import com.vladosiwe.gorewidow.entity.GorewidowStats;
import com.vladosiwe.gorewidow.menu.GorewidowMenu;
import com.vladosiwe.gorewidow.network.RenamePayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The creature sheet: name, level, experience and every stat with its level-up button on the left;
 * harness and cargo slots with the weight gauge on the right; orders and aggression underneath.
 */
public class GorewidowInventoryScreen extends AbstractContainerScreen<GorewidowMenu> {
    private static final int LEFT_W = 170;
    private final List<Button> statButtons = new ArrayList<>();
    private final List<Button> commandButtons = new ArrayList<>();
    private final List<Button> aggressionButtons = new ArrayList<>();
    private EditBox nameBox;

    public GorewidowInventoryScreen(GorewidowMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 352;
        this.imageHeight = 188;
    }

    @Override
    protected void init() {
        super.init();
        GorewidowEntity widow = this.menu.getWidow();
        int x = this.leftPos;
        int y = this.topPos;
        this.nameBox = new EditBox(this.font, x + 6, y + 6, 142, 12, Component.translatable("gui.gorewidow.name"));
        this.nameBox.setMaxLength(RenamePayload.MAX_LENGTH);
        this.nameBox.setValue(widow != null && widow.hasCustomName() ? widow.getCustomName().getString() : "");
        this.nameBox.setHint(Component.translatable("gui.gorewidow.name_hint"));
        this.addRenderableWidget(this.nameBox);
        this.addRenderableWidget(Button.builder(Component.literal("✔"), b -> this.applyName())
                .bounds(x + 150, y + 6, 14, 12).tooltip(Tooltip.create(Component.translatable("gui.gorewidow.rename"))).build());

        this.statButtons.clear();
        for (int i = 0; i < GorewidowStats.STATS.length; i++) {
            int id = GorewidowMenu.BUTTON_STAT + i;
            Button b = Button.builder(Component.literal("+"), btn -> this.click(id)).bounds(x + 154, y + 54 + i * 15, 11, 11).build();
            this.statButtons.add(this.addRenderableWidget(b));
        }
        this.commandButtons.clear();
        GorewidowCommand[] commands = GorewidowCommand.values();
        for (int i = 0; i < commands.length; i++) {
            int id = GorewidowMenu.BUTTON_COMMAND + i;
            this.commandButtons.add(this.addRenderableWidget(Button.builder(commands[i].label(), btn -> this.click(id))
                    .bounds(x + 4 + i * 54, y + 155, 53, 13).build()));
        }
        this.aggressionButtons.clear();
        AggressionMode[] modes = AggressionMode.values();
        for (int i = 0; i < modes.length; i++) {
            int id = GorewidowMenu.BUTTON_AGGRESSION + i;
            this.aggressionButtons.add(this.addRenderableWidget(Button.builder(Component.translatable("gui.gorewidow.aggr." + modes[i].name().toLowerCase(java.util.Locale.ROOT)), btn -> this.click(id))
                    .bounds(x + 4 + i * 54, y + 170, 53, 13).build()));
        }
        this.addRenderableWidget(Button.builder(Component.translatable("gui.gorewidow.ride"), btn -> this.click(GorewidowMenu.BUTTON_RIDE))
                .bounds(x + 302, y + 3, 46, 13).build());
    }

    private void click(int id) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
        }
    }

    private void applyName() {
        GorewidowEntity widow = this.menu.getWidow();
        if (widow != null) {
            PacketDistributor.sendToServer(new RenamePayload(widow.getId(), this.nameBox.getValue()));
            this.nameBox.setFocused(false);
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (this.nameBox.isFocused()) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                this.applyName();
                return true;
            }
            if (key != GLFW.GLFW_KEY_ESCAPE) {
                return this.nameBox.keyPressed(key, scan, modifiers) || true;
            }
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        GorewidowEntity widow = this.menu.getWidow();
        if (widow == null) {
            return;
        }
        boolean points = widow.getStats().unspent() > 0;
        this.statButtons.forEach(b -> b.visible = points);
        GorewidowCommand command = widow.getCommand();
        for (int i = 0; i < this.commandButtons.size(); i++) {
            this.commandButtons.get(i).active = command.ordinal() != i;
        }
        AggressionMode mode = widow.getAggression();
        for (int i = 0; i < this.aggressionButtons.size(); i++) {
            this.aggressionButtons.get(i).active = mode.ordinal() != i;
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        UiDraw.panel(g, x, y, LEFT_W, this.imageHeight);
        UiDraw.panel(g, x + LEFT_W + 2, y, this.imageWidth - LEFT_W - 2, this.imageHeight);
        for (Slot slot : this.menu.slots) {
            int sx = x + slot.x - 1;
            int sy = y + slot.y - 1;
            g.fill(sx, sy, sx + 18, sy + 18, 0xFF2A2F36);
            g.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFF0B0D10);
        }
        // harness slot accent
        int hx = x + GorewidowMenu.HARNESS_X - 1;
        int hy = y + GorewidowMenu.HARNESS_Y - 1;
        g.fill(hx, hy, hx + 18, hy + 1, UiDraw.TRIM);
        g.fill(hx, hy + 17, hx + 18, hy + 18, UiDraw.TRIM);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        GorewidowEntity widow = this.menu.getWidow();
        if (widow == null) {
            return;
        }
        GorewidowStats stats = widow.getStats();
        int unspent = stats.unspent();
        Component level = unspent > 0
                ? Component.translatable("gui.gorewidow.level_points", widow.getLevel(), unspent)
                : Component.translatable("gui.gorewidow.level", widow.getLevel());
        g.drawString(this.font, level, 6, 22, unspent > 0 ? UiDraw.XP : UiDraw.TEXT, false);
        UiDraw.bar(g, 6, 32, 158, 5, stats.xp() / stats.xpToNext(), UiDraw.XP);
        Component sex = Component.translatable(widow.isFemale() ? "gui.gorewidow.female" : "gui.gorewidow.male");
        String lineage = Component.translatable("gui.gorewidow.lineage", sex, stats.generation(), stats.mutations()).getString()
                + (stats.imprint() > 0 ? "  ·  " + Component.translatable("gui.gorewidow.imprint", Math.round(stats.imprint() * 100)).getString() : "");
        g.drawString(this.font, lineage, 6, 40 - 2 + 0, widow.isFemale() ? 0xFFFF9ACD : 0xFF8FC8FF, false);

        int row = 0;
        for (GorewidowStats.Stat stat : GorewidowStats.STATS) {
            int ry = 52 + row * 15;
            String value;
            float frac;
            int color;
            switch (stat) {
                case HEALTH -> { value = UiDraw.num(widow.getHealth()) + " / " + UiDraw.num(widow.getMaxHealth()); frac = widow.getHealth() / widow.getMaxHealth(); color = UiDraw.HEALTH; }
                case STAMINA -> { value = UiDraw.num(widow.getStamina()) + " / " + UiDraw.num(widow.getMaxStamina()); frac = widow.getStamina() / widow.getMaxStamina(); color = UiDraw.STAMINA; }
                case FOOD -> { value = UiDraw.num(widow.getFood()) + " / " + UiDraw.num(widow.getMaxFood()); frac = widow.getFood() / widow.getMaxFood(); color = UiDraw.FOOD; }
                case WEIGHT -> { value = UiDraw.num(widow.getCarriedWeight()) + " / " + UiDraw.num(widow.getMaxWeight()); frac = widow.getCarriedWeight() / widow.getMaxWeight(); color = widow.isEncumbered() ? UiDraw.HEALTH : UiDraw.WEIGHT; }
                case MELEE -> { value = Math.round(stats.value(stat) / stat.base * 100) + "%"; frac = (float) ((stats.value(stat) / stat.base - 1) / 2.0); color = UiDraw.MELEE; }
                default -> { value = Math.round(stats.value(stat) / stat.base * 100) + "%"; frac = (float) ((stats.value(stat) / stat.base - 1) / 1.0); color = UiDraw.SPEED; }
            }
            String label = Component.translatable(stat.key()).getString();
            int points = stats.wildPoints(stat) + stats.tamedPoints(stat);
            g.drawString(this.font, label, 6, ry, UiDraw.TEXT_DIM, false);
            g.drawString(this.font, "·" + points, 6 + this.font.width(label) + 2, ry, 0xFF5C636B, false);
            g.drawString(this.font, value, 150 - this.font.width(value), ry, UiDraw.TEXT, false);
            UiDraw.bar(g, 6, ry + 9, 144, 3, frac, color);
            row++;
        }
        g.drawString(this.font, Component.translatable("gui.gorewidow.orders"), 6, 144, UiDraw.TEXT_DIM, false);

        int right = LEFT_W + 8;
        String harness = Component.translatable("gui.gorewidow.harness").getString();
        g.drawString(this.font, harness, GorewidowMenu.HARNESS_X + 8 - this.font.width(harness) / 2, GorewidowMenu.HARNESS_Y + 21, UiDraw.TEXT_DIM, false);
        g.drawString(this.font, Component.translatable("gui.gorewidow.cargo"), GorewidowMenu.CARGO_X, 8, UiDraw.TEXT_DIM, false);
        String weight = Component.translatable("gui.gorewidow.weight", UiDraw.num(widow.getCarriedWeight()), UiDraw.num(widow.getMaxWeight())).getString();
        g.drawString(this.font, weight, right, 80, widow.isEncumbered() ? UiDraw.HEALTH : UiDraw.TEXT_DIM, false);
        if (widow.isEncumbered()) {
            g.drawString(this.font, Component.translatable("gui.gorewidow.encumbered"), right + this.font.width(weight) + 6, 80, UiDraw.HEALTH, false);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        this.renderTooltip(g, mouseX, mouseY);
    }
}
