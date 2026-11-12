# Gorewidow

**Gorewidow** is a NeoForge 1.21.1 mod that adds one creature: a long-legged blood predator
that crawls on any surface, swings on web lines, reels its prey in and drains it. Tame it and you
ride it the way a climber rides a harness, strapped face-down **under** its belly.

The creature, its model, textures, animations and sounds are all original.

**Look.** A small armoured body with a fan of hooked spines on its head, slung high (about two
blocks up) between eight very long, thick legs. Each leg rises in a tall bowed arch above the body,
with pale bone caps at every joint, and comes down almost vertically to a long scythe claw that
fades to a blood-red point. The hide is black, mottled with deep crimson and cracked with veins that
glow. Glowing runes mark the swollen abdomen. Crimson skin membranes stretch inside every arch. A
rider hangs in the open space under the body.

- Minecraft 1.21.1, NeoForge 21.1.150+ (built against 21.1.266)
- GeckoLib 4.9.x (required dependency)

## In the wild

Gorewidows live in mountain lairs and, more rarely, in swamps (see below). They hunt the way
spiders do:

- **Ambush.** It webs its way up to a branch or cave ceiling, goes still, sometimes drops down on a
  thread and dangles there, waiting for prey to pass below.
- **Pounce.** From the ground or out of an ambush it leaps on its target in a ballistic arc.
- **Web pull.** It snags a distant target with a web line and reels it in. A target too heavy to
  pull gets the creature hauling itself toward it instead.
- **Web swing.** It crosses long gaps and climbs to high targets by firing a line at a high anchor,
  pumping the swing and letting go at the top of the arc.
- **Wall and ceiling crawling.** It climbs walls, crosses ceilings and wraps around edges.
- **Blood drain.** It lunges, clamps its fangs in, and wraps its front legs around the prey.
  Anything up to player size is lifted off the ground and held against its mouth while it drinks
  and heals itself.

## Mind: trust and mood

Where they live: Gorewidows make their **lairs in the mountains** (`/locate structure gorewidow:lair`).
A lair is a webbed crack in a steep slope; inside, a tunnel winds down into a deep chamber full of
bones, silk cocoons and a barrel of loot, and its Gorewidow sleeps there by day. **At night it climbs
out to hunt** across the slopes and returns at dawn. A few also roam swamps (ARK's bog), hanging in
the trees.

Every Gorewidow keeps a **trust** score for each player, from -100 to 100, and a **mood**. Both
are shown on the info panel.

- **Wild and wary** (the default). It *stalks* you: it creeps after you crouched low and silent,
  keeping behind you. Look straight at it and it freezes; keep staring and it slinks off into cover
  where you cannot see it. Turn your back while you are hurt and it strikes. At night it hunts
  anyone it does not trust.
- **Earning trust.** Leave bottled blood on the ground and back off; it only comes for it when
  nobody is standing over it, though crouching quietly nearby is fine. Each offering it drinks
  is worth +12 trust. Crouching patiently nearby without staring also helps, but only up to 25.
  Charging at it, staring it down, hitting it (-35) or killing its kind (-50) destroy trust.
  Hated players are hunted on sight.
- **Curious** (trust 20+). It drops the act, comes over openly and watches you.
- **Taming needs trust 40+.** Below that it refuses to feed from you.
- **Tamed: the opposite.** Trust becomes its **bond** with you. It stays in plain sight, comes up
  close when you settle down, purrs and shows affection, and greets you when you come back. Good
  care (food, petting, hand-feeding) builds the bond; starving or hitting it wears it down. A
  sulking Gorewidow keeps its distance, sometimes ignores orders and whistles, and refuses to be
  ridden below -20.

Bottled blood means this mod's Blood Vial **and, if installed, Iron's Spells 'n Spellbooks'
blood vial** (`irons_spellbooks:blood_vial`, via the `gorewidow:blood_food` item tag, no hard
dependency). Both work for offerings, taming, feeding and cargo.

## Taming (passive, ARK-style)

Every wild Gorewidow has a **level** (1–30). Its points are spread randomly over health,
stamina, food, carry weight and melee damage.

1. Craft **Blood Vials**: glass bottle + any raw meat + redstone. Carry them in your inventory.
2. Once it trusts you (40+), **sneak and use** the wild Gorewidow. It latches onto you and drinks one vial every 3 seconds
   for as long as you stay crouched within reach. Stand up or walk away and it pauses; if you are
   gone for 30 seconds it loses interest.
3. When the vials run out it drinks **your** blood instead: you take damage, progress grows half
   as fast, and taming effectiveness drops.
4. **Taming effectiveness** starts at 100% and falls whenever the creature takes damage while being
   tamed. On completion it gains `wild level × 0.5 × effectiveness` bonus levels.
5. If you hit it yourself while taming it, you lose 30% of the progress and it attacks you.

The HUD is a small panel at the top of the screen. For the Gorewidow under the crosshair it shows
the name, sex and level, a health bar, and one line with what matters right now: taming progress
and effectiveness, a baby's growth and imprint or what it wants, or otherwise its trust and mood.
While riding, three slim bars in the corner show health, stamina and food. A weight bar appears
when the cargo is nearly full.

## A tamed Gorewidow

| Action | How |
|---|---|
| Ride | Use with an empty hand (no saddle needed) |
| **Option wheel** | Sneak + use: Ride, Inventory, Follow / Stay / Wander, Aggressive / Neutral / Passive, Pet / Cuddle, Mating, Follow distance (near / medium / far), Allow others to ride, Take harness (number keys work too) |
| Inventory and stats | From the wheel or the screen's buttons: harness slot, 15 cargo slots, name, level, XP and every stat with a **+** button for unspent points |
| Feed / heal | Use a Blood Vial or raw meat on it. When hungry it also eats from its own cargo |
| Whistles | **J** follow me, **K** stay, **H** attack what I am looking at, **U** go where I point (the block under the crosshair, up to 64 blocks: it walks, climbs walls and ceilings, or fires a line and reels itself up, then waits there), **Y** come and pick me up (the nearest one comes over and takes you into the riding hold). Applies to every owned Gorewidow within 40 blocks, or to the one under the crosshair. No harness needed |
| Remove harness | Wheel → Take harness, or sneak + use with shears |

- **Levels.** Kills made by the creature or by its rider give XP. Each level-up gives one point to
  spend on Health, Stamina, Food, Weight, Melee or Speed (Speed can only be raised after taming).
  Tamed creatures can gain up to 75 extra levels.
- **Food** drains over time, faster while sprinting. A starving Gorewidow takes damage; a well-fed
  one slowly regenerates.
- **Stamina** is spent by sprinting while ridden (sprint key), climbing walls and ceilings, leaps
  and web shots, and regenerates when the creature rests.
- **Weight.** Everything in the cargo has weight (blocks are heavy). An overloaded Gorewidow can
  barely move, cannot leap and cannot swing.
- **Attitude.** Aggressive hunts nearby monsters and untamed animals (never players or your
  pets). Neutral defends itself and you. Passive never fights.

## Breeding and imprinting (ARK-style)

- **Sex and mating.** Every Gorewidow is male or female (shown on its panel). Turn **Mating** on
  in the option wheel for a tamed female and a tamed male. Near each other they mate and she lays a
  **Fertilized Gorewidow Egg**, then rests for 10 minutes before she can mate again.
- **Inheritance and mutations.** The egg already holds the child. Each stat's points come from one
  parent, the stronger one winning 55% of the time. Each parent's line may add a **mutation** (+2
  points in a random stat, 2.5% chance, up to 20 per line). Generation and mutation counters are
  tracked.
- **Incubation.** Place the egg within 3 blocks of heat (a campfire, fire, lava, magma, a lit
  furnace, a torch or a lantern). Warm, it incubates for 5 minutes. Cold, it loses health and dies.
  Punch it to pick it up; it keeps its progress. Use it to check its temperature.
- **Babies** hatch tamed to the parents' owner and grow over 40 minutes, from a third of adult size
  to full size. They can't be ridden and don't use web attacks until grown.
- **Imprinting.** While growing, the baby asks for **a cuddle** (wheel → Cuddle), **food from your
  hand** or **a walk** (stay with it while it covers 30 blocks). Every request answered in time adds
  12.5% imprint, and the first player to answer becomes its only imprinter. Imprint raises every
  stat except speed by up to 20%. When its imprinter rides it, the mount deals up to 30% more damage
  and takes up to 30% less.

## Riding (ARK-style)

No saddle is needed. Use your tamed Gorewidow with an empty hand and it mounts you in three beats:
it crouches and **seizes you with its front legs** (silk threads lash on), then rises and **hauls
you up under its belly**, and finally settles as its claspers lock on. You end up **hanging under
the belly at an angle**, head up and forward, legs trailing behind. Two pairs of short **grasping arms** under its thorax then close
around you: their hooked hands take you by the shoulders and the hips and keep hold (an IK solve
every frame, so the grip stays put while it walks, climbs and swings). Out of the saddle they fold
flat against the belly. You stay there on floors, walls, ceilings and in swings. When you
dismount it lowers itself and the threads snap away. A **Gorewidow Harness** is optional: the rider
takes 35% less damage and the mount carries 60 more weight.

| Key (rebindable) | Action |
|---|---|
| W/A/S/D | Move relative to where you look, on any surface. Walk into a wall to climb it |
| Space | Leap. In the air, tap it again for a **double jump**; **hold** it while falling to **sail**: it throws its legs wide and rolls them so the membranes lie flat like a canopy, hangs almost still for a moment, then sinks slowly, drifting where you look. On a wall or ceiling, jump off it. On a web line, let go and keep the momentum |
| R | Shoot a web line at the crosshair: a block becomes a swing anchor, a creature gets pulled in. Press again to release |
| C | **Slingshot**: fire two lines in a V ahead and launch yourself between them (25 stamina) |
| Z / X | Reel the line in / pay it out |
| G | Bite what you look at. Biting a webbed or wounded target starts a blood drain that heals the mount |
| V | Toggle **spider vision** |
| B | **Weapon stance**: it rears up almost vertically on its hind legs and holds you high up and out in front, cradled in its front legs, so you can shoot, fight and use any item. Turns on by itself when you hold a bow, crossbow or trident, or anything in the `gorewidow:mounted_weapons` tag (for modded guns or staves). It moves at half speed, its web, bite and slingshot are put away, and your projectiles fly past it |
| N | Tilt the camera with the mount on/off |
| Sprint key | Sprint (costs stamina) |
| Shift | Dismount |

**Using it as a work mount.** Hanging under it, you keep your hands free:
- **Mine and build** from any surface: walls, ceilings, cliff faces. The usual "not on the ground"
  mining penalty does not apply to its rider, so ore breaks as fast as when you stand.
- **Use anything**: open your inventory (E), read books, open chests, place blocks, eat. Your
  first-person view sits just ahead of its fangs, so its head never blocks the view, and a leg
  planted right next to your eyes is left out of your own view.
- **Room to see.** While carrying you it holds its belly up and away from you, higher still when
  you look up, and your field of view widens a little.
- **Your view banks with it**: on a wall or slope the horizon tilts with the body (up to 50
  degrees, smoothly). **N** toggles this.

Passives, after ARK's Bloodstalker:
- **Yanks riders off their mounts.** A wild Gorewidow's web pulls a mounted player out of the saddle.
- **Spider vision.** The rider gets night vision, and hostile creatures nearby (monsters, wild
  Gorewidows, anything targeting you) glow through walls.
- **Feeds when wounded.** Below 40% health it drinks from the rider: a blood vial from the pack if
  you carry one, otherwise your own blood.
- **Skates on water.** Its long legs keep it standing on the surface.
- **Climbs anything**, wrapping around edges and onto ceilings.
- **Sails on its membranes.** A wild one that falls more than a few blocks spreads its legs and
  parachutes down unhurt.

## How it is built

- **Movement.** The surface the creature clings to is real physics state, not an animation. On walls
  and ceilings it moves in the surface plane and detects inner corners (it switches surfaces) and
  outer edges (it wraps around them). A web line is a pendulum with a hard length constraint, reel
  in/out, momentum and grabbing whatever it swings into. Whoever controls the creature runs the
  physics (the server for the AI, the rider's client while ridden), and surface changes sync to
  the server.
- **Smooth surface changes.** The body turns onto a new surface over a fraction of a second, a
  one-step position change (wrapping round an outer edge) is blended out over a few frames, and
  right after a change it does not flip straight back (no flicker in inside corners).
- **Procedural rig** (`client/GorewidowRig`). Every foot is planted in the world and re-placed in
  an alternating gait by ray-casting the surface ahead. An analytic IK solve per leg (coxa yaw plus
  two-bone with a near-vertical tarsus) reaches the planted foot, and the body pitches, rolls and
  rises with the terrain. Curl, spread, raise and grip poses blend in during jumps, swings,
  attacks, feeding and death.
- **Keyframe animations** (GeckoLib, 24 clips, incl. the grab lunge) drive the body, head, fangs, palps, abdomen and
  spinneret, with a state-machine controller plus triggered one-shots.
- **Web lines** render as crossed silk ribbons with sag when slack, a twang when pulled taut, and an
  animated retraction when released.

The mod is fully translated into Russian (`ru_ru`); every in-game string has an entry.

## Development

```
./gradlew build               # build the jar
./gradlew runClient           # play
./gradlew runGameTestServer   # 23 game tests (incl. weapon stance): clinging, climbing, swing physics, tethering,
                              # slingshot, water walking, trust, feeding-tame, level-ups, cargo,
                              # mount sequence, riding underneath, rider drain, mating and eggs,
                              # incubation and hatching, imprinting, pulling riders off mounts
./gradlew runShowcase         # dev only: needs a world in run/saves/showcase; stages every
                              # state and saves screenshots to run/screenshots
                              # (-Dgorewidow.showcase.ui / .mind / .ride / .family / .lair = true;
                              # .motion = true films floor-wall-ceiling, leap+sail and web swing,
                              # a frame every 3 ticks)
```

Screenshots from the real game are in `docs/screenshots/`.

The `tools/` folder holds the generators for every asset: the model and texture, the animations,
the pixel art and JSON data, the synthesized sounds and the test arena. Rerun them after changing
proportions or sounds. `tools/preview_model.py` renders the model with GeckoLib's own transform
conventions, so you can check it without starting the game.
