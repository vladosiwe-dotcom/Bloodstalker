#!/usr/bin/env python3
"""Generates the small pixel-art textures (silk, particles, items) and the JSON resources/data.

All art here is original and procedurally drawn. Run: python3 tools/gen_assets.py
"""
import json
import math
import os
import random

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src/main/resources")
A = os.path.join(RES, "assets/gorewidow")
D = os.path.join(RES, "data")
rng = random.Random(7)


def save(img, *path):
    p = os.path.join(*path)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    img.save(p)


def write_json(obj, *path):
    p = os.path.join(*path)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def rgba(w, h):
    return np.zeros((h, w, 4), dtype=np.uint8)


# ------------------------------------------------------------------ silk strand (u across, v along)
strand = rgba(16, 32)
for y in range(32):
    for x in range(16):
        # bright core, fibres wandering around it
        core = math.exp(-((x - 7.5) / 1.6) ** 2)
        fib = 0.0
        for k in range(3):
            cx = 7.5 + 4.5 * math.sin(y * 0.39 + k * 2.1)
            fib = max(fib, math.exp(-((x - cx) / 0.7) ** 2) * 0.55)
        a = min(1.0, core + fib)
        if a > 0.05:
            shade = 225 + int(30 * core)
            strand[y, x] = (shade, shade, min(255, shade - 8), int(255 * a))
save(Image.fromarray(strand, "RGBA"), A, "textures/entity/web_strand.png")

# ------------------------------------------------------------------ web glob (radial splat)
glob = rgba(32, 32)
cx = cy = 15.5
for y in range(32):
    for x in range(32):
        dx, dy = x - cx, y - cy
        r = math.hypot(dx, dy)
        ang = math.atan2(dy, dx)
        spoke = abs(math.sin(ang * 4)) > 0.96
        ring = abs((r + ang * 1.2) % 4.0 - 2.0) < 0.45
        blob = r < 5.5
        if r < 15 and (blob or (spoke and r < 14) or (ring and r < 12)):
            a = 1.0 if blob else max(0.35, 1 - r / 15)
            s = 240 if blob else 230
            glob[y, x] = (s, s, s - 6, int(255 * a))
save(Image.fromarray(glob, "RGBA"), A, "textures/entity/web_glob.png")

# ------------------------------------------------------------------ particles
def fiber(seed):
    r = random.Random(seed)
    img = rgba(8, 8)
    x, y = r.uniform(1, 6), r.uniform(1, 6)
    ang = r.uniform(0, math.pi)
    for _ in range(14):
        xi, yi = int(round(x)), int(round(y))
        if 0 <= xi < 8 and 0 <= yi < 8:
            img[yi, xi] = (245, 245, 240, 230)
        ang += r.uniform(-0.7, 0.7)
        x += math.cos(ang) * 0.6
        y += math.sin(ang) * 0.6
    return img


def drop(size):
    img = rgba(8, 8)
    c = 3.5
    for y in range(8):
        for x in range(8):
            d = math.hypot(x - c, (y - c) * 0.85)
            if d < size:
                shade = 1.0 - d / size * 0.45
                img[y, x] = (int(170 * shade), int(12 * shade), int(20 * shade), 255)
    if size > 2:
        img[2, 2] = (230, 90, 96, 255)
    return img


def soft_dot():
    img = rgba(8, 8)
    for y in range(8):
        for x in range(8):
            d = math.hypot(x - 3.5, y - 3.5)
            a = max(0.0, 1 - d / 3.8)
            if a > 0:
                img[y, x] = (255, 60, 70, int(255 * a ** 1.3))
    return img


def burst(frame):
    img = rgba(16, 16)
    rad = 3 + frame * 2.2
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 7.5
            r = math.hypot(dx, dy)
            ang = math.atan2(dy, dx)
            spike = abs(math.sin(ang * 3 + 0.4)) ** 6
            edge = abs(r - rad) < 1.1 + spike * 1.6
            if edge and r < 8:
                a = 1 - frame * 0.22
                img[y, x] = (255, int(70 + 60 * spike), 70, int(255 * a))
    return img


fibers = []
for i in range(4):
    save(Image.fromarray(fiber(100 + i), "RGBA"), A, f"textures/particle/web_fiber_{i}.png")
    fibers.append(f"gorewidow:web_fiber_{i}")
drops = []
for i, s in enumerate((1.6, 2.3, 3.0)):
    save(Image.fromarray(drop(s), "RGBA"), A, f"textures/particle/blood_drop_{i}.png")
    drops.append(f"gorewidow:blood_drop_{i}")
save(Image.fromarray(soft_dot(), "RGBA"), A, "textures/particle/blood_stream.png")
impacts = []
for i in range(4):
    save(Image.fromarray(burst(i), "RGBA"), A, f"textures/particle/impact_{i}.png")
    impacts.append(f"gorewidow:impact_{i}")
write_json({"textures": fibers}, A, "particles/web_fiber.json")
write_json({"textures": drops}, A, "particles/blood_drop.json")
write_json({"textures": ["gorewidow:blood_stream"]}, A, "particles/blood_stream.json")
write_json({"textures": impacts}, A, "particles/impact.json")

# ------------------------------------------------------------------ item textures
OUTLINE = (24, 10, 12, 255)
vial = rgba(16, 16)
# neck and cork
for y in range(1, 4):
    for x in range(6, 10):
        vial[y, x] = (120, 84, 52, 255) if y < 3 else (190, 190, 200, 200)
for y in range(3, 6):
    vial[y, 6] = vial[y, 9] = (200, 210, 220, 220)
    for x in range(7, 9):
        vial[y, x] = (150, 10, 22, 255)
# round body
for y in range(5, 15):
    for x in range(2, 14):
        d = math.hypot((x - 7.5) / 5.6, (y - 10) / 4.8)
        if d < 1.0:
            if d > 0.86:
                vial[y, x] = (205, 214, 226, 230)
            else:
                level = 7 + int(1.5 * math.sin(x * 0.9))
                if y >= level:
                    shade = 0.75 + 0.25 * (1 - (y - level) / 9)
                    vial[y, x] = (int(180 * shade), int(14 * shade), int(26 * shade), 255)
                else:
                    vial[y, x] = (210, 220, 232, 90)
vial[8, 4] = vial[9, 4] = (255, 255, 255, 220)
vial[10, 11] = (255, 120, 120, 255)
save(Image.fromarray(vial, "RGBA"), A, "textures/item/blood_vial.png")

harness = rgba(16, 16)
LEATHER = (110, 68, 36, 255)
DARK = (72, 42, 22, 255)
IRON = (200, 200, 210, 255)
for i in range(16):
    for w in range(2):
        # two crossing straps
        y1 = i
        x1 = int(2 + i * 0.75) + w
        if 0 <= x1 < 16:
            harness[y1, x1] = LEATHER if w == 0 else DARK
        x2 = int(13 - i * 0.75) + w
        if 0 <= x2 < 16:
            harness[y1, x2] = LEATHER if w == 0 else DARK
for y in range(9, 13):
    for x in range(3, 13):
        harness[y, x] = LEATHER if (x + y) % 5 else (196, 170, 120, 255)
for y, x in ((10, 3), (10, 12), (6, 7), (6, 8)):
    harness[y, x] = IRON
    harness[y + 1, x] = (150, 150, 160, 255)
save(Image.fromarray(harness, "RGBA"), A, "textures/item/gorewidow_harness.png")

write_json({"parent": "minecraft:item/generated", "textures": {"layer0": "gorewidow:item/blood_vial"}}, A, "models/item/blood_vial.json")
write_json({"parent": "minecraft:item/generated", "textures": {"layer0": "gorewidow:item/gorewidow_harness"}}, A, "models/item/gorewidow_harness.json")
write_json({"parent": "minecraft:item/template_spawn_egg"}, A, "models/item/gorewidow_spawn_egg.json")

# ------------------------------------------------------------------ language
en = {
    "stat.gorewidow.health": "Health",
    "stat.gorewidow.stamina": "Stamina",
    "stat.gorewidow.food": "Food",
    "stat.gorewidow.weight": "Weight",
    "stat.gorewidow.melee": "Melee",
    "stat.gorewidow.speed": "Speed",
    "message.gorewidow.aggression": "Attitude: %s",
    "message.gorewidow.aggression.aggressive": "Aggressive",
    "message.gorewidow.aggression.neutral": "Neutral",
    "message.gorewidow.aggression.passive": "Passive",
    "message.gorewidow.needs_vials": "It only feeds if you carry Blood Vials.",
    "message.gorewidow.sneak_to_feed": "Sneak and stay close to let it feed on you.",
    "message.gorewidow.latched": "It latches onto you and starts to feed. Keep sneaking and stay close.",
    "message.gorewidow.drinking_you": "No Blood Vials left: it drinks YOUR blood!",
    "message.gorewidow.tamed_level": "Taming complete! Level %s (+%s bonus levels).",
    "message.gorewidow.level_up": "%s reached level %s. Spend the point in its inventory.",
    "message.gorewidow.whistle.follow": "Whistle: follow (%s)",
    "message.gorewidow.whistle.stay": "Whistle: stay (%s)",
    "message.gorewidow.whistle.attack": "Whistle: attack my target (%s)",
    "key.gorewidow.whistle_follow": "Whistle: follow me",
    "key.gorewidow.whistle_stay": "Whistle: stay",
    "key.gorewidow.whistle_attack": "Whistle: attack target",
    "gui.gorewidow.name": "Name",
    "gui.gorewidow.aggr.aggressive": "Aggro",
    "gui.gorewidow.aggr.neutral": "Neutral",
    "gui.gorewidow.aggr.passive": "Passive",
    "gui.gorewidow.name_hint": "Name...",
    "gui.gorewidow.rename": "Rename",
    "gui.gorewidow.level": "Lvl %s",
    "gui.gorewidow.level_points": "Lvl %s  (+%s to spend)",
    "gui.gorewidow.orders": "Orders / attitude",
    "gui.gorewidow.harness": "Harness",
    "gui.gorewidow.cargo": "Cargo",
    "gui.gorewidow.weight": "Weight %s / %s",
    "gui.gorewidow.encumbered": "ENCUMBERED",
    "gui.gorewidow.ride": "Ride",
    "gui.gorewidow.inventory": "Inventory",
    "gui.gorewidow.pet": "Pet",
    "gui.gorewidow.unharness": "Take harness",
    "gui.gorewidow.wheel": "Gorewidow options",
    "gui.gorewidow.wild": "Wild %s",
    "gui.gorewidow.taming": "Taming",
    "gui.gorewidow.effectiveness": "Taming effectiveness",
    "gui.gorewidow.taming_info": "Vials left: ~%s   Bonus: +%s lvl",
    "gui.gorewidow.owner": "Owner: %s",
    "gui.gorewidow.hint_owner": "[Use] ride  [Sneak+Use] options",
    "gui.gorewidow.hint_other": "Tamed by someone else",
    "gui.gorewidow.hint_feeding": "Feeding on you... keep sneaking",
    "gui.gorewidow.hint_wild": "Sneak + use with Blood Vials to let it feed",
    "entity.gorewidow.gorewidow": "Gorewidow",
    "entity.gorewidow.web_shot": "Web Shot",
    "item.gorewidow.blood_vial": "Blood Vial",
    "item.gorewidow.blood_vial.tooltip": "Offer it to a Gorewidow to calm and tame it.",
    "item.gorewidow.gorewidow_harness": "Gorewidow Harness",
    "item.gorewidow.gorewidow_spawn_egg": "Gorewidow Spawn Egg",
    "itemGroup.gorewidow": "Gorewidow",
    "effect.gorewidow.webbed": "Webbed",
    "death.attack.gorewidow.blood_drain": "%1$s was drained dry by %2$s",
    "death.attack.gorewidow.blood_drain.player": "%1$s was drained dry by %2$s",
    "key.categories.gorewidow": "Gorewidow (riding)",
    "key.gorewidow.web": "Shoot / release web",
    "key.gorewidow.bite": "Bite / drain",
    "key.gorewidow.reel_in": "Reel web in",
    "key.gorewidow.reel_out": "Pay web out",
    "message.gorewidow.taming": "Taming: %s%%",
    "message.gorewidow.tamed": "The Gorewidow accepts you as its master.",
    "message.gorewidow.busy": "It is feeding from someone else's hand.",
    "message.gorewidow.needs_harness": "Strap a Gorewidow Harness on it to ride.",
    "message.gorewidow.command": "Order: %s",
    "message.gorewidow.command.follow": "Follow",
    "message.gorewidow.command.stay": "Stay",
    "message.gorewidow.command.wander": "Wander",
    "message.gorewidow.controls": "[%s] web  [%s] bite  [%s]/[%s] reel  [%s] leap / let go",
    "subtitles.gorewidow.ambient": "Gorewidow chitters",
    "subtitles.gorewidow.hurt": "Gorewidow hurts",
    "subtitles.gorewidow.death": "Gorewidow dies",
    "subtitles.gorewidow.step": "Gorewidow skitters",
    "subtitles.gorewidow.bite": "Gorewidow bites",
    "subtitles.gorewidow.screech": "Gorewidow shrieks",
    "subtitles.gorewidow.drain": "Gorewidow feeds",
    "subtitles.gorewidow.purr": "Gorewidow purrs",
    "subtitles.gorewidow.leap": "Gorewidow leaps",
    "subtitles.gorewidow.land": "Gorewidow lands",
    "subtitles.gorewidow.web_shoot": "Web shoots",
    "subtitles.gorewidow.web_hit": "Web sticks",
    "subtitles.gorewidow.web_retract": "Web retracts",
    "subtitles.gorewidow.web_strain": "Web strains",
}
ru = {
    "stat.gorewidow.health": "Здоровье",
    "stat.gorewidow.stamina": "Выносливость",
    "stat.gorewidow.food": "Еда",
    "stat.gorewidow.weight": "Вес",
    "stat.gorewidow.melee": "Урон",
    "stat.gorewidow.speed": "Скорость",
    "message.gorewidow.aggression": "Поведение: %s",
    "message.gorewidow.aggression.aggressive": "Агрессивно",
    "message.gorewidow.aggression.neutral": "Нейтрально",
    "message.gorewidow.aggression.passive": "Пассивно",
    "message.gorewidow.needs_vials": "Она будет пить, только если у вас есть Склянки крови.",
    "message.gorewidow.sneak_to_feed": "Присядьте и стойте рядом, чтобы она пила из вас.",
    "message.gorewidow.latched": "Она вцепилась в вас и пьёт. Сидите и не отходите.",
    "message.gorewidow.drinking_you": "Склянки кончились — она пьёт ВАШУ кровь!",
    "message.gorewidow.tamed_level": "Приручение завершено! Уровень %s (+%s бонусных уровней).",
    "message.gorewidow.level_up": "%s достигла уровня %s. Распределите очко в её инвентаре.",
    "message.gorewidow.whistle.follow": "Свисток: за мной (%s)",
    "message.gorewidow.whistle.stay": "Свисток: стоять (%s)",
    "message.gorewidow.whistle.attack": "Свисток: атаковать цель (%s)",
    "key.gorewidow.whistle_follow": "Свисток: за мной",
    "key.gorewidow.whistle_stay": "Свисток: стоять",
    "key.gorewidow.whistle_attack": "Свисток: атаковать цель",
    "gui.gorewidow.name": "Имя",
    "gui.gorewidow.aggr.aggressive": "Агрессия",
    "gui.gorewidow.aggr.neutral": "Нейтрал",
    "gui.gorewidow.aggr.passive": "Пассив",
    "gui.gorewidow.name_hint": "Имя...",
    "gui.gorewidow.rename": "Переименовать",
    "gui.gorewidow.level": "Ур. %s",
    "gui.gorewidow.level_points": "Ур. %s  (+%s очков)",
    "gui.gorewidow.orders": "Приказы / поведение",
    "gui.gorewidow.harness": "Упряжь",
    "gui.gorewidow.cargo": "Груз",
    "gui.gorewidow.weight": "Вес %s / %s",
    "gui.gorewidow.encumbered": "ПЕРЕГРУЗ",
    "gui.gorewidow.ride": "Ехать",
    "gui.gorewidow.inventory": "Инвентарь",
    "gui.gorewidow.pet": "Погладить",
    "gui.gorewidow.unharness": "Снять упряжь",
    "gui.gorewidow.wheel": "Команды Кровавой вдовы",
    "gui.gorewidow.wild": "Дикая: %s",
    "gui.gorewidow.taming": "Приручение",
    "gui.gorewidow.effectiveness": "Эффективность приручения",
    "gui.gorewidow.taming_info": "Склянок: ~%s   Бонус: +%s ур.",
    "gui.gorewidow.owner": "Хозяин: %s",
    "gui.gorewidow.hint_owner": "[ПКМ] ехать  [Присед+ПКМ] команды",
    "gui.gorewidow.hint_other": "Приручена другим игроком",
    "gui.gorewidow.hint_feeding": "Пьёт из вас... не вставайте",
    "gui.gorewidow.hint_wild": "Присядьте + ПКМ со Склянками крови",
    "entity.gorewidow.gorewidow": "Кровавая вдова",
    "entity.gorewidow.web_shot": "Сгусток паутины",
    "item.gorewidow.blood_vial": "Склянка крови",
    "item.gorewidow.blood_vial.tooltip": "Предложите её Кровавой вдове, чтобы успокоить и приручить.",
    "item.gorewidow.gorewidow_harness": "Упряжь Кровавой вдовы",
    "item.gorewidow.gorewidow_spawn_egg": "Яйцо призыва Кровавой вдовы",
    "itemGroup.gorewidow": "Кровавая вдова",
    "effect.gorewidow.webbed": "Опутан паутиной",
    "death.attack.gorewidow.blood_drain": "Из %1$s выпила всю кровь %2$s",
    "death.attack.gorewidow.blood_drain.player": "Из %1$s выпила всю кровь %2$s",
    "key.categories.gorewidow": "Кровавая вдова (верхом)",
    "key.gorewidow.web": "Выстрел / отпустить паутину",
    "key.gorewidow.bite": "Укус / пить кровь",
    "key.gorewidow.reel_in": "Подтянуть паутину",
    "key.gorewidow.reel_out": "Отпустить паутину длиннее",
    "message.gorewidow.taming": "Приручение: %s%%",
    "message.gorewidow.tamed": "Кровавая вдова признала вас хозяином.",
    "message.gorewidow.busy": "Она сейчас кормится из чужих рук.",
    "message.gorewidow.needs_harness": "Чтобы ездить, наденьте на неё упряжь.",
    "message.gorewidow.command": "Приказ: %s",
    "message.gorewidow.command.follow": "Следовать",
    "message.gorewidow.command.stay": "Стоять",
    "message.gorewidow.command.wander": "Бродить",
    "message.gorewidow.controls": "[%s] паутина  [%s] укус  [%s]/[%s] трос  [%s] прыжок / отпустить",
    "subtitles.gorewidow.ambient": "Кровавая вдова стрекочет",
    "subtitles.gorewidow.hurt": "Кровавая вдова ранена",
    "subtitles.gorewidow.death": "Кровавая вдова погибает",
    "subtitles.gorewidow.step": "Кровавая вдова семенит",
    "subtitles.gorewidow.bite": "Кровавая вдова кусает",
    "subtitles.gorewidow.screech": "Кровавая вдова визжит",
    "subtitles.gorewidow.drain": "Кровавая вдова пьёт кровь",
    "subtitles.gorewidow.purr": "Кровавая вдова урчит",
    "subtitles.gorewidow.leap": "Кровавая вдова прыгает",
    "subtitles.gorewidow.land": "Кровавая вдова приземляется",
    "subtitles.gorewidow.web_shoot": "Выстрел паутиной",
    "subtitles.gorewidow.web_hit": "Паутина прилипает",
    "subtitles.gorewidow.web_retract": "Паутина втягивается",
    "subtitles.gorewidow.web_strain": "Паутина натягивается",
}
write_json(en, A, "lang/en_us.json")
write_json(ru, A, "lang/ru_ru.json")

# ------------------------------------------------------------------ data
G = os.path.join(D, "gorewidow")
write_json({"message_id": "gorewidow.blood_drain", "exhaustion": 0.1, "scaling": "when_caused_by_living_non_player"},
           G, "damage_type/blood_drain.json")
write_json({"replace": False, "values": ["gorewidow:blood_drain"]}, D, "minecraft/tags/damage_type/bypasses_armor.json")
write_json({"replace": False, "values": ["gorewidow:blood_drain"]}, D, "minecraft/tags/damage_type/no_knockback.json")

write_json({
    "type": "minecraft:entity",
    "pools": [
        {"rolls": 1, "entries": [{"type": "minecraft:item", "name": "minecraft:string", "functions": [
            {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 3, "max": 7}},
            {"function": "minecraft:enchanted_count_increase", "enchantment": "minecraft:looting", "count": {"type": "minecraft:uniform", "min": 0, "max": 2}}]}]},
        {"rolls": 1, "entries": [{"type": "minecraft:item", "name": "minecraft:spider_eye", "functions": [
            {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 3}}]}]},
        {"rolls": 1, "conditions": [{"condition": "minecraft:killed_by_player"},
                                    {"condition": "minecraft:random_chance_with_enchanted_bonus", "enchantment": "minecraft:looting",
                                     "unenchanted_chance": 0.35, "enchanted_chance": {"type": "minecraft:linear", "base": 0.45, "per_level_above_first": 0.1}}],
         "entries": [{"type": "minecraft:item", "name": "gorewidow:blood_vial"}]},
    ],
}, G, "loot_table/entities/gorewidow.json")

write_json({
    "type": "minecraft:crafting_shapeless",
    "category": "misc",
    "ingredients": [{"item": "minecraft:glass_bottle"}, {"tag": "c:foods/raw_meat"}, {"item": "minecraft:redstone"}],
    "result": {"id": "gorewidow:blood_vial", "count": 1},
}, G, "recipe/blood_vial.json")
write_json({
    "type": "minecraft:crafting_shaped",
    "category": "equipment",
    "pattern": ["LSL", "SIS", "L L"],
    "key": {"L": {"item": "minecraft:leather"}, "S": {"item": "minecraft:string"}, "I": {"item": "minecraft:iron_ingot"}},
    "result": {"id": "gorewidow:gorewidow_harness", "count": 1},
}, G, "recipe/gorewidow_harness.json")

write_json({
    "type": "neoforge:add_spawns",
    "biomes": {"type": "neoforge:or", "values": ["#minecraft:is_jungle", "#minecraft:is_forest", "#minecraft:is_taiga", "minecraft:lush_caves"]},
    "spawners": {"type": "gorewidow:gorewidow", "weight": 6, "minCount": 1, "maxCount": 1},
}, G, "neoforge/biome_modifier/gorewidow_spawns.json")

print("assets written")
