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

## Taming

1. Craft **Blood Vials**: glass bottle + any raw meat + redstone.
2. Use a vial on a wild Gorewidow. It drinks, calms down toward you for 30 seconds and the taming
   meter goes up. It takes about 5–7 vials.
3. If you hit it while taming it, it loses progress and turns on you.

Tamed Gorewidows:

- **Sneak + use with an empty hand** cycles the order: Follow / Stay / Wander.
- **Blood Vial or raw meat** heals it.
- **Gorewidow Harness** (leather, string, iron) makes it rideable. To take the harness off, sneak and
  use shears on it.

## Riding

Use it with an empty hand to mount. You hang under its belly on floors, walls, ceilings and in swings.

| Key (rebindable) | Action |
|---|---|
| W/A/S/D | Move relative to where you look, on any surface. Walk into a wall to climb it |
| Space (hold to charge) | Leap. On a wall or ceiling, jump off it. On a web line, let go and keep the momentum |
| R | Shoot a web line at the crosshair: a block becomes a swing anchor, a creature gets pulled in. Press again to release |
| Z / X | Reel the line in / pay it out |
| G | Bite what you look at. Biting a webbed or wounded target starts a blood drain that heals the mount |
| Shift | Dismount |

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
./gradlew runGameTestServer   # 7 game tests covering clinging, climbing, swing physics,
                              # tethering, taming, riding underneath and draining
./gradlew runShowcase         # dev only: needs a world in run/saves/showcase; stages every
                              # state and saves screenshots to run/screenshots
```

Screenshots from the real game are in `docs/screenshots/`.

The `tools/` folder holds the generators for every asset: the model and texture, the animations,
the pixel art and JSON data, the synthesized sounds and the test arena. Rerun them after changing
proportions or sounds. `tools/preview_model.py` renders the model with GeckoLib's own transform
conventions, so you can check it without starting the game.
