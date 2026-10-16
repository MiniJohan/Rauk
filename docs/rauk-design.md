# Rauk — Creature Design

**Draft 0.3: design approved, building one part at a time** · 2026-10-04 · Minecraft 1.21.1 / Fabric · Covers the creature only. The hotel it lives in gets its own design pass later.

> Everything here is a proposal. Numbers marked *(tune)* are starting points for playtesting, not commitments.
>
> **Changes in 0.2:** rewritten for the hotel. Echolocation is gone; it now taps on doors and walls and listens for what moves. New door, light-switch, room-search and territory behaviors. Climbing rules changed to match hotel surfaces. Added a list of spaces the hotel needs to give it (section 12).
>
> **Changes in 0.3:** decisions from the first review (section 14). It switches lights off *and* on, no room is a refuge, totems don't save you, 64 pixels per block, exactly one Rauk, and notes for the hotel pass.

---

## 1. At a glance

Rauk is a solitary apex predator that lives in a hotel: pale, 7 blocks tall, thin as a dead tree, and stronger and faster than anything else in the world. The hotel is its territory and it never leaves. Neither can you, until you find a way out. It knows every corridor, shaft and room, and it notices when something has changed. Most of the time it doesn't move at all. When it does, it moves over floors, walls and ceilings with the same ease, and it can cover the length of a corridor before you've finished turning around.

It hunts by hearing, smell and eyes adapted to the dark. It listens at doors, sniffs along thresholds, and taps on walls to flush out whatever is hiding. It keeps track of what you do (where you look, what you hold, where you hide, what you did last time) and plans around it. It never roars, never teleports and never glows. Everything frightening about it comes from its body and from the fact that it is clearly thinking.

### Name

A *rauk* (Swedish, plural *raukar*) is one of the tall, pale limestone sea stacks of Gotland and Fårö, which erode into shapes that look like standing figures. The creature is named after them. Standing upright and still, it looks like a pale stone pillar, and in the hotel lobby like one more column. Its skin is the color of old plaster and marble, so in the half-dark it looks like part of the building until it moves.

### Design pillars

1. **Stillness, then violence.** It's motionless most of the time and explosive the rest. Every movement has a purpose; it never fidgets.
2. **It's thinking about you.** Its behavior comes from what it senses and remembers, not from scripts. It isn't all-knowing: it can lose you, guess wrong and be fooled, and it learns from each of those.
3. **One body.** Every pose and movement comes from the same skeleton, joint limits, mass and muscle model. If a real body couldn't reach a pose, Rauk can't either.
4. **Physical, not magical.** Every ability has an anatomical cause, and every feature that gives it an ability also gives the player a way to exploit it (see 2.13).
5. **The head is the mind.** The head stays steady while the body does chaotic things beneath it. Where the head points is the most important thing the player can read, and it is deliberately hard to read.

### Three silhouettes, one animal

| | What it looks like | Where you see it |
|---|---|---|
| **Pillar** | Tall and narrow, arms hanging past its knees, head pushed forward on a long neck. From across the lobby, one of the columns. | The lobby, atrium, ballroom, stairwells and shafts: the only spaces tall enough |
| **Spider** | Elbows and knees higher than its spine, limbs spread wide, body slung low between them. | Corridors and rooms (most of the hotel is too low for it to stand), on walls and ceilings, through doorways |
| **Heron** | Long neck, head held level and perfectly still, moving in sharp snaps while the body below does something else. | Always. Often the head comes around a corner before anything else does. |

### Deliberately left out

- Glowing eyes. Its eyes only *reflect* light (eyeshine, see 2.3).
- Voice mimicry, teleporting, invisibility, moving through blocks. Its taps on doors sound like knocking, but it isn't imitating anyone (3.1).
- Extra limbs, tentacles, random body horror. Its strangeness is in its proportions and its motion, not in distortion.
- Roars and warning screams. Predators don't warn their prey.
- Scripted jump scares. Every scare comes from it actually hunting you.

---

## 2. Anatomy

> **The reference drawings win.** Where this section disagrees with them, follow the drawings: a long spiked tail that drops behind the legs and trails along the ground; a long, arched, segmented neck carrying the skull low; an exposed ribcage hump; a human skull with deep black sockets, cracks, a dropped jaw and fangs, gripped from behind by two bony hands; bloody drool. Pale bone colours throughout.

### 2.1 Proportions

1 block ≈ 1 m. Its mass is about 650 kg, which is the reference for every physics and animation constant.

| Part | Size (blocks) | Notes |
|---|---|---|
| Height, fully extended | 8.0 | On its toes, neck extended. Only used when observing. |
| Height, relaxed upright | 7.0 | Slight forward hunch, head carried forward |
| Height, stalking upright | 6.0 | Knees bent, shoulders low, neck level |
| Head (crown to chin) | 0.85 | Width 0.42, depth 0.75 |
| Neck | 1.1 | Longer than the head |
| Torso (shoulder to hip) | 2.0 | |
| Chest depth / width | 0.9 / 0.8 | Deeper than it is wide (keel chest) |
| Waist width | 0.42 | |
| Shoulders, spread / collapsed | 1.3 / 0.75 | No rigid collarbone (2.6) |
| Upper arm / forearm / hand | 1.7 / 1.8 / 0.95 | Fingertips hang about 1 block above the ground, below the knee |
| Thigh / shin / foot / toes | 1.5 / 1.6 / 0.75 / 0.45 | Heel held about 0.6 off the ground |

The arms are longer than the legs. Standing, that gives it the hanging-arm silhouette. On all fours, its shoulders ride higher than its hips, like a hyena's.

### 2.2 Postures and collision

Minecraft collision boxes are axis-aligned and square from above, so Rauk switches collision box with its posture. It only switches when the new box fits, the same kind of check the player's crouch and swim use.

| Posture | Visual height | Hitbox w × h *(tune)* | Needs |
|---|---|---|---|
| Upright | 6.0–8.0 | 1.2 × 6.4 | 7+ blocks of clearance (8.5 for full extension) |
| Quadruped | ~3.0 | 1.8 × 2.9 | 3 high, 2 wide |
| Low crawl | ~1.9 | 1.8 × 1.9 | 2 high, 2 wide |
| Squeeze | ~1.8 | 0.75 × 1.8 | 2 high, 1 wide (doorways, service passages). Narrow enough to pass an open door's panel. |
| Cling (wall or ceiling) | — | 1.8 × 1.9 | A surface it can grip (2.10) |

**In the hotel**, most corridors and rooms are about 3 blocks high, so it spends most of its time on all fours. It only stands up in the lobby, atrium, ballroom, stairwells and elevator shafts. If you see it stand up, you're in a big room.

Gaps 1 block high are a hard limit: the rigid keel of its chest and the ridge of its spine leave no room to pull itself along. That means vents are closed to it, but it can reach about 4 blocks into one with an arm.

### 2.3 Head and face

The skull is long and narrow, with a flat, forward-facing face like a primate's. Forward-facing eyes mean binocular vision, which means a predator. The face is stretched vertically, though, so it's too tall for its width.

- **Brow:** a heavy, overhanging shelf. Under almost any light, the eyes sit in shadow.
- **Eyes:** small, black and deep-set. A reflective layer behind the retina gives a cold silver-green **eyeshine** when light hits it from the right angle. A milky **nictitating membrane** sweeps sideways across the eye. Half-closed in bright light, the eyes look blind, so you can't tell whether it sees you.
- **Nose:** none. Two narrow vertical slits flare when it smells.
- **Mouth:** no lips. A thin closed seam runs from the chin almost back to the jaw hinge. Lit from below, for example by a lantern you're holding, the seam looks like a faint smile. Lit from above, it looks like nothing. It has no expressions, only mechanisms.
- **Jaw:** opens to about 70°. The gums pull back as it opens, showing the teeth at full length: long, narrow, interlocking teeth at the front for gripping, and shearing teeth at the back for cutting. The teeth are yellow-grey; the gums and the inside of the mouth are blue-black. The tongue is long and dark.
- **Listening folds:** it has no outer ears. Along each side of the skull, behind the jaw, thin folds of skin lie flat. When it listens hard, they lift and open like gills, exposing the eardrums underneath. This is the clearest signal that it's listening.

### 2.4 Neck

1.1 blocks long, corded with muscle, and as flexible as a bird's. Its range relative to the chest: yaw ±150°, pitch −80° to +90°, roll ±120°. That range lets the head stay level while the body climbs a wall or hangs from a ceiling (5.6), and lets it look around a corner while its body stays hidden. When the neck twists far, the skin on the inside of the turn bunches into folds, and that should be visible.

### 2.5 Torso and spine

- **Chest:** deep and narrow like a greyhound's, with room for the lungs and heart a sprinter needs. The ribs show under the skin and visibly expand when it breathes. The rib cartilage is flexible, so the chest can compress for squeezing.
- **Abdomen:** pulled in hard, almost hollow below the ribs.
- **Spine:** long, and very flexible at the lower back, so it bends and straightens hard in the gallop like a cheetah's. Fourteen bony knobs run down the back from the base of the neck to the pelvis. On all fours they form a serrated ridge, the most recognizable line in its silhouette.
- **Pelvis:** narrow but tall.

### 2.6 Shoulders and arms

Rauk has **no rigid collarbone**. Its shoulder blades are held only by muscle and slide freely over the ribs, like a cat's. That one trait explains three abilities:

- **Reach:** the shoulder slides forward, adding about 0.5 blocks of reach.
- **Squeezing:** the shoulders fold in from 1.3 to 0.75 blocks wide, with a visible and audible pop as the shoulder blades shift. This is how it gets through doorways.
- **Climbing:** long overhead reach, with the shoulder blades rising up the back as it pulls itself up.

Its elbows point backward when it's upright and outward and up when it crawls ("spider elbows"). The forearm has two bones and can rotate, so the palm can turn over to grip a ceiling.

### 2.7 Hands

- Four fingers and an opposable thumb. Each finger has **one extra segment**, so the fingers wrap over edges, door frames and prey. At rest they curl loosely. Straightened, they turn out longer than you expected.
- The fingers are 0.55 long and the palm 0.4. The thumb sits low on the palm for grip.
- **Nails:** thick, dark horn, hooked and blunted by wear. They are climbing tools first and raking weapons second, and they make its tapping sound (3.1).
- **Pads:** the palms and knuckles have grey-brown callouses from walking on its hands.
- After the head, the hands are the most expressive part of the body. The fingers curl slowly and tap once. A single finger hooks around a door's edge to pull it open.

### 2.8 Legs and feet

- **Semi-digitigrade:** it stands on the balls of its feet with the heel about 0.6 blocks up, so the leg has a high, backward-pointing "second knee". The knees face forward like a human's. The silhouette stays humanoid but has spring in it.
- **Feet:** long and able to grip, with an opposable big toe set apart like an ape's. The feet work as a second pair of hands when it climbs or hangs from a ceiling.
- **Soles:** thick pads. On carpet, its careful steps are almost silent.

### 2.9 Skin

- **Base color:** a cold grey-white like old plaster and marble (in Minecraft terms, calcite, diorite, quartz and bone block tones), with faint mottling on the back and shoulders like water-stained plaster.
- **Thin skin** (fingertips, eyelids, the mouth seam, the insides of the joints, the listening folds) is translucent and shows blood flow as a dull pink-violet.
- **Veins:** faint blue-grey on the forearms, the neck and the insides of the thighs.
- **Over bone** (brow, cheekbones, knuckles, shins, spine, ribs), the skin is stretched tight with a waxy sheen.
- **Folds** form at the elbows, knees, armpits and neck when they compress.
- **Callouses:** grey-brown on the palms, knuckles, soles, knees and elbows.
- **History:** this individual is old. It has three parallel healed scars across the left side of its chest, a broken left ring finger that healed crooked, and old burn scarring on the right forearm. Details like these make it an individual rather than a mob type.
- No hair anywhere.

### 2.10 Grip

It climbs by digging its nails into surfaces that give a little or have seams: plaster, wallpaper, wood, carpet, concrete, brick and stone. It can also climb chains (elevator cables). It **can't** grip hard, smooth surfaces: glass and panes, glazed terracotta, the quartz family and polished stone, metal blocks, and ice.

In the hotel, that means it moves freely along the walls and ceilings of corridors and rooms, but not in **tiled bathrooms**, on **marble** in the lobby, on **mirrors and windows**, or around the **kitchen's metal**. The list lives in a data-driven block tag, `rauk:unclimbable`, so it can be tuned along with the hotel's palette. Players who notice can use these spaces against it.

### 2.11 Physical constants *(tune)*

| | |
|---|---|
| Mass | ~650 kg |
| Knockback resistance | 0.95 |
| Falling | Any height. It lands on all fours. |
| Body temperature | Warm-blooded |
| Buoyancy | Negative: almost no body fat and dense bones. It sinks. |

### 2.12 Always bloody

**Nothing hurts it** (decided in review, 2026-10-06): blades, arrows, fire, falls and drowning do nothing to it. A blow still lands: it startles and turns on whoever struck it, arrows glance off, and a blow to the face while it holds you still loosens its grip (7.2).

It is never clean. Its jaws, chin and throat are thick with its kills' blood, its hands are soaked to the wrist and splashed up the forearms, and there are dried splashes and runs of it all over its body. It drips: now and then a drop falls from its jaws, a faint trail of stains across the floor behind it (they dry and fade).

### 2.13 Every trait cuts both ways

| Trait | What it gives Rauk | What it costs Rauk |
|---|---|---|
| No collarbone | Extra reach; gets through any doorway | Squeezing through is slow and loud, and it can't dodge while doing it |
| Hooked nails | Climbing walls and ceilings | Can't grip glass, tile, marble or metal |
| Dense, fat-free body | Power and durability | Sinks, so deep water stops it |
| Dark-adapted eyes | Sees well in a dark hotel | Bright light washes out its view of anything standing still |
| Tapping and listening | Flushes out hidden prey | You hear it coming, door by door |
| Smell | Follows your exact path, smells you through a door | Water and doubling back break the trail |
| Sprinter's chest | Bursts of 10–13 blocks per second | About 5 seconds, then it has to recover |
| Pale, thin skin | Blends in with plaster and marble | Shows every bit of blood on it |
| Keel chest | Big lungs and heart | Gaps 1 block high (vents) stop it |
| Long, stabilized neck | Steady gaze while moving wildly; looks around corners | The head is exposed: a blow to the face makes it let go |

---

## 3. Senses

It knows only what these senses tell it. It can't see through walls and it can't know where you are without sensing you. It acts on its **best guess** of where you are, and that guess can be wrong.

| Sense | Ranges *(tune)* | Works best | Defeated by | What you see it do |
|---|---|---|---|---|
| **Hearing** | Sprinting 24, walking 12, breaking blocks 32, doors 24, eating 10, combat 32. Halved through walls and floors; doors barely muffle anything. Carries between floors through vents, shafts and stairwells. | A quiet hotel | Sneaking, distance, louder noises elsewhere | Head snaps toward the sound, listening folds lift |
| **Listening at doors and walls** | Movement in the room behind: walking 12, sneaking 4 | Pressed against a door or thin wall | Staying completely still | The side of its head pressed to the door, folds open |
| **Tap and listen** (active) | After tapping, it hears anything that moves within 16 for about 3 seconds, even sneaking | Searching a floor room by room | Not moving at all after a tap | Two or three slow taps, then silence |
| **Vision** | Dark: movement 40, still 10. Bright: movement 28, still 4. A light you carry is visible from 48 down a dark corridor. | Dim light, long corridors, anything that moves | Standing still in light, cover, putting your light away | Head locks on. In bright light the membrane half-covers its eyes. |
| **Smell** | Follows scent trails up to about 2 in-game days old (still indoor air keeps them). Smells you through a closed door if you're within 3 blocks of it. Smells blood (injured players) at 32. | Fresh trails, injured prey | Water (breaks the trail), doubling back (confuses it for a while), staying away from the door, time | Head low along the corridor floor; face pressed to the gap under a door, nostrils flaring |

### 3.1 Tap and listen

When it knows prey is close but not exactly where, it taps a nail against a door or wall two or three times, slowly, then goes completely still and listens. Hidden prey usually reacts to a sound, and the reaction gives it away. Herons do something similar, stirring the mud and watching what moves.

In a hotel this sounds exactly like someone knocking. It isn't imitating anyone. That resemblance is just a coincidence it happens to benefit from. The rule for the player is simple: **after a knock, don't move.**

### 3.2 Notes

- **Light and dark.** In the dark it sees you better than you see it, even standing still at close range. In light it relies on movement, so standing still is your best defense.
- **A light you carry** (if the hotel pass adds one) helps you see but gives you away: in a dark corridor it sees the light long before it sees you. Putting it away at the right moment is a skill.
- **Smell leads to doors.** Your scent trail goes right up to the door you hid behind. Hiding works best when you've broken the trail first (doubled back, walked through water) and stay well away from the door.
- **An injured player smells stronger**, so low health makes you easier to track.
- Hearing is built on vanilla game events (the vibration system 1.21 already has), so sneaking already silences footsteps. Compared with the Warden: the Warden is blind and reacts to stimuli, while Rauk combines all its senses into one guess and plans around it.

---

## 4. Movement

### 4.1 Locomotion modes

Speeds are in blocks per second. For reference, the player walks at 4.3, sprints at 5.6 and sprint-jumps at about 7.

| Mode | Posture | Speed *(tune)* | Used for | Sound |
|---|---|---|---|---|
| Stalk | Upright, knees bent | 1.5–3 | Approaching unseen in big rooms, observing | Almost silent |
| Stride | Upright, long steps | up to 5.5 | Crossing the lobby or ballroom without hurrying. Its *walk* keeps up with your sprint. | Soft, heavy steps |
| Prowl | Low, on all fours | 2–4 | Moving through corridors and rooms, following scent | Silent on carpet; nail clicks on tile, creaks on wood |
| Gallop | All fours, spine flexing | **10–13 in bursts** | Chasing down a corridor | Heavy pounding, floors shaking |
| Low crawl | Spider posture, 2 high | up to 4 | Service crawlspaces, low passages | Skin dragging, nails |
| Squeeze | Shoulders collapsed, 1 wide | ~1.2 s per doorway; 0.5–1 in 1-wide passages | Doorways, 1-wide service passages | Joints popping, scraping, breathing |
| Wall climb | Clinging, limbs move in diagonal pairs | 3–6 | Shafts, stairwells, getting above you | Nails scratching plaster |
| Ceiling crawl | Hanging, at least 3 limbs gripping | 2–4 | Ambushes, crossing corridors and rooms unseen | Slow scratching, falling plaster dust |
| Leap | Ballistic arc | up to 14 across, 7 up | Between atrium galleries, down stairwells, ambushes | Silent until it lands |

**Floors matter.** On carpet it's nearly silent. On wood the boards creak under its weight, and on tile and marble its nails click. When it wants to be quiet, it prefers carpet, walls and ceilings. When it stops caring about being heard, it has decided to attack.

### 4.2 Stamina

The gallop is an anaerobic burst, like a cheetah's: about **5 seconds at full speed**, then **8 seconds of recovery** during which it can go no faster than its stride. While recovering it pants visibly, mouth open and ribs heaving. That is your best chance to get through a door and out of sight. It knows its limit, so it won't start a chase it can't finish, and it tries to start every chase as close to you as possible.

### 4.3 Leaps

- It only leaps to surfaces it has actually sensed, along an arc it has checked is clear. It never leaps blind.
- The wind-up (crouching, spine coiling) takes 0.25–0.6 seconds, longer for longer leaps. That's your warning, and it's short.
- It can leap from floor to wall, wall to ceiling, ceiling to floor, across the atrium between gallery floors, and down a stairwell's central gap. A leap can end on a surface or directly on its prey.

### 4.4 Doorways and tight spaces

- It can get through any space at least 2 blocks high and 1 block wide by changing posture.
- **A doorway** takes about 1.2 seconds: the head goes through, the shoulders pop, and the body flows after it. A **1-wide passage** (service corridors) is a slow squeeze the whole way.
- Squeezing is loud and dangerous for it, because it can't dodge and can only grab straight ahead. It knows this. It won't come through a doorway you're aiming at unless it's desperate. Instead it waits beside the door or goes around.
- Gaps 1 block high (vents) stop it completely, so it reaches in instead.

### 4.5 Water

- It wades through anything it can stand in, up to about 5 blocks deep: flooded basements, the pool.
- It sinks, so it won't enter deeper water.
- Walking through water breaks your scent trail.

### 4.6 Doors, lights and blocks

The rule: **it understands openings and light, nothing more.**

- **Doors:** it opens doors, trapdoors and gates by hooking them with a finger, and **leaves them open**. A door you closed that's now standing open is a sign. No door in the hotel keeps it out, so no room is a permanent refuge.
- **Lights:** it **switches lights off and on** with the hotel's levers, and knocks lanterns and candles down (they drop as items). It learns which lever controls which lights by trying them, or by watching you use them. It uses light in three ways:
  - **Hiding:** it switches off the lights along its approach.
  - **Steering:** prey avoids the dark and moves toward light, so it switches lights on where it wants you to go: a dead end, or a corridor where it waits on the ceiling.
  - **Flushing:** it flicks a room's lights off and on, then listens, the same way it taps on walls (3.1).

  For the player, this should be disorienting: the lighting is never quite how you left it. It stays away from open fire (7.3).
- **Everything else:** buttons and any redstone beyond light switches mean nothing to it. It doesn't break blocks.

### 4.7 Finding its way (reworked, 2026-10-14)

Asked for in review (2026-10-14): a line that curves and bends instead of zigzagging block by block; keeping to the first plan and deciding the next by what it finds there; tracing back from the player's latest known place to find the fastest way; cutting the player off when it can; a heat map of where the players spend their time; and, stuck, another way. Rebuilt from scratch:

- **One set of body rules** (`NavRules`) for where it can be (floor, wall, ceiling, in the largest posture that fits) and how it gets from one place to the next. On the floor it rests at the height its body would (stairs, slabs, carpets) and needs something under at least half its feet, so it never walks along the top of a railing. Nowhere with a drop on both sides counts as floor (the top of a hedge, a railing, a wall); something low in its way (a hedge, a kerb, a fountain's rim) it steps over. It walks in eight directions and keeps a little off walls and the edges of drops. The same rules read the world (for its searches) and the hotel's plan (for its map), so the map never promises a way the body can't take.
- **The hotel's map** (`HotelGraph`), worked out from the plan away from the game's thread in about two seconds, and again whenever a lift car moves or a locked door is opened:
  - **Places:** about 88,000 places its body can be.
  - **Sections:** 379, one for each space of the plan, split wherever it doesn't join up for it.
  - **Doorways:** 1,103 ways from one section into the next, found from its real moves rather than from where the plan draws the openings.
  - **Costs:** what crossing each section costs from one doorway to the next.
- **Traced back from the target** (`HotelGraph.Field`). Every way starts at where it is going (for prey, the latest place it knew them to be) and runs back through the doorways to every corner of the hotel. From wherever Rauk is, the field gives the fastest run of doorways. Working out a way across the whole hotel takes about 3 ms.
- **The next stretch, block by block** (`NavSearch`): up to a few doorways on (about 26 blocks), searched in the world itself, keeping to the sections the route goes through. A search never takes more than 25 ms. If it runs out of time, it walks as far as it got and works out the rest on the way, so it never holds up the game.
- **A smooth line** (`NavPath`):
  - The search's places are pulled straight wherever the body fits all along the line with a little room to spare, so it no longer zigzags across rooms or down the slanted wings.
  - The corners left are rounded off as wide as there is room for, wider the faster it means to go.
  - The line is a point every 0.3 blocks, and the body follows it (5.11).
- **Following it** (`RaukMover`):
  - It heads for a point a little way along the line, further the faster it goes. In a doorway or a low passage it looks only a little way ahead, so it doesn't cut in and catch a door's panel. Bumping into something, it steers straight back onto its line.
  - It speeds up on the straight and brakes in time for bends, narrows and changes of surface.
  - It turns its weight no faster than it can, so it runs in curves.
  - Climbing from surface to surface works as before.
- **Keeping to it:**
  - A place that stays put is worked out once.
  - A moving one (prey) is caught up with every two seconds, every half second when close and in sight, or at once if it jumps far.
  - It works out the next stretch before it reaches the end of the one it has, without stopping.
  - On its way somewhere to look (a noise, a search), it doesn't turn aside for every new idea, only for prey it sees or a loud new noise. There it decides what next by what it finds.
- **Stuck:**
  - After a second without getting anywhere, it works out its way again from where it is.
  - Stuck again, it keeps off the blocks ahead for half a minute.
  - Stuck a third time, it gives up the doorway it was making for and goes another way for a minute.
  - Losing its grip on a climb, or ending up off its line (slipped back down a wall), counts as stuck: it keeps off that climb.
  - Trouble three times in one spot gives up the doorway there.
  - If every way left is one it gave up on, it tries those again rather than stand there.

**Hunting with it** (6.3):

- **Cut-offs** (`CutOff`):
  - **Where the prey can go:** from where it is, every doorway it can reach on foot soon (stairs, doors, ladders, short drops; no walls or shafts).
  - **Which way it's likely going:** each doorway is weighed by how well it lines up with the way the prey is running, how straight on its way it lies, and how warm the space beyond is on the heat map. Going up or down the stairs, the doorways on the floor it is making for count most.
  - **The race:** for the likeliest few, Rauk works out how soon it can be just through the doorway by its own ways (walls, shafts, crawlspaces), never through the space the prey is in. It goes for the likeliest one it can reach first and waits beside the door.
  - **How often:** it does this in most encounters when it is hunting (85%); otherwise it runs them down. Playing (6.3), it stalks instead.
  - **When it loses sight of them running**, it works out the same from where and how it last saw them, allowing for the time since.
- **The heat map** (`HotelHeat`, saved with the world):
  - **Warming:** every second a player spends in a space of the hotel warms it for them (their scent soaks in). The last few minutes count most; their habits of the last hour or two lie underneath.
  - **Looked into:** it also keeps when Rauk last looked into each space.
  - **What it changes:**
    - patrolling, it looks in on warm spaces it hasn't checked for a while far more than the rest;
    - searching, it tries the doors into the rooms the prey has spent its time in first;
    - predicting where someone is going, the warm spaces count with their haunts and bolt-holes.
  - **Test commands:** `/rauk heat` lists the warmest spaces; `/rauk heat warm <pos> <seconds>` warms one.
- **Tested** (2026-10-14; DevShot `nav_moves`, `hunt_hotel`, `heat_patrol`, `nav_film`; `/rauk nav` shows the map and `/rauk nav route <pos>` the way from Rauk to a place):
  - **Routes:** of the 17 test legs through the wings and the old building, 16 or 17 arrive in each run. Before, 3 of the 10 through the wings did. The one that fails now and then is the climb up the west stair tower into the attic (§15).
  - **Hunts:** all six hotel hunt trials caught their prey in two runs, and five in the third, where the one under the flight ran just over its time limit. The cut-off up the service stairs has it waiting at the top.
  - **Heat map:** with the library made warm, it looks in on it again and again, about every minute, with other rooms in between.
  - **Time:** most plans take a tenth of a millisecond (it keeps what it has worked out), 9 in 10 under 25 ms, and a search never more than 25 ms.

---

## 5. Procedural animation

### 5.1 Principles

1. **One skeleton, one mass, one set of joint limits** for every movement. Nothing is ever posed outside what the rig allows.
2. **Planted limbs never slide.** Once a hand or foot is down, it stays fixed in the world until it lifts. More than anything else, this sells weight.
3. **Head first, body follows.** Every turn ripples outward: eyes, head, neck, chest, hips, limbs.
4. **Two dials drive everything:**
   - **Tension** (0 = resting, 0.5 = alert, 1 = committed) sets spring stiffness across the whole rig (about 1.5 Hz relaxed to about 6 Hz committed), how high it carries its body, how far its fingers spread, its breathing rate, and whether it holds its breath. You can read its state from how it moves before it does anything.
   - **Attention** (0 = scanning, 1 = locked on) sets what the head does: quick snapping glances when scanning, smooth tracking when locked on.
5. **Almost everything is procedural.** Attacks and displays are procedural moves toward a target, shaped by a few hand-authored curves. There are no canned animation clips that ignore the environment.

### 5.2 Skeleton (~75 bones)

- **Root and pelvis**
- **Spine:** 2 lumbar and 3 thoracic bones. The back knobs follow these.
- **Ribs:** one breathing bone that expands the chest.
- **Neck:** 5 bones, plus the head.
- **Face:** jaw; listening folds (2 sides × 3 segments); 2 nostrils; 2 upper eyelids; 2 nictitating membranes; tongue (3 bones).
- **Each arm:** shoulder blade (slides and rotates), upper arm, upper-arm twist, forearm, forearm twist, wrist, 4 fingers × 4 segments, thumb × 3.
- **Each leg:** thigh, shin, foot, toe group, big toe × 2.
- **Helper bones:** corrective bones at the elbows, knees, armpits and neck so joints keep their volume when they bend.

### 5.3 Order of work each frame (client)

1. **Body planner:** from the synced state (velocity, surface normal, posture, intent), work out where the root should be and which way it faces.
2. **Gait engine:** decide which limbs step and where they land (5.4).
3. **Limb IK:** a two-bone solve per limb, with the elbow and knee direction set by posture (elbows back or out, knees forward or out). For arms, the shoulder-blade slide is solved first. Hands and feet align to the surface; fingers and toes curl around edges.
4. **Body fit:** the pelvis follows the hind limbs, the chest follows the forelimbs, and the spine curves between them so it drapes over stairs, door frames and corners.
5. **Head and neck:** gaze stabilization (5.6).
6. **Secondary motion:** breathing, blinking, listening folds, nostrils, jaw, finger tension.
7. **Springs:** everything passes through critically damped springs whose stiffness comes from Tension.

### 5.4 Gait engine

- Each limb has a **rest position** relative to the body (set by posture) and a **planted point** in the world.
- **When to step:** when the planted point falls too far behind where the limb should be (its rest position plus velocity × half a stride), the gait pattern allows that limb to move, and enough other limbs stay planted.
- **Footfall patterns:**

| Gait | Pattern | Limbs always down |
|---|---|---|
| Upright walk | Alternating | 1 |
| Quadruped walk and prowl | Lateral sequence (left hind, left front, right hind, right front), like primates | 3 |
| Gallop | Rotary, with airborne phases | 0–2 |
| Wall climb | Diagonal pairs, like a gecko | 2 |
| Ceiling | One limb at a time when slow; diagonal pairs when fast | 3 / 2 |
| Squeeze | Push-pull: arms reach and pull, legs push | 2 |

- **Where a limb lands:** cast onto the surface around the predicted position. Hands prefer **edges**: stair edges, railings, door frames, the edge of a bed. When there's one, it drapes its fingers over it. That's a key visual. If the spot isn't valid it tries nearby ones; if none work, it takes a shorter step.
- **Swing:** the lift height scales with stride length and any obstacle in the way (furniture). Hands reach forward with fingers spread, then curl and press down on contact.
- **The gallop** runs on a fixed cycle rather than step triggers, with IK correcting each foot against the floor.

### 5.5 Weight and momentum

- **Lean when accelerating:** the body tilts by atan(acceleration / gravity), up to 30°. Starting a sprint, it drops low and forward. Stopping, its weight shifts back and its hands plant ahead.
- **Hard stop from a gallop:** front limbs plant ahead and the nails tear at the carpet or scrape the floor (block particles), the hindquarters swing around, the body settles. The head stays locked forward the whole time.
- **Turns:** banked at speed, outer limbs extended. At a corridor corner it **runs up the outside wall** and kicks off it instead of slowing down.
- **Landing:** how far it compresses depends on impact speed, up to 40% of leg length, then it springs back. It lands hands-first on walls and ceilings and feet-first on floors. Landings from more than 4 blocks up shake the camera for players within 8 blocks.
- **Footfalls:** block particles and sound matched to the floor (4.1).
- **Bob:** small at a walk. In the gallop it mostly disappears and the spine flex takes over.

### 5.6 Head and gaze: the signature

- **Stabilization:** within the neck's limits, the head keeps its orientation in the world (eyes on the target, top of the head close to world-up) whatever the body is doing underneath. On a ceiling the body is upside down, and the neck rolls as far as it can (±120°), so the head ends up **tilted sideways**: almost upright, but not quite. That comes from the joint limit, not a scripted pose.
- **Scanning:** the head moves in sharp **snaps** (about 80 ms) between points and holds each for 0.3–1.2 seconds, like a bird.
- **Tracking:** when locked on, it follows smoothly with almost no lag.
- **Startle:** the head snaps to the stimulus instantly. The body follows a moment later, one part at a time.
- **Looking back:** it can turn its head about 150° to look straight behind itself while its body faces away.
- **Peeking:** the long neck lets the head come around a corner or out of a doorway while the body stays hidden: level, still, watching. It might be at floor height, or upside down from the ceiling and tilted.
- **Rising:** when it observes you in a big room, it may slowly rise to its full 8 blocks over 4–6 seconds, the neck extending last, head perfectly still, looking at you. Then it lowers itself out of sight.

### 5.7 Transitions

| From → to | How |
|---|---|
| Upright → all fours | Leans forward, reaches out and plants both hands, spine bends, head stays level. At speed it **falls forward into the gallop** in one long reaching step. |
| All fours → upright | Hind legs step under the body, it pushes its chest up, arms trail and then hang, and the neck straightens last. |
| Floor → wall | The hands climb the wall first, the chest pitches up, the hind limbs follow. The head stays level throughout. |
| Wall → ceiling | The same, pitching over again. |
| Down over an edge | Hands reach over a railing or ledge and grip, then the body **pours over head-first** down the wall. |
| Ceiling → floor | Lets go with the hind limbs first and swings down onto feet and hands, or drops straight onto its target. |
| Leap | Coil (crouch, spine loaded) → launch (hind limbs extend, then front) → flight (arms reach toward the landing surface and legs tuck; the body rotates to match the surface while the arms and neck counter-rotate) → landing (5.5). |
| Opening a door | One long finger hooks the door's edge and pulls it open slowly. The head is already at the gap. |
| Through a doorway | The head and neck go through first, the shoulders collapse with a visible shift and an audible pop, the chest compresses, and the body flows through in a wave before unfolding on the other side. |

### 5.8 Face and secondary motion

- **Blinking:** the nictitating membrane sweeps sideways every 4–10 seconds. It never blinks while locked on. The upper eyelids rarely move.
- **Listening folds:** flat at rest, half-raised when alert, fully open when listening at a door or wall or after a tap. They snap flat when it commits.
- **Nostrils:** flare in rhythm while following scent or sniffing under a door.
- **Jaw:** almost always closed. It opens to pant, bite and feed.
- **Breathing:** slow and deep at rest with the ribs showing, fast when panting, and **held completely when committed**. Breathing that suddenly stops is a warning.
- **Fingers:** loose and curled at rest, spread and hooked when committed.

The skull head (step 1) has no listening folds or nostrils to animate: what it does have is the jaw, the breathing, the eyeshine and the blink, and the neck carries the rest (listening, peeking).

### 5.9 Expression without a face

It has no facial expressions. Its state shows only through Tension, Attention, its height, the listening folds and its breathing. These behave consistently, so experienced players can learn to read them, but the face itself never gives anything away.

### 5.10 Server and client split

- **Server:** AI, movement, physics, the collision box, and rough positions of body parts for hit detection. It syncs a compact state: posture, surface normal, gaze target, Tension, Attention, intent flags, leap target, injury states.
- **Client:** the full rig: IK, gait, springs, face. It can cast against the client world to place limbs, because exact limb positions don't affect gameplay.

### 5.11 Following the way it goes (fix pass, 2026-10-12)

The body no longer turns as one stiff piece. It follows a line down its own middle:

- **The line** is made of two parts. Behind the root, it's where the root has been (a point every fifth of a block, as far back as the tail reaches). Ahead of it, it's where its path goes next (the server sends points 1 to 5 blocks along it, eased on the client). With no path, the line runs straight on the way it faces. Turning on the spot, the part behind swings round to straight a little at a time, so the hind end follows the front round. On a new surface, or in the air, the line starts again straight and the bends come back over a moment.
- **The trunk:** each vertebra from the pelvis to the base of the neck sits on the line where it would be on a straight one, turned along it. Ribs, shoulders and hips go with them. The hands' and feet's places are worked out on the straight body and then carried round onto the bent one. Knees, elbows and feet turn with the hips and chest.
- **The head** is carried along the line ahead, so round a corner it goes first. It swings toward what it looks at from the way it's going, not from the body's facing.
- **The tail** is drawn sideways onto the line behind while moving, so it follows the body round corners instead of cutting across them (along the line it still drags as before).
- **Never through walls:**
  - The skull and the dropped jaw are balls, kept clear of blocks; off a wall they come back toward the line, which is clear because the body is about to go there. If the neck can't get there in time (turning back in a corridor), it bends harder until the skull is out.
  - Every bone of the tail is kept out of blocks, coming back toward the one before it.
  - Hands and feet are never put down in a wall; in a narrow place they come in toward the middle. Elbows and knees fold round to whichever side is clear.
  - Planted claws straighten until they rest on the floor rather than sinking into it.
  - Headroom isn't measured where the face is over a wall (a wall is no ceiling), so the head doesn't duck into it.
- **Down the middle:** the pathfinder gives a small cost to hugging a wall, so in a corridor Rauk walks down the centre.
- **Tested** (DevShot `snake`, `snake_s`, `snake_close`, `snake_corner`, `snake_hotel`): a 3-wide Z and a tight S of glass walls, and the hotel's corridors on 2, each run first the old way and then the new. A check counts the frames in which any part of each region is inside a block.

| Run | Head before → now | Tail before → now |
|---|---|---|
| Z, on all fours | 26% → 1.6% | 41% → 0% |
| Z, upright | 22% → 1.5% | 49% → 0% |
| Tight S | 37% → 0% | 13% → 0% |
| Turning back in the corridor | 25% → 1.6% | 60% → 0% |
| Hotel corridors on 2 | 35% → 0% | 30% → 0.2% |

  Neck and trunk fell the same way, to about 0. In the hotel the hands are at 0 and the feet at 1 to 3%.

### 5.12 The body in three dimensions (2026-10-14)

The line down its middle (5.11) now bends up and down as well as side to side, and the head always leads it:

- **Over a lip, down a stair:** the line ahead goes on across a change of surface (the server sends each point where the middle of the body sits on that surface), and the line behind keeps where the root went, up a wall or down a stair. Climbing out of a hole, the chest bends over the lip as soon as the head gets there, with the hands on the floor and the hips still down in the hole, the hind feet gripping its walls. Only a leap starts the line again.
- **Way round at every point:** each point of the line has its own "up", carried along it without twisting, so over an edge the body's up turns over with it. Hands and feet feel for holds by their own part of the body's up and forward, not the root's.
- **As much as a spine bends:** each point goes its own length on from the one before toward where it was, turning no more than 35° per block side to side and 80° per block over an edge. So the line curves round as tightly as it can and then comes back onto its way, and the body never folds double or kinks. Where the way goes back behind it, it turns round aside first (straight round would go through the floor) and goes up or down only once it faces that way.
- **A small circle to turn round:** where the server's path sets off back the way Rauk is facing and there is room (a circle 4.4 or 3.4 wide of floor clear for its body), it walks round in that circle first and joins the path a few blocks on. Where there is no room it turns on the spot, and the way it came swings round behind it as it sets off.
- **Head first through a door:** the skull never goes where the path is inside a block (a shut door); it waits at the last clear place, and the body follows where the head went.
- **Fitting the feet:** the hips sit at their stance's height over the hind feet, the trunk pitches toward the front feet and rolls toward the higher side, all measured off each limb's own part of the body. A hip stretched toward a foot far out leans toward it rather than pulling the leg straight, and a limb stretched as far as it goes steps.
- **Stairs as a ramp:** where the root has been goes up a stair in sudden half-block steps, so the line's height is evened out over about a block and a half and its length measured again along the ramp. The root's own height on a floor is eased too, so a step up isn't a jump. Without this the hips rocked back and forth at every step.
- **A whole ribcage:** the chest is laid as one piece across the line, from where its back end is on it to where its front end is, so its ribs never fan apart; the bending is done in the waist and the neck. The waist never stretches either: over an edge, where the back is on the outside of the bend, each vertebra is kept within its length of the one in front, and the hips come in after them.
- **Head over a stair:** the room above the face is measured from no lower than what the face is over, so the underside of a stair open beneath doesn't count as a ceiling (it used to press the head down into the steps).
- **Holds:** a hand or foot reaches down at most a step (1.25 blocks) below its spot, so never to the floor under a stair beside it; then round the block edges there, then back along the body (over a lip), then in toward its middle (the walls of a hole). With nothing in reach it hangs tucked under its hip or shoulder.
- **The tail** is drawn across onto the line behind in every direction, so it lies down a stair or a wall after the body. Pushed out of a block, it is not thrown: the push gives it no speed.
- **Tested** (DevShot `anim_turn`, `anim_stairs`, `anim_door` on a pad south of the course; `anim_ledge` up the Cabinet's drain from B2): see `share/media/anim_*.gif`.

### 5.13 Keeping the body out of blocks (in progress, 2026-10-14)

Only Rauk's own body: nothing here makes it solid to players (decided in review, 2026-10-14). Each part of the body is kept out of whatever is solid:

- **The line** the body is laid on: any point of it in a block is moved out by the nearest way to open space (over a lip or a stair, or swung round on the spot, it used to cut through block corners).
- **The trunk:** each vertebra with its ribs, spikes, shoulder blades and hip bones is tested; one in a block is pushed out the nearest way to open space (never on into the next block), the pushes spread along the back, the chest moved as one piece. A push comes at once and goes over a moment, so it doesn't shiver along a wall.
- **The head and neck:** besides the skull's ball, the hands clasped round the skull and every vertebra and spike of the neck are kept out; the skull never swings through a wall to its far side (it only goes as far toward its place as it can from the base of the neck without touching anything).
- **Arms and legs:** knees and elbows fold whichever way keeps the whole limb out, measured along both bones, not just at the joint. The ball of a foot stands off the surface it holds whichever way that faces.
- **Hands:** on a step the hand turns until its fingers are clear of the riser, and fingers still in bend back up off it. Holding the front of a step, a wall or a lip, the palm lies on it with the fingers reaching up it.
- **Feet:** toes and the dewclaw bend back up off a riser or a wall.
- **Squeezing** through a doorway, the ribcage folds in toward the spine.
- **Measured** (DevShot `anim_turn`, `anim_stairs`, `anim_door`, `col_corridor` (a dead-end corridor three wide: in, a startle from behind, out), `col_climb` (up a wall, under a landing and round its edge), `anim_ledge`): the share of frames in which any bone of each part, anywhere along it, is more than 5 cm inside a block (hands and feet on the floor don't count).

| Test | Head | Neck | Trunk | Arms | Legs | Tail |
|---|---|---|---|---|---|---|
| U-turn | 1% → 0% | 0% | 0% | 3% → 5% | 0% | 0% |
| Stairs up | 0% | 0% | 1% → 0% | 58% → 46% | 1% → 0% | 0% |
| Stairs down | 0% | 2% → 1% | 4% → 2% | 24% → 4% | 1% → 0% | 0% |
| Through a door | 3% → 14% | 15% → 11% | 46% → 32% | 24% → 9% | 12% → 5% | 0% |
| Dead-end corridor | 3% → 0% | 4% → 0% | 4% → 1% | 4% → 1% | 0% → 2% | 3% → 5% |
| Ledge (the Cabinet's drain) | 11% → 3% | 59% → 10% | 67% → 13% | 60% → 47% | 23% → 9% | 0% → 3% |
| Under a landing | 3% | 5% | 6% | 22% | 5% | 0% |

  Still to do: the hands on stairs and over a lip (mostly the fingers), the skull's clasping hands and the shoulders squeezing through a door, the tail's spikes.

---

## 6. Behavior and intelligence

### 6.1 How it thinks

```
Senses ─→ Belief (where prey is, how sure it is) ─→ Choose an intent
   │            ↑                                       │
   └─→ Memory (player profile, danger map, scent        ↓
              trails, how its hotel should look)
                                  Tactics (where to be) ─→ 3D surface pathfinding ─→ Movement
```

- **Belief:** for each prey, an estimated position and velocity, a confidence, and when it last sensed them. When it loses contact, it predicts where you were heading (which corridor, which stairwell) and searches there. It acts on this belief, not on your real position, so it can be wrong.
- **Territory:** it knows its hotel: the layout, which doors are usually closed, which lights are usually off. Anything that changes gets investigated: a door left open, a light switched on, something moved or left behind, a puzzle disturbed. You're an intruder, and you leave traces.
- **Intents** are re-scored every few ticks: Rest, Patrol, Investigate, Track, Search Rooms, Stalk, Set Ambush, Probe, Strike, Flank, Reach, Chase, Intercept, Disengage, Grab, Feed.
- **Tactics** score possible positions: on the ceiling above your predicted path, on a wall beside it, beside a door you're about to open, at a corner you have to pass, in darkness, outside your field of view, at the right distance.
- **Pathfinding:** one 3D graph over the block faces it can grip (floors, walls, ceilings), with links for walking, inside and outside corners, drops, leaps and doors; across the hotel, a map of its sections and doorways, traced back from where it is going, and a smooth line to follow (reworked 2026-10-14, see 4.7). It's one pathfinder with **different costs for each intent**:
  - **Stalking** penalizes being visible to you, being in light and walking on noisy floors, so hidden routes along ceilings, walls, carpet and dark corners come out of the search on their own instead of being scripted.
  - **Chasing** only cares about time.
  - **Disengaging** penalizes anywhere you could see it.

### 6.2 Drives

| Drive | Rises with | Effect |
|---|---|---|
| Hunger | Time since its last meal | How willing it is to hunt and how much risk it accepts |
| Caution | Being struck, bright light, being seen | Prefers ambushes and cover |
| Curiosity | Changes in its hotel, unfamiliar sounds | Investigates and probes |
| Satiation | A recent kill | It takes the kill to its larder and rests |

### 6.3 The hunt

Based on how real predators hunt: search → orient → stalk → test → commit → capture → kill.

**Hunt or play** (decided in review, 2026-10-06: "a predator showing no mercy"). Each time it finds you it decides how to go about it:

- **Hunting** (most of the time; more likely the hungrier it is, if you're alone or weak, and always once you've got away from it before): it never stops and stares. It closes in at a run, watched or not, and from about 8 to 14 blocks (further when hungrier) it simply goes. Facing it armed buys you a moment while it goes round you, then it takes you head on. If it misses, it comes straight again.
- **Playing** (now and then, for about half a minute to a minute and a half): it keeps its distance. Watched from afar it holds dead still; it shows itself, or puts only its head round a corner or out of a doorway. Come toward it and it melts away, then turns up where you're going. Turn your back within a leap of it, or get busy, and it strikes. The game ends the moment you strike it, when it gets too hungry, or when it has had enough, and then it hunts.

**Outsmarting you** (`Prediction`): it works out where you're going from the places it has found you most (your haunts), the places you bolted to the last time it lost you (your bolt-holes, which it remembers), the doors ahead and which way you're running. Running from it, it doesn't chase behind you if it can get where you're going first: it cuts you off and is waiting. When it loses you, it goes and lies in wait where you usually are rather than search at random.

| Phase | What it does | What you notice |
|---|---|---|
| **Patrol** | Moves through the hotel, often along walls, ceilings and shafts, almost silently | Distant scratching in the walls; doors you closed now standing open; lights that were on are off |
| **Detect** | Freezes. Head snaps to the source, listening folds lift, nostrils flare. | Breathing you were hearing through a vent suddenly stops |
| **Assess** | Weighs you up: armor, what you're holding, health, hunger (can you sprint?), light, escape routes, whether you're looking at it | Nothing. That's the point. |
| **Track** | Follows your scent trail exactly, head low along the corridor floor | It comes along your own route, behind you |
| **Search rooms** | When it knows you're on a floor but not which room, it goes door to door. Sniffs the gap under the door, presses its head against it, sometimes taps and listens. Then it moves on or goes in. | Knocking on doors down the corridor, getting closer |
| **Stalk** | Hunting, it comes on without stopping; playing, it moves only while you look away and holds still when you look toward it. Gets above you. **Switches lights off** on its way in, and on somewhere else to steer you. | Lights going out down the corridor, and coming on where you didn't expect. Eyeshine at the end of it. Something on the ceiling you didn't look at. |
| **Probe** | Lets you see it briefly to find out what you'll do. In big rooms it shows itself at the edge of the light and may rise to full height. In corridors it peeks: only the head comes around the corner. Running sets off the chase. Only when it is playing. | A head at the corner, level, watching. Then it's gone. |
| **Commit** | Holds its breath, listening folds snap flat, nails go quiet, body coils | Silence. Then the first heavy footfall. |
| **Strike** | Ambush from the ceiling, a wall, a doorway or a side corridor, by leap or gallop | |
| **Chase** | Cuts corners along walls and ceilings. **Leads its target**, heading for where you'll be rather than where you are, or cuts you off at where you're going if it can get there first. Stops when the burst runs out. | It runs at you down the corridor, then simply stops |
| **Intercept** | When a chase fails or it can get there first, it goes silent and moves to where you're heading (6.3, outsmarting you): the stairwell, the exit, the room you've been hiding in, the place you bolted to last time. | It's already there |
| **Capture → kill** | Grab and kill, no escape (7.2) | |

### 6.4 Reading the player

- **Where you're looking:** it knows whether you're looking at it (from your look direction, line of sight and light). Being watched changes the route it takes and how it moves.
- **What you're holding:**
  - A drawn bow or crossbow: nothing hurts it, so it doesn't care.
  - A raised shield: it goes for your side or hooks the shield away.
  - Fire (Fire Aspect, Flame, flint and steel, fire charges): it keeps its distance.
- **What you're busy with:** mining, eating, drawing a bow, standing at a crafting table or chest, facing a wall. All of these are openings, and when it is playing with you, an opening is what it is waiting for.
- **Your condition:** low health (it smells the blood), too hungry to sprint, no armor.
- **Multiplayer:** it singles out the **isolated** player. Corridors and doors make splitting a group easy. It goes for whoever is alone in a room.

### 6.5 Learning (per player, saved, fades slowly)

| What it learns | From | What changes |
|---|---|---|
| You fight back | Encounters where you strike it | It goes from bold to ambush-only and probes more |
| You're a ranged threat | Being shot at | Takes cover, approaches through doors and around corners |
| You rely on light | Light levels where it finds you; lights you turn on | Switches lights off earlier, learns which levers you use |
| Your refuges | Where you've escaped to: vents, water, the rooms you hide in | Cuts you off before you get there, takes another route around, waits where the vent comes out, reaches in |
| Your routes | The corridors and stairwells your scent trail uses most | Sets ambushes on them |
| Your room | The room you keep coming back to | Checks it first. Sometimes it's waiting inside. |
| Your bluffs | Facing it with a weapon but never attacking | Stops respecting the standoff |
| Your startle tricks | Bells (the front desk bell too), goat horns | Startles the first time or two, then **gets used to it** |

### 6.6 Nothing hurts it

It can't be damaged (decided in review, 2026-10-06), so it has nothing to retreat from and no den to heal in; there is no cornered display. What it still does:

- **Fire:** open fire in your hand keeps it at a distance (it backs off and comes another way), and a burn to the face makes it let go.
- **Fake retreat:** if you chase it, it leads you out of sight and circles around to come at you from behind. Chase it, and you become the prey again.
- **Danger map:** it remembers where it has been struck (traps, doorways covered by an archer) and avoids them.
- **It won't take bad fights.** It won't come through a doorway you're aiming at. It waits for a better moment; it has time, and it lives here.
- **Exploit resistance:** it climbs, so towering up doesn't work, and shot at again and again from somewhere it can't reach, it takes cover rather than stand there.

### 6.7 Other creatures

- **Prey:** anything warm-blooded. What else lives in (or wanders into) the hotel is up to the hotel design. Whatever it kills ends up in its larder.
- **Caching:** like a leopard dragging a kill into a tree, it drags kills to specific rooms it uses as larders and feeds there later. Finding a larder is one of the worst things that can happen to you, and one of the best warnings.
- **Undead:** it ignores them because they aren't food, and kills them silently if they get in its way.
- **Anything alive that can flee, flees** when it's near.

---

## 7. Combat

### 7.1 Attacks

| Attack | What it does | Damage *(tune, Normal)* |
|---|---|---|
| **Pounce and pin** | Leaps and pins you under one hand, leading into the grab | 8, then pinned |
| **Grab** | Lifts you (7.2) | — |
| **Ceiling drop** | Drops onto you from above and pins you | 8, then pinned |
| **Doorway ambush** | Waits flat against the wall beside a door and grabs whatever comes through | Leads into the grab |
| **Railing pull** | Reaches up from the floor below, over a gallery railing or stair landing, and pulls you down | Fall damage, then the grab |
| **Reach-in** | One arm into a vent, a window or a gap, up to 4 blocks deep. Drags you out if it catches you. | Leads into the grab |
| **Shield hook** | Hooks a raised shield away and disables it, like an axe does | — |
| **Flank** | Armed prey that faces it isn't attacked head on, not even with a leap: it circles at a run to your side, then your back, and grabs the moment you aren't facing it. If you keep turning to face it, it gives up and comes another way. | Leads into the grab |


The rake (a sideways sweep with one arm) was taken out in review (2026-10-05) and flanking replaced it; the cornered display and its snap went with the decision that nothing hurts it (2026-10-06).

### 7.2 Grab and kill

1. **Grab:** it lifts you toward its head, and your camera is **pulled toward its face**. This is the one time you see it clearly and up close. If it's on a ceiling, it lifts you up into the dark.
2. **Kill:** straight away, the jaw opens fully and it bites your head and neck. The bite ignores armor, and a Totem of Undying doesn't save you.

Once it has you, you die (decided in review, 2026-10-14): hitting it, burning it, anything you do while it holds you does nothing, and it doesn't so much as look round. The struggle and escape that used to be here (three hits to the face, or one burn with flint and steel or a fire charge, to break free) were taken out: being let go for hitting it wasn't believable.

### 7.3 Stats

| | |
|---|---|
| Damage taken | None: blades, arrows, fire, falls and drowning do nothing to it (2026-10-06). Only a command or the void removes it. |
| Fire | Doesn't hurt it, but it keeps away from open fire, and a burn to the face makes it let go. It can swat out small flames like candles and torches with its calloused hands. |

### 7.4 What the player can do

- Sneak. Avoid sprinting, and stay on carpet where you can.
- **After a knock, don't move.**
- In light, stay still. In the dark, keep out of its line of sight.
- Hide deep in a room, away from the door, and break your scent trail first: double back, or walk through water.
- Close doors behind you. It opens them, but you'll hear the door.
- Face it. It prefers your back.
- Use the spaces it can't climb: tiled bathrooms, the marble lobby.
- Use the few vents it can't follow you into. Watch for the arm, and expect it to be waiting where the vent comes out.
- Use fire: it keeps its distance from a flame in your hand.
- Shoot at it when it's exposed: it can't be hurt, but it takes cover.
- Survive the burst, then get through a door and out of sight while it pants.
- When grabbed, hit its face.

---

## 8. Sound

Its sound identity is **silence and physical noise**. It has no voice at all. The tapping is its signature and gets the most sound-design attention: dry, unhurried, placed exactly at the door or wall it's touching.

| Sound | When | What it means |
|---|---|---|
| Scratching on plaster and wood | Climbing walls and ceilings | It's moving nearby. Look up. |
| Nail clicks on tile and marble | Crossing hard floors | It's in the room, or the next one |
| Floorboards creaking | Walking on wood | It isn't bothering to hide |
| **Slow knocks, then silence** | Tap and listen | It's searching for you. Don't move. |
| A door opening somewhere | Moving through the hotel | It's on your floor |
| A lever clicking, a lantern hitting the floor | Switching lights | It's on its way, or steering you |
| Lights flicking off and on | Flushing a room | It's right outside, listening. Don't move. |
| Slow, deep, wet breathing (often through a vent) | Resting or alert | It's close, and calm |
| The breathing stops | Committing | It's about to act |
| Pounding, the floor shaking | Gallop and hard stops | It's committed |
| Joints popping, skin dragging | Squeezing through a doorway | It's coming through |

| Tearing, wet chewing | Feeding in a larder | Something just died |

All of its sounds are positional and should be muffled by walls and floors but not by doors, and carried along vents and shafts. That needs custom handling, because vanilla doesn't muffle sound through blocks.

---

## 9. Gore and visual effects

As graphic as possible, per your answer.

- **Hitting Rauk:** nothing bleeds; the blood on it is never its own. Arrows glance off.
- **Always bloody:** jaws, throat and hands soaked, splashes and runs of it all over, and drops falling from its jaws as it moves (2.12).
- **Killing the player:** close-up of its face, the jaw opens, the bite, a heavy burst of blood, a red flash. Death message: "*[player] was taken by Rauk*".
- **Feeding:** tearing and feeding motion and blood, with the remains (bones, torn leather, meat) left in its larder rooms: in a bathtub, on a bed, behind a closet door.
- **Drag marks:** dragging a kill leaves a smeared blood trail along the corridor floor. This needs custom stain blocks it places that fade over time *(optional)*.
- **Eyeshine:** a cold silver-green, drawn as a glowing layer whose strength depends on how bright the light is at its eyes and how directly the head faces the camera (within about 25°). Blinking hides it.
- **Landings and skids:** block particles and plaster dust. Heavy landings shake the camera nearby.

---

## 10. Model and rendering

**Choice: a vanilla-style cuboid model, built in code.** A smooth sculpted mesh looked too smooth in game, so Rauk is made of about 600 rigid cuboid parts (vanilla `ModelPart`s), shaped after two reference drawings: the skeletal stalker (body and pose) and the skull gripped by bony hands (head).

| | |
|---|---|
| **Model** | `RaukRig` describes the body as a skeleton of parts: one continuous chain of vertebrae from the skull to the tail tip (neck, hump, waist and tail flow as one curve), ribs caging a dark body cavity, pelvis, long arms with hooked fingers, digitigrade legs with clawed toes, a cracked human skull with a dropped jaw, two bony hands gripping it from behind, and strings of bloody drool. Every part is a joint the procedural animation can turn. |
| **Animation** | Rigid parts rotated per frame by the procedural rig (section 5); the chains (neck, spine, tail, fingers, ribs) bend smoothly because they have many short segments. |
| **Texture** | **64 pixels per block** (four times vanilla): the model is built at 64 units per block and the renderer scales it down by 4. `RaukTexturePainter` paints the 1024-wide texture from the same layout: pale bone and sinew with plaster-like mottling, occlusion in the creases, ink-style hatching in shadow, cracks across the cranium, black sockets, yellowed teeth, blood around the mouth and claws. It is painted soaked in its kills' blood: jaws, throat and hands, and splashes and runs all over. Run `gradlew paintRaukTexture` after changing the shape; it also paints the eyeshine overlay from the same layout (only the eyes, small black points deep in the sockets). |
| **Shading** | Vanilla entity lighting, but light is sampled at several bones instead of only the root. A hand reaching into lamplight is lit while the rest of the body stays dark. |
| **Hit detection** | Separate hitboxes for the head, torso, each arm and each leg, placed from the server's rough pose, like the Ender Dragon's parts. |
| **Collision box** | Depends on posture (2.2). When it clings to walls and ceilings, the renderer offsets and rotates the body relative to the box. |
| **Pipeline** | All in the mod's code, no external modeling tool: `RaukRig` → `RaukModel` (game) and `RaukTexturePainter` (texture). `gradlew runDevShot` photographs Rauk in a test world for review. |
| **Performance** | There is only ever one Rauk, so a heavy rig is affordable. The AI and pathfinding still run on a schedule, with pathfinding on a separate thread using a snapshot of the area. |

---

## 11. Reading Rauk: the warning signs

| You notice | It means |
|---|---|
| A door you closed is standing open | It has been through here |
| Lights going out down a corridor | It's on its way to you |
| Lights coming on somewhere you didn't switch them on | It wants you to go there |
| The lights in your room flicking off and on | It's right outside, listening |
| Knocking on doors, getting closer | It's searching your floor. After a knock, don't move. |
| Breathing through a vent | It's close, maybe on another floor |
| Silver-green pinpoints at the end of the corridor | It's looking at you |
| A head at the corner, level and still | It's watching to see what you do |
| Listening folds open | It's listening, so don't move |
| Breathing you'd been hearing stops | It has made up its mind |
| Footsteps you can suddenly hear | It's charging |
| Panting, mouth open | Its burst is spent. Get through a door now. |
| A fresh trail of drops | It passed this way, not long ago |
| It stands up to its full height | You're in a big room, and it wants you to see it |

---

## 12. What it needs from the hotel

The hotel gets its own design pass later. These are the spaces the creature's behavior assumes, so the structure should include them.

| Space | How Rauk uses it |
|---|---|
| **Long corridors** (about 3 high, 2–3 wide) | Gallop runs, ceiling stalking, door-by-door searches, peeking around corners |
| **Lobby or atrium with gallery floors** | The only place it stands at full height. Leaps between floors, railing pulls. Columns to stand among. Marble floor it can't climb. |
| **Ballroom or dining hall** | Open space for upright stalking and the rise |
| **Stairwells** | Climbing the open middle between floors |
| **Elevator shafts** | Its main way between floors, up the walls and cables |
| **Service corridors and 2-high crawlspaces** | Its private network. You hear it moving above the ceiling. |
| **A few vents (1×1)** | Player-only routes. It listens at the grilles, reaches in, and learns where they come out. |
| **Guest rooms with doors** | Hiding spots for the player, search targets for it |
| **Tiled bathrooms, kitchen** | Surfaces it can't climb |
| **Lever-controlled lights, lamps, candles** | What it switches off and on to hide, steer and flush |
| **Larder rooms** | Where it caches kills |
| **A den** | Where it rests when fed |
| **Water** (pool, flooded basement) | Breaks scent trails |

### Notes for the hotel pass (from review)

- **One hotel, one Rauk.** You get into the hotel from the Overworld somehow, and then you're trapped. The only way out is to escape.
- **Escaping** takes 3–5 puzzles or tasks, tied to a story about what happened in the hotel, told through the building itself.
- **Lighting:** a balance of lit and dark areas. If the player gets a light to carry, the hotel as a whole gets darker.
- **No permanent refuges:** no rooms that lock it out, and only a few vents.
- **To decide then:** how you get in; what happens when you die; whether players can place or break blocks inside; how the puzzles and Rauk interact (puzzles keep you busy, noisy and in one place).

---

## 13. Build order

**Process:** one step at a time. Each step is finished, polished and working before the next one starts, and you review it before we move on. We playtest in-game whenever a change is big enough to need it, and always for behavior.


1. **Model and renderer:** the cuboid model and its texture, in the reference pose in-game.
2. **Procedural rig on flat ground:** IK, the gait engine, blending between upright and all fours, Tension and Attention.
3. **Moving on every surface:** wall and ceiling physics, posture-dependent collision boxes, leaps, doorways, the 3D surface pathfinder.
4. **Senses and memory**, including territory memory. Built in `entity/mind/`: `RaukMind` (hearing from game events, listening, tap and listen, vision by light and movement, smell), `Belief`, `ScentData` (trails saved with the world), `Territory`, `PlayerProfile`. `/rauk debug` draws its mind in the world.
5. **The hunt:** intents, tactics, room search, stalking, chasing, intercepting. Built in `entity/hunt/`: `RaukBrain` (intents re-scored twice a second, drives), `HuntCosts` (stalk, quiet and retreat path costs), `PreyRead` (6.4), `Lights` (lever learning, lights off to hide, on to steer, flicked to flush). Gallop stamina (4.2) lives on the entity. Creatures tagged `rauk_prey` count as prey (for testing and for the hotel's other animals).
6. **Combat:** attacks, grab and struggle. Built in `entity/combat/` (`RaukCombat`: pounce and pin, shield hook, grab, struggle, bite, where a blow lands by `Region`; `RaukDamage`: the bite and grip damage types) and the brain's GRAB intent. (Its own injuries, retreat, healing and cornered display were built here and taken out in step 7 when it was decided nothing hurts it.) Player swings are tested on the client against the posed body (`RaukHitTest`, sent as `RaukHitPayload`); arrows can hit the skull (`ProjectileUtilMixin`).
7. **Sound, gore and polish**, and everything on the fix-later list. Behavior: the rake is gone (FLANK instead), REACH (vent reach-in, railing pull), FEED (drag to a larder, feed, leave remains; larders saved), the doorway ambush, peeking round corners and out of doorways in corridors, no grabbing round door frames (reach needs a clear line), animals fleeing it (`OtherCreatures`) and the undead killed silently when in its way, water (it wades, never swims), and the body lowering before a doorway (`postureAhead` from the mover's path; the skull ducks under lintels). In review it was decided that nothing hurts it: its injuries, retreat, den recovery, healing, stuck arrows, wound overlays and the cornered display with its hiss were taken out, and it is painted blood-soaked instead. Gore: `rauk:blood` stain blocks (drops, smears, pools; they dry and fade), its drip trail, the carried and eaten kill (`CarcassRenderer`), eyeshine and blinking (`RaukEyesLayer`), landing dust, the red flash and camera shake (`RaukEffects`, `GameRendererMixin`). Sound: `RaukSounds` (events, sounds.json built from vanilla samples) played on the client by `RaukSoundClient`, which carries sound through open air, doors and vents and muffles it through walls; footsteps, scratching, breathing, pops and skids come from the animation, which keeps running off screen.
8. **The hotel**: its own design, in [hotel-design.md](hotel-design.md).

A debug overlay comes first and grows with each step: belief positions, scent trails, the surface graph, planned paths, limb targets, Tension and Attention. Most of the tuning will happen through it. Until the hotel exists, a small test building with a corridor, a few rooms, a stairwell and a tall room is enough to develop against.

---

## 14. Decisions from review

| Question | Decision |
|---|---|
| Hotel lighting | A balance of lit and dark areas. If a light you can carry is added, the hotel gets darker. |
| Leaving the hotel | Rauk never leaves it. Neither can the player, until they escape. |
| Light switches | It switches lights off and on to hide, steer and disorient (4.6). More mechanics can come later. |
| Tap and listen | Kept. It's one of its key features, so the sound has to be executed well (8). |
| Refuges | No room it can't get into. Only a few vents. |
| Totem of Undying | Doesn't save you |
| Damage to Rauk | None (2026-10-06): nothing hurts it. It is always bloody instead. |
| Cornered display | Removed (2026-10-06), with its hiss |
| Texture density | 64 pixels per block (10) |
| Variation | Exactly one Rauk, always this individual |
| Playtesting | Whenever a change is big enough, and always for behavior |

---

## 15. Fix later (polish list)

Things noticed in review that were deliberately left for a polish pass, so progress could continue. Step 7 worked through the list; what is left open is marked so.

| Found | Issue | Status |
|---|---|---|
| Step 3 review (2026-10-05) | Ducking into a squeeze or crawl: the neck and body clipped through the blocks above before the body lowered. | **Done (step 7).** The mover syncs the tightest posture on the next few blocks of its path; the body starts lowering for it early and lowers faster than it rises, and the skull is kept under the measured ceiling. |
| Step 1 review | The skull may get another pass later (second reference: gaping jaw, fangs, cracks). | **Open**, only if you ask for it. |
| Step 5 (hunt) | Probing in corridors should be a peek. | **Done (step 7).** In anything under 6 high it finds a corner the prey can't see round and puts only its head out. |
| Step 5 (hunt) | A raised shield was read but not reacted to. | **Done (step 6):** the shield hook. |
| Step 5 (hunt) | Contact could count through a doorway. | **Done (step 7).** Reach needs a clear line from its eyes or chest to the prey. |
| Step 5 (hunt) | Other creatures (6.7): undead ignored, small animals flee, larders. | **Done (step 7).** Animals and wandering traders keep away from it once they see it; undead it doesn't listen to, and one in its way it kills silently; kills go to larders. |
| Step 6 (combat) | Wounds and stuck arrows weren't drawn; the blood trail was particles only. | **Superseded (step 7).** Built, then taken out when nothing hurts it; it is painted blood-soaked instead and leaves a drip trail of stains that dry and fade. |
| Step 6 (combat) | Placeholder sounds. | **Done (step 7).** Its own sound events (built from vanilla samples), muffled through walls, not doors. |
| Step 6 (combat) | Railing pull, reach-in through vents, waiting beside a door. | **Done (step 7)** as general behavior (REACH, the doorway ambush); the hotel decides where vents and galleries are. |
| Step 6 (combat) | Dragging the kill to a larder and feeding there. | **Done (step 7).** Larders are chosen through a door, saved, and reused; the remains never despawn. The hotel can later mark preferred larder spots (a bathtub, a bed, behind a closet door). |
| Step 6 (combat) | Retreating, it didn't double back; cornered, it didn't try to slip past. | **Superseded (step 7).** Retreat and the cornered display were taken out with damage. |
| Step 6 review (2026-10-05) | Remove the rake attack (your call). | **Done (step 7).** Replaced by flanking armed prey that faces it (7.1). |
| Step 7 | Water (4.5) wasn't in yet. | **Done (step 7).** It wades up to five blocks deep and never swims. |
| Step 7 review (2026-10-06) | Whether Rauk can be damaged; the cornered display and hiss. | **Decided:** nothing hurts it, it is always bloody instead, and the cornered display and hiss are gone. |
| Step 7 review (2026-10-06) | Close to its prey it stopped and stared, then backed off. It should hunt without mercy, predict where you go, and only play with you when it wants to. | **Done (step 7).** Hunt or play per encounter, prediction from haunts, bolt-holes, doors and heading, cut-offs and lying in wait (6.3). |
| Step 7 review (2026-10-06) | The back still clipped through ceilings when ducking or squeezing; the grabbing arm looked stretched and stiff; the tail tip stayed curled toward the body when it walked off. | **Done (step 7).** The body measures the ceiling over its back and sinks, levels and straightens until the hump fits, and knees and elbows fold whichever way keeps them inside; the prey is held closer and the gripping arm stays bent, elbow out, working against the struggle; moving, the loose end of the tail lets go of its resting curl and drags behind. |
| Hotel step 2 (2026-10-06) | Multiplayer: once the coal chute falls in, a second player can't get into the hotel until everyone inside has left. | **Done (2026-10-06).** A late arrival stepping onto the rubble at the top of the chute goes down with it into the coal store; the chute stays full, so it's a way in only. Anyone who dies inside wakes in the staff dormitory instead of at the world spawn. |
| Hotel step 2 (2026-10-06) | Ways out of a sealed hotel that adventure mode doesn't stop: chorus fruit teleports, and explosions from outside (creepers, TNT) breaking the shell. | **Open**: block chorus teleports inside and keep explosions off the hotel's blocks. |
| Hotel step 2 (2026-10-06) | A world made with "Generate Structures" off gets no hotel (vanilla skips all structures then). | **Open, your call**: leave it, or always generate the hotel. |
| Hotel step 2 (2026-10-06) | Rauk crosses big rooms and climbs between floors well, but the atrium's open middle is still only crossable by the stairs and lifts: it doesn't yet leap between galleries or climb the columns (there are no columns yet). | **Open**: columns in dressing (step 4), and leaps in its routes. |
| Hotel step 4 (2026-10-06) | Furniture broken in survival (outside the hotel) drops nothing yet. | **Open**: give the pieces loot tables. |
| Hotel step 4 (2026-10-06) | Chairs and sofas can't be sat in. | **Open**: seats you can sit in, if wanted. |
| Hotel step 4 (2026-10-06) | Candles light again only with flint and steel or a fire charge. | **Open**: matches, with the things to carry (4d). |
| Hotel step 4 (2026-10-06) | The wall between the penthouse drawing room and its lift lobby shows blue stucco on its inside, as if it were outside. | **Open**: a skinning slip from the revamp (2b). |
| Playtest (2026-10-06) | It stood about too much: didn't see a still player right in front of it in a lit room, played and waited too often, crept in too slowly, stalked a ceiling it couldn't reach, listened at door after door; it grabbed from too far and through the steps of a stair; it ignored players walking up to it while it fed; its routes went up the guest lift's cage, which it can't climb. | **Done.** Sees stillness at 12–16 straight ahead; mostly hunts and comes on faster than a walk; gives up on anything it can't get to after 2.5 seconds; a short search; reach 2.4 from its face with room for an arm; feeding, it drops the kill for anyone close; the caged shaft is no route between floors. Also: it may start anywhere in the hotel, and smaller, brighter pupils. |
| Playtest (2026-10-06) | Service stairs, 1 to 2 (the missing flight): climbing up the door wall, under the landing above and round its edge, it lets go and falls back, two or three times, before it goes up the side wall instead. | **Done (2026-10-07).** It takes hold of each surface on the way (it used to skip the stretch under the landing), only once the very block it holds is under its hands; between two surfaces it hangs on a moment; a way round an edge its body can't swing through (a stair beside it) isn't taken; it lines up exactly at a lip. Up from 1 to 2 in six seconds. |
| Atmosphere (2026-10-07) | With the brightness setting ignored, the hotel with the power off is black but for its few flames: there's nothing to carry a light with yet. | **Open**: the flashlight (4d) makes it playable as designed. Until then: `/rauk hotel power on`, or night vision. |
| Atmosphere (2026-10-07) | 6.4 wants a ticking clock in the cozy rooms, but every clock in the hotel is stopped (the stopped clock is puzzle 5). | **Open, your call**: left silent; one clock that still ticks somewhere would be easy to add. |
| Fix pass (2026-10-12) | Its head and tail stuck out of the walls, which made it far less frightening; it turned stiffly, head and tail swinging round with the body. | **Done** (5.11). The body follows its path: head first round a corner, then the trunk, then the tail, in an S where the way bends back and forth; head, neck, trunk and tail are kept out of walls, hands and feet too; it walks down the middle of a corridor. |
| Fix pass (2026-10-12) | Turning right round in a corridor 3 wide (as long as it is, it can't really), the skull still touches a wall in about 1 frame in 60. Feet are in a wall in 1 to 3% of frames. | **Open, minor.** |
| Fix pass (2026-10-12) | The hotel hunt trials vary from run to run: under the upper flight of the service stairs ("below"), it sometimes climbs under the flight, loses the figure on it, and lies in wait for 20 seconds instead of going up and round; "stairs" and "cutoff" time out now and then too. The same route is taken in runs that catch and runs that don't: what differs is the brain's choice afterwards. | **Done (4.7, 2026-10-14).** With the new pathfinding all six hotel hunt trials catch their prey (stairs, under the flight, the atrium, the intruder, the cut-off up the service stairs, the gallery), twice running. |
| Second playtest (2026-10-07) | Its routes were odd: it should find the best way quickly and, when that way turns out blocked, another; it couldn't get out of the spa's pool; it should cut players off, up the lift shaft while they take the stairs, and wait for them at the top. | **Done.** It works out the whole way across the hotel and follows it (no more turning back and forth between two doors); the map follows the lift cars as they move; an opening it can't reach, or gets stuck on twice, it keeps out of for a minute and goes another way at once. In water it climbs any wall, and the pool is joined to the spa. It reckons where someone running is going (through a stairwell's door: out at the next floor), compares their way on foot with its own by any shaft or wall, never through their stairwell, and if it's there first, or near enough, it waits beside that door. Seeing them vanish through a stairwell's door, it goes round the same way. |
| Hotel reshape (2026-10-12) | Its routes through the new wings: of ten test legs (DevShot `wing_moves`) three pass (atrium to the rotunda, up the round stair, down to the servants' tunnel). It climbs into the one-wide dumbwaiter and laundry chute and sticks against their hatches; from the rotunda gallery on 3 it plans along the top of the well's railing and a seven-block drop instead of the stair; at the foot of a stair it can stall under the step it is making for (a fix for that is in, not yet tried). | **Done (4.7, 2026-10-14).** Pathfinding rebuilt from scratch: all 17 test legs pass (the ten through the wings and seven through the old building). Squeezing up a one-wide shaft (the dumbwaiter, the laundry chute) costs it so much that it goes round, and where it sticks it now goes another way; railing tops aren't floor; the stairs are walked at the height its body rests at. The rotunda lift's brass cage now joins up (it stood as loose posts, with gaps a body or a player could slip through). |
| Puzzle groundwork (2026-10-12) | The flashlight's beam doesn't show on the walls and floors. The blocks it falls on are worked out (about 130 down a corridor) and the game's light is raised there, but the parts of the world they're in are never drawn again, so only things drawn live (doors, Rauk, items) light up in it. Eyes adjusting to it (the dark round it darker) is left out until the beam works. The item, picking it up, and Rauk seeing it (48 blocks, 64 with the beam in its face) all work. | **Open, set aside (your call).** |
| Pathfinding rework (2026-10-14) | Some climbs still fail now and then: over the top of the west stair tower into the attic, it lets go and slips back. It also squeezes only slowly up the rotunda's round stair. Each time it goes another way, so it gets there, but late: in the test leg up to the attic's west wing it runs out of time. | **Open, minor:** the climbing changeovers at those two places. |
| Animation review (2026-10-14) | Climbing a ledge (up the Cabinet's drain from B2) its upper legs floated; turning sharply or back, the torso broke and split; the head went through a door before it was open; arms and legs stretched straight rather than the body leaning; on stairs it put feet on the floor three blocks down beside them. | **Done** (5.12). The line down its middle is three-dimensional and head-led; the chest bends over a lip with the hands on the floor; it turns round in a small circle where there is room, the body flowing after; the skull waits at a shut door; hips lean toward far feet; holds are never more than a step below. |
| Animation review (2026-10-14) | On stairs the head goes through the steps, and the body jumps back and forth. Climbing up onto the ledge, the ribcage gets sliced apart, the pieces floating up to the rest of the body one after another. | **Done** (5.12). Stairs are a ramp to the body (the line's height evened out, the root's height eased); the ribcage is one piece and the waist never stretches; the head is no longer pressed down into the steps by the stair's underside. Any part still touching a step is for collision. |
| Animation review (2026-10-14) | Turned right round at the end of a narrow landing (no room for the circle), the body knots for a few frames before it sets off down; at the top of a climb it waits a second or two, chest over the lip, before the server lets it get over. | **Open, minor.** |
| Animation review (2026-10-14) | Hitting it while it held a player made it put them down, and they got away; that wasn't believable. | **Done** (7.2). A catch is always a kill: it lifts and bites at once, and no hit or burn makes it let go or even look round. |
