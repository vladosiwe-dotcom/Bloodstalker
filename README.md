# Gorewidow

**Gorewidow** is a NeoForge 1.21.1 mod that adds one creature: a long-legged blood predator
that crawls on any surface, swings on web lines, reels its prey in and drains it. Tame it and you
ride it the way a climber rides a harness, strapped face-down **under** its belly.

The creature, its model, textures, animations and sounds are all original.

- Minecraft 1.21.1, NeoForge 21.1.150+ (built against 21.1.266)
- GeckoLib 4.9.x (required dependency)

## In the wild

Gorewidows spawn rarely in jungles, forests, taigas and lush caves, at night or under thick
canopy. They hunt the way spiders do:

- **Ambush.** It webs its way up to a branch or cave ceiling, goes still, sometimes drops down on a
  thread and dangles there, waiting for prey to pass below.
- **Pounce.** From the ground or out of an ambush it leaps on its target in a ballistic arc.
- **Web pull.** It snags a distant target with a web line and reels it in. A target too heavy to
  pull gets the creature hauling itself toward it instead.
- **Web swing.** It crosses long gaps and climbs to high targets by firing a line at a high anchor,
  pumping the swing and letting go at the top of the arc.
- **Wall and ceiling crawling.** It climbs walls, crosses ceilings and wraps around edges.
- **Blood drain.** It latches onto webbed or weakened prey, drinks from it and heals itself.

## Taming (passive, ARK-style)

Every wild Gorewidow has a **level** (1–30). Its points are spread randomly over health,
stamina, food, carry weight and melee damage.

1. Craft **Blood Vials**: glass bottle + any raw meat + redstone. Carry them in your inventory.
2. **Sneak and use** the wild Gorewidow. It latches onto you and drinks one vial every 3 seconds
   for as long as you stay crouched within reach. Stand up or walk away and it pauses; if you are
   gone for 30 seconds it loses interest.
3. When the vials run out it drinks **your** blood instead: you take damage, progress grows half
   as fast, and taming effectiveness drops.
4. **Taming effectiveness** starts at 100% and falls whenever the creature takes damage while being
   tamed. On completion it gains `wild level × 0.5 × effectiveness` bonus levels.
5. If you hit it yourself while taming it, you lose 30% of the progress and it attacks you.

The HUD over a looked-at Gorewidow shows its level, health, taming progress, effectiveness, the
vials still needed and the bonus levels you would get.

## A tamed Gorewidow

| Action | How |
|---|---|
| Ride | Use with an empty hand (needs a harness) |
| **Option wheel** | Sneak + use: Ride, Inventory, Follow / Stay / Wander, Aggressive / Neutral / Passive, Pet, Take harness (number keys work too) |
| Inventory and stats | From the wheel or the screen's buttons: harness slot, 15 cargo slots, name, level, XP and every stat with a **+** button for unspent points |
| Feed / heal | Use a Blood Vial or raw meat on it. When hungry it also eats from its own cargo |
| Whistles | **J** follow me, **K** stay, **H** attack what I am looking at. Applies to every owned Gorewidow within 40 blocks, or to the one under the crosshair |
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

## How it is built

- **Movement.** The surface the creature clings to is real physics state, not an animation. On walls
  and ceilings it moves in the surface plane and detects inner corners (it switches surfaces) and
  outer edges (it wraps around them). A web line is a pendulum with a hard length constraint, reel
  in/out, momentum and grabbing whatever it swings into. Whoever controls the creature runs the
  physics (the server for the AI, the rider's client while ridden), and surface changes sync to
  the server.
- **Procedural rig** (`client/GorewidowRig`). Every foot is planted in the world and re-placed in
  an alternating gait by ray-casting the surface ahead. An analytic IK solve per leg (coxa yaw plus
  two-bone with a near-vertical tarsus) reaches the planted foot, and the body pitches, rolls and
  rises with the terrain. Curl, spread, raise and grip poses blend in during jumps, swings,
  attacks, feeding and death.
- **Keyframe animations** (GeckoLib, 21 clips) drive the body, head, fangs, palps, abdomen and
  spinneret, with a state-machine controller plus triggered one-shots.
- **Web lines** render as crossed silk ribbons with sag when slack, a twang when pulled taut, and an
  animated retraction when released.

## Development

```
./gradlew build               # build the jar
./gradlew runClient           # play
./gradlew runGameTestServer   # 10 game tests: clinging, climbing, swing physics, tethering,
                              # feeding-tame with effectiveness, level-ups, cargo weight and
                              # eating, riding underneath, draining
./gradlew runShowcase         # dev only: needs a world in run/saves/showcase; stages every
                              # state and saves screenshots to run/screenshots
                              # (-Dgorewidow.showcase.ui=true for the interface scenes)
```

Screenshots from the real game are in `docs/screenshots/`.

The `tools/` folder holds the generators for every asset: the model and texture, the animations,
the pixel art and JSON data, the synthesized sounds and the test arena. Rerun them after changing
proportions or sounds. `tools/preview_model.py` renders the model with GeckoLib's own transform
conventions, so you can check it without starting the game.
