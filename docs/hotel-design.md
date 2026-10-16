# The Raukvik Grand: hotel design

**Draft 0.2: design approved, building one part at a time** · 2026-10-06 · Minecraft 1.21.1 / Fabric · The building Rauk lives in, the story it tells, and the way out. The creature itself is in [rauk-design.md](rauk-design.md); its section 12 lists what the hotel has to give it.

> **Changes in 0.2** (decisions from the first review): the hotel stands in the **Overworld**, one per world, 1,500 to 3,500 blocks from spawn, not in a dimension of its own. **No death screen**: you wake in the staff dormitory after a short minigame. **No placing or breaking blocks** inside. **Light and ambience** get their own section (6): dark corridors, dim lights, only the big spaces properly lit. **The waltz** draws Rauk in to look for you (4.3).

---

## 1. At a glance

A grand seaside hotel on a limestone coast, somewhere out in your world, sealed since the night of its 1931 Midsummer gala. The shutters came down that night and never went up again. You get in. They come down behind you. The only way out is to lift the lockdown, and to do that you have to piece together what happened here, floor by floor, while the thing that happened is still here.

- **Seven levels**: two basements, the lobby floor, two guest floors, the founder's penthouse, and the roof.
- **Two elevators** (the brass guest lift in the atrium and the service lift in the staff core) and **two stairways** (the grand staircase and the service stairs), each broken somewhere, so you need all of them.
- **Cozy, run-down and ruined** parts, so the hotel isn't one long grey corridor: warm lamplit rooms that feel safe and aren't, worn-out staff areas, and a burned wing, a flooded basement and a ballroom frozen in the middle of the gala.
- **Five puzzles and a finale**, each in a different part of the hotel, each telling part of the story, and each loud, slow or stationary enough to give Rauk its chance.

### Design pillars

1. **The building tells the story.** No cutscenes, no narrator. Notes, ledgers and photographs, plus rooms left exactly as they were when it happened.
2. **Every puzzle is a risk.** Puzzles make noise, keep you in one place, or have you looking at something other than the corridor. That is when Rauk comes.
3. **Rauk's territory first.** The layout is built for the creature's behavior (corridors to gallop, shafts to climb, ceilings to stalk, doors to search), not dressed around it afterwards.
4. **Variety you can read.** You always know which part of the hotel you're in by its light, its colors and its sound.

---

## 2. The story

### The Raukvik Grand

Built in 1911 on the cliffs above Raukvik bay by **Holger Wennberg**, who made his fortune quarrying the island's limestone. The bay is named after the raukar, the tall pale sea stacks off the shore. The hotel was the finest on the coast: a marble atrium three floors high under a glass roof, a ballroom, a seawater spa in the basement, and electric light and lifts long before the rest of the island had either.

Wennberg was also a collector. Under the hotel, behind the spa, he kept what the staff called **the Cabinet**: a private menagerie of things he bought, traded for and dug up. Most of it was stuffed. One thing wasn't.

### The gala

On Midsummer's Eve 1931, the hotel's twentieth anniversary, Wennberg held a gala for two hundred guests. At 23:47, something got out of the Cabinet.

Wennberg had prepared for that night for years. At the first scream he threw the **lockdown**: iron shutters over every window and door, all at once. It kept his specimen inside. It also kept everyone else in with it.

What happened next is told by what the staff and guests left behind:

- **The night clerk** kept the ledger going. Guests "checked out" one by one, in neat handwriting that gets worse.
- **The chef** held the kitchen with lamp oil and fire. The east wing burned. *(Rauk's burn scar, right forearm.)*
- **The night porter** shut the cold-room door on its hand and heard the finger break. *(Its crooked left ring finger.)*
- **The lift boy** was caught in the brass lift with it, and the scissor gate tore across its chest. *(The three parallel scars.)*
- **The bandleader** kept the band playing, because while the music played it stood still in the dark at the edge of the ballroom, and listened.
- **Wennberg** took the lockdown key up to his penthouse and never came down. His last journal entries are the closest thing to an explanation the hotel has.

The lockdown never lifted. Within a year the hotel was a story the island told children. The shutters are still down.

### You

You come across the Raukvik Grand: a shuttered hotel on a cliff above the sea, a long way from anywhere you've been. Every door and window is behind iron. There is one way in, the old coal chute round the side by the kitchen yard: a hatch in the ground and a ladder down. You climb in. It gives way behind you, and the lockdown that has held for ninety years now holds you too.

The creature has never left. It knows every corridor, shaft and room. It doesn't know you yet.

### The goal

**Lift the lockdown and walk out of the front doors.** To do that you need three things from the founder's own system, all hidden around the hotel:

1. **Power**: the hotel has been dark for ninety years. The lockdown, the lifts and the lights all need the generator running.
2. **The lockdown key**: Wennberg took it to the penthouse, which only the lifts reach.
3. **The release code**: written down only in pieces, by the people who tried to get out.

Each puzzle gets you one step closer, and each one tells part of what happened (4).

---

## 3. The building

### 3.1 Levels

The hotel is built to Minecraft scale for Rauk: corridors 3 high (so it moves on all fours and can stalk the ceiling) with its 2-high crawlspace above them, rooms 4 high, the grand rooms 6, and a few tall spaces where it can stand and rise (the atrium, the ballroom). Each floor is 7 blocks from floor to floor, so the crawlspaces fit between the corridor ceilings and the floor above. The floor plans are drawn from the code into [hotel/plans.png](hotel/plans.png) (`./gradlew drawHotelPlans`).

| Level | Name | What's there | Feel |
|---|---|---|---|
| **R** | Roof | Roof terrace, the atrium's glass roof, Wennberg's glasshouse observatory, the lift machine room | Wind, rain, the sea below. Exposed. |
| **4** | Penthouse | Wennberg's private suite, study and the lockdown control room. Reached only by the service lift with his key, or a ladder from the roof | Preserved, silent, untouched for ninety years. Uncanny. |
| **3** | Upper guest floor | The burned east wing, collapsed rooms and holes through to the floor below, the bandleader's room, larders | Ruined and charred, open to the floors above and below |
| **2** | Lower guest floor | Intact carpeted corridors, suites still made up, the **library and reading room** with its fireplace, the atrium gallery | The cozy floor: warm lamps, carpet, quiet. The most dangerous kind of safe. |
| **1** | Lobby floor | The **atrium** (three floors high, marble, galleries on 2 and 3), front doors, reception and the night clerk's office, the bar, the dining room, the **ballroom**, the kitchen | Grand, cold marble, echoing; the gala left as it was |
| **B1** | Service level | Staff quarters, laundry, cold rooms, wine cellar, the **spa** (seawater pool, half flooded) and behind it **the Cabinet**, Rauk's den | Worn, wet, dim; the staff's world |
| **B2** | Machinery | The generator and boiler room, the flooded drainage tunnels, the lift pits | Industrial, flooded, loud once the generator runs |

### 3.2 Getting between levels

Every route is broken somewhere, so you have to use all of them, and Rauk uses all of them better than you.

| Route | Serves | Broken how | Rauk |
|---|---|---|---|
| **Grand staircase** (atrium) | 1 ↔ 2 | Collapsed between 2 and 3 in the fire | Climbs the atrium walls and leaps between galleries |
| **Service stairs** (staff core) | B2 ↔ 3 | Flooded below B1; a flight missing between 1 and 2 | Climbs the open middle of the stairwell |
| **Guest lift** (brass cage in the atrium) | 1 ↔ 3 | Needs power. The cage is open: you can see out, and in. | Climbs its shaft and cables; can be on the cage roof |
| **Service lift** (enclosed, staff core) | B2 ↔ 3, and 4 with Wennberg's key | Needs power and its crank handle | Its main highway between floors |
| **Vents** (a few, 1×1) | Short cuts between rooms and floors | Player only | Listens at the grilles, reaches in, learns where they come out |
| **Crawlspaces** (2 high) | Above the corridors, between floors | Rauk's network | You hear it moving above the ceiling |
| **Holes** in the burned wing | 3 → 2 → 1 | One-way drops for you | Drops straight through onto you |

**The lifts** work like this: you step in, close the gate, press a floor, and the car rides with sound, shudder and the floor numbers going past; the doors open on the other floor. The ride is the slowest few seconds in the game, in a box with one way out. The shafts beside the cars are real and open, and Rauk climbs them; once, the service lift stops between floors with something on its roof. (How the ride is made is in 7.)

### 3.3 Areas and their mood

The same building in three states, so it never looks the same twice:

**Cozy (intact, warm, safe-looking):** the library and reading room (fireplace, armchairs, green lamps, carpet), the bar (amber light, a gramophone, bottles), the made-up suites on floor 2, the glasshouse observatory. These are where you want to hide. Rauk finds them anyway: the lamps it turns off are always the ones you were using.

**Run-down (lived in, then left):** staff quarters, the laundry, the kitchen, the night clerk's office, floor 2's back corridors. Peeling wallpaper, water stains, worn carpet, cobwebs, broken furniture, dim and flickering.

**Ruined (where it happened):** the burned east wing (charred wood, ash, holes between floors), the ballroom (tables still laid for the gala, a fallen chandelier, dust), the half-flooded spa, the Cabinet (cages bent open from the inside), the larders (what is left of its kills).

| | Cozy | Run-down | Ruined |
|---|---|---|---|
| **Light** | Warm, lamps and fireplaces, on levers | Dim, a few bulbs, flickering | Dark; only what you bring, moonlight through holes |
| **Floor** | Carpet: you're quiet, so is it | Wood: creaks for both of you | Marble, tile, ash, water: its nails click, you splash |
| **Palette** | Dark wood, green, brass, cream | Faded paint, stained plaster, pine | Charcoal, rust, grey stone, algae |
| **For Rauk** | Ceilings to stalk, doors to search | Its everyday routes | Its den, larders and tall spaces |

### 3.4 What Rauk gets (from its design, section 12)

Every space the creature needs is placed for it:

- **Long corridors** on floors 2 and 3 and in the service level: its gallop runs, ceiling stalking, door-to-door searches and corridor peeks.
- **The atrium**: the only place it stands to full height and rises; leaps between galleries, railing pulls. **Marble** walls and floor it can't climb, so it uses the columns and galleries instead.
- **The ballroom**: open space for upright stalking.
- **Stairwells and both lift shafts**: its ways between floors.
- **Crawlspaces** above the corridors: its private network.
- **A few vents**: your routes, which it learns.
- **Tiled bathrooms, the kitchen's metal, the spa's tiles**: rooms it can't climb.
- **Lever-switched lights** throughout once the power is on, lanterns and candles before.
- **Larders**: a bathtub on floor 3, a bed in a suite, behind the cold-room door.
- **Its den**: the Cabinet, behind the spa.
- **Water**: the spa pool and the flooded B2 tunnels break your scent trail.

---

## 4. Puzzles

Five puzzles, then the finale. They can be done partly in parallel, but the lockdown needs all three of power, key and code. Each one takes you somewhere new, tells part of the story, and is dangerous in its own way.

| # | Puzzle | Where | You get | Why it's dangerous | Story |
|---|---|---|---|---|---|
| 1 | **The Night Ledger** | Reception, the night clerk's office (1) | The safe's contents: the **service lift crank** and the staff keyring | Reading in the open lobby, back to the atrium | The guest ledger: who "checked out", and when |
| 2 | **The Generator** | Boiler room (B2) | **Power**: lights, both lifts, the lockdown panel | Three fuses from three dangerous places, then the loudest thing in the hotel | The engineer's log: what the power was really for |
| 3 | **The Last Waltz** | Ballroom (1) | The bandstand compartment: **Wennberg's study key** | You have to play it, loudly, alone in the ballroom | The bandleader's letter: why the band kept playing |
| 4 | **The Collector's Shelves** | Library (2) | Wennberg's hidden study: the **first half of the code** and the **penthouse key** | Long, quiet and absorbing, in the cozy room | Wennberg's catalogue of the Cabinet |
| 5 | **The Stopped Clock** | Penthouse (4) | The lockdown control room: the **lockdown key** and the **second half of the code** | Up on the top floor, reached by one lift, with one way down | Wennberg's last journal |
| — | **The Lockdown** (finale) | Security office behind reception (1) | The shutters rise. The front doors. | The alarm wakes the whole hotel | — |

### 4.1 The Night Ledger (reception)

The safe behind the front desk has a four-dial combination. The night clerk's ledger lists the guests who "checked out" on gala night, with room numbers and times. A note in the clerk's hand: *"If anyone finds this: the safe is the order they went, first to last, the last digit of each room."* Read the ledger, put the four in order of time, dial it in.

- **Inside:** the service lift's crank handle (the lift won't run without it), the staff keyring (opens staff-only doors), and the clerk's last entry.
- **Danger:** the desk faces the atrium; the dials click; you're reading. The first puzzle teaches you to keep looking up.

### 4.2 The Generator (boiler room, B2)

The hotel's generator still works but its fuse board is empty. Three fuses are in three places, each a different kind of danger:

- **The laundry** (B1): under the flooded floor; you have to dive for it in the dark.
- **The cold room** (B1): inside one of Rauk's larders.
- **The bar** (1): in the cash drawer; the bar is lamplit and open to the atrium.

With the fuses in, starting it takes a sequence from the engineer's log (prime the fuel pump, open the choke, crank, close the choke as it catches). Get it wrong and it coughs, loudly, and you start again. When it starts, the whole hotel hears it.

- **You get:** power. The lights come on (Rauk starts switching them), both lifts work, and the lockdown panel wakes up.
- **Story:** the engineer's log: the generator was oversized for a hotel, because it also powered the lockdown, the Cabinet's locks and the "feeding hatch".

### 4.3 The Last Waltz (ballroom)

The bandleader's letter (in his room on floor 3) explains the night: while the band played, it stood at the edge of the ballroom and didn't move. The sheet music for the last waltz is with the letter. In the ballroom, the grand piano on the bandstand is a row of note blocks. Play the waltz, and the compartment under the bandstand opens.

- **You get:** Wennberg's study key (the bandleader took it from Wennberg that night).
- **Danger:** it is loud, it takes time, and you have to stand at the piano with your back to a dark ballroom.
- **What the music does to Rauk** (decided): it draws it in, looking for you. Arriving, if it sees or hears no sign of you, it usually goes on searching (the rooms around, the galleries, the doors). Sometimes instead it stops at the edge of the ballroom and stands still, listening, for as long as the music plays: the bandleader's trick, not reliable.

### 4.4 The Collector's Shelves (library)

The library's back wall is Wennberg's catalogue: chiseled bookshelves, with books scattered around the reading room. His catalogue lists the Cabinet's specimens by number. Put the right books in the right slots and a section of the shelves swings open.

- **You get:** Wennberg's hidden study: the **first half of the release code** (in his desk), the **penthouse key** (lets the service lift go to floor 4), and his catalogue, which describes "No. 7".
- **Danger:** the coziest room in the hotel, warm and lamplit, and this puzzle takes a while. You'll want to stay. The lamps go out.

### 4.5 The Stopped Clock (penthouse)

Wennberg's suite is exactly as he left it. The grandfather clock in his study is stopped. His last journal entry gives the time he threw the lockdown. Set the clock to 23:47, and the panel behind it opens into the lockdown control room.

- **You get:** the **lockdown key** and the **second half of the code**, and the end of Wennberg's journal.
- **Danger:** the top floor, reached by one lift. To get down again you take the lift or the roof ladder, and Rauk knows both.

### 4.6 The Lockdown (finale)

The release panel is in the security office behind reception, where the clerk could see the front doors. Power on, key in, code entered, lever pulled.

The lockdown alarm rings through the whole building, and the shutters start to rise one by one, slowly, all over the hotel. The front doors are across the atrium. Rauk is coming, and it is hunting, not playing. Get through the doors.

### 4.7 Optional: the Cabinet

Behind the spa, in Rauk's den, is Wennberg's original journal from the day he brought "No. 7" to the hotel. You don't need it to escape. Reading it explains what Rauk is, as far as anyone knows. Getting it means going into the den, ideally while it is out, or while it is fed and resting.

### 4.8 In the reshaped hotel (agreed 2026-10-12, built 2026-10-13)

The reshape (14) moves some of this. Where each puzzle is now:

| # | Puzzle | Where now | What changed |
|---|---|---|---|
| 1 | The Night Ledger | Reception in the rotunda: the ledger open on the desk, the safe in the pigeonhole wall behind it | Moved with reception (decided). Your back is to the round stair and the cage lift's open shaft, which come down from every floor above. |
| 2 | The Generator | B2; the fuses in the laundry (B1), the cold room (B1) and the bar (1) | Nothing |
| 3 | The Last Waltz | The ballroom; the bandleader's letter in his room on 3 | Nothing |
| 4 | The Collector's Shelves | The library on 2 | Nothing |
| 5 | The Stopped Clock | The penthouse | Nothing |
| — | The Lockdown | **Decided:** the release panel stays in the old building, in the night clerk's office behind the old reception. The run out then goes from there to the new front doors at the corner: through the garden hall, across the dead courtyard, through the palm court under its glass roof, through the rotunda, with the shutters rising all round. About 100 blocks, where it used to be about 20. | The front doors moved to the corner |

**Groundwork first** (built 2026-10-12, notes in 10):

- **Shutters on the new front doors.** The reshape left the glazed doors at the corner opening straight out. They go behind lockdown shutters on the outer face, as every window does, and those shutters are the last ones to rise in the finale.
- **Progress saved with the world**: which puzzles are solved, which items have been handed out, the lockdown's state.
- **The puzzle items**: the service lift's crank, the staff keyring, the three fuses, Wennberg's study key, the penthouse key and the lockdown key. The notes are written books.
- **Locks**: staff-only doors the keyring opens; Wennberg's study (the study key); floor 4 on the service lift (the penthouse key); the service lift won't run without its crank.
- **The flashlight** (8, step 4d), brought forward. With the power off the hotel is black but for a few flames, so without it puzzles 1 and 2 can't be played as designed. Carried lit, it darkens the hotel round you and lets Rauk see you from far down a corridor (6.1).

**Then the puzzles in order**, 1 to 5 and the finale. Each comes with its own blocks (the safe, the fuse board and the generator's controls, the piano, the shelves, the clock, the lockdown panel), its notes written in voice, its sounds for Rauk to hear, and a playtest. Hiding places (step 4c) aren't needed for any of them and can come after.

**Later, if wanted:** the new rooms could take a puzzle of their own once the five are in, for example threading the last reel in the cinema's projection room, or a message on the telegraph.

---

## 5. The story in the building

How the story is told, without anyone telling it:

- **Notes and books**: the clerk's ledger, the chef's note, the porter's note, the bandleader's letter, the engineer's log, Wennberg's catalogue and journals, a gala programme, guests' letters never sent. Vanilla written books on lecterns, in drawers and on bodies' remains. Short, in voice, and readable in the dark at a glance.
- **Rooms left as they were**: the ballroom with the tables still laid; the kitchen barricade; the burned corridor; the lift stuck between floors with its gate torn; the Cabinet's cages bent open from the inside.
- **Rauk's own signs**: claw marks on the plaster, its larders (with its remains), blood trails, doors standing open.
- **Each of its scars has a matching note** (2). Players who read them will recognise the injuries on the creature that's hunting them.

---

## 6. Rules, dying, light and ambience

### 6.1 Rules inside the hotel

- **No building or breaking** (decided): inside the hotel you're in **adventure mode**. You can open doors, pull levers, use what's there, but not wall yourself in or dig out. Leave the hotel and you're back to your own game mode.
- **Light you carry**: lanterns and candles can be found. Carrying one makes the hotel darker around you, and lets Rauk see you from far down a dark corridor (its design, 3).
- **Food**: the kitchen and staff rooms have enough to get by.
- **Multiplayer**: one hotel, one Rauk, shared progress. When the lockdown lifts, everyone can leave. Whoever comes after the chute has fallen in steps onto the rubble and goes down with it into the coal store (a way in, never out); anyone taken inside wakes in the staff dormitory. Played together over e4mc (Open to LAN).

### 6.2 Dying: no death screen

Inside the hotel you never see the death screen or the respawn button (decided). When Rauk kills you:

1. **The bite** plays as it does now (its face, the jaw, the bite, the red flash), then the screen goes black.
2. **Under**: in the black, you hear it, muffled and far away: dragging, then tearing. Your body is in its jaws. It takes you to its larder like any other kill.
3. **Wake up**, a small minigame: a heartbeat, slow and faint. Press in time with it. Each beat you catch brings it back a little stronger, the black lifts a little, and you start to make out the room. Miss and it slips away again for a moment. There is no failing it, only taking longer. About five good beats and you're awake.
4. **The staff dormitory** (B1): you're in one of the bunks, gasping. Your progress is kept. What you were carrying isn't with you: it's in its larder, with what it thinks is you. You'll want it back.

Dying any other way inside (a fall, drowning) goes the same way, without Rauk. Outside the hotel, death is vanilla.

### 6.3 Light

The hotel is dark (decided): you should want light, and fear it.

| Where | Lights off | Lights on |
|---|---|---|
| **Corridors** | Dark (light level 0–2). Your eyes, your lantern, and what comes through an open door. | Still dim: a weak wall lamp every few doors (about 5 near a lamp, 2 to 3 between). Pools of light with dark between. |
| **Rooms** (guest rooms, offices, staff rooms) | Dark | One lamp: about 6 to 8, dark corners. |
| **Big spaces** (atrium, ballroom, dining room, library) | Moonlight through the glass roof and high windows, very dim | Properly lit by chandeliers: about 10 to 12. The only places that feel bright. |
| **Before the power is back** | Candles and oil lamps here and there, the bar's lamps, the fireplace in the reading room | — |

- **The hotel's own lamps** are the mod's: wall sconces, ceiling lamps and chandeliers that give a dim light (not the full brightness of vanilla lamps), switched by the levers on the walls, the way Rauk expects (its design, 4.6).
- **Your brightness setting doesn't lift it**: inside the hotel the game's brightness option is ignored and darkness stays dark, so every player sees the hotel the way it's meant to be seen.

### 6.4 Ambience

Each part of the hotel sounds like itself, quietly, all the time:

- **The building**: settling creaks, a pipe knocking somewhere, wind finding a gap in the shutters, rain on the glass roof, the sea against the cliff below.
- **Cozy rooms**: a ticking clock, the fire, rain on the shutters.
- **Run-down areas**: dripping, a loose shutter tapping, a flickering bulb's buzz.
- **Ruined areas**: wind through the holes, creaking timbers, ash settling.
- **Below ground**: dripping water, the sea's low boom through the walls; the generator's thrum once it runs.
- **Fog**: a faint haze in the dark, thicker in the ruined wing and the flooded basement.

These ambient sounds are what you hear Rauk against. They stay quiet so its sounds still carry, and they never sound like it.

---

## 7. How it's built

| Part | Approach |
|---|---|
| **Where the hotel is** | In the Overworld (decided), exactly one per world, placed the way strongholds are but on a single ring: 1,500 to 3,500 blocks from spawn in a direction set by the world seed, moved to the nearest coast if there is one close by. Over the sea it stands on its own limestone stack. `/locate structure rauk:raukvik_grand` finds it, and a test command takes you there. |
| **Getting in, staying in** | The coal chute on the west side is the only way in: a hatch, a ladder down a shaft one block wide (too narrow for Rauk), a low passage into the coal store on B1. It falls in behind the first one through, and clears again only once the hotel has been empty a while. The shell has no other gaps and the roof can't be reached, you're in adventure mode inside, and nobody but a creative player can break the hotel's blocks from outside either. Rauk never leaves it: the hotel is the edge of its world. |
| **Building it** | Built in code, like Rauk's body: a `HotelBuilder` lays out floors, rooms, corridors and furniture from a description, so every change is reviewable and the same in every world. World generation copies it into the world chunk by chunk, carving into the cliff for the basements. |
| **Furniture and dressing** | The mod's own furniture, built in code like Rauk's body: each piece drawn as boxes, textured in code, placed room by room after its purpose and condition (12). |
| **The lifts** | Each car is blocks standing at one floor, in its shaft behind a brass scissor gate. When it's sent, the gate shuts, the car lifts out of the world as moving block displays with whoever is in it seated inside, rides the shaft with sound, shake and the floor going past, and sets down as blocks again at the other floor, where the gate opens. A lever in the car goes up a floor (sneak for down); a button by each gate calls it. The shaft beside and under the car is real and open, for Rauk. |
| **Puzzle blocks** | Vanilla where it fits (note blocks, chiseled bookshelves, lecterns, levers, item frames). A few of the mod's own: the safe, the fuse board, the generator, the lift panels, keyed doors, the shutters and the lockdown panel. |
| **Progress** | Saved with the world: power, puzzles solved, keys found, lockdown state. |
| **Rauk** | Spawns in the Cabinet, its home, when the hotel is generated. The hotel's levers, doors and larder spots are its territory from the start. |
| **Light and ambience** | The hotel's own dim lamp blocks; inside the hotel the client ignores the brightness setting and adds a faint fog; ambient sound layers by area (6). |

---

## 8. Build order

One part at a time, each reviewed before the next:

1. **This design**, reviewed.
2. **The shell**: the hotel in the Overworld (placement, getting in, staying in, the test command) and the bare structure of all seven levels: walls, floors, corridors, the atrium, both stairways and both lift shafts. Rauk let loose in it, to check it moves well through all of it.
2b. **The revamp** (11): the look of the building, inside and out. Its own textured blocks; the structure rebuilt with depth: thick walls and deep windows, pavilions, cornices, pilasters, a mansard attic floor, turrets; inside, wainscot, wallpaper, coffered ceilings and columns, each area in its condition.
3. **The lifts.** *(Built after the doors.)*
4. **Dressing** (12): the furniture and fittings, lighting and switches, room by room. *(Built before the lifts, as asked.)*
4b. **Doors** that open by hand, slowly, fast or just ajar, for Rauk and for you (13).
4c. **Hiding places**: wardrobes, closets, behind curtains (11.5).
4d. **Things to carry**: the flashlight and the other useful and story items (11.5).
5. **The puzzles**, one at a time, with their notes.
6. **The story pass**: all the notes and set pieces, written and placed.
7. **The finale and the ending**, dying and the wake-up minigame, light and ambience.
8. **Playtest and polish.**

---

## 11. The look (revamp, 2026-10-06)

The building as the references show it ([_References/rauk_references](../../_References/rauk_references)): **dark old-world luxury, a 1920s grand hotel**. Dark mahogany and brass all through it. Most of it is **cozy and intact**, warm and safe-feeling, which is the lie; the service parts are **gross**, and some of the fine rooms are **wrecked**.

### 11.1 Outside

A Beaux-Arts grand hotel, after the reference's shape:

- **Blue-grey stucco** walls over a **rusticated limestone** ground floor with arched windows; **cream limestone** for everything that frames: window surrounds with keystones, quoins at the corners, string courses between floors, the big cornice under the roof.
- **Depth, not flat walls**: walls two blocks thick, so every window sits in a deep reveal behind its iron shutter; **corner pavilions** and a **central pavilion** stand forward of the wings; **pilasters** run up the pavilions; balconies with **wrought-iron** railings.
- **A slate mansard roof** with **dormers**, a fourth storey under it (the attic), iron cresting along its top, **domed corner turrets** with brass finials, and the glasshouse on the central pavilion.
- **The front**: a columned **portico** over the front doors, and steps down to the drive.
- Ninety years of weather: streaked, stained, the paint gone grey in places.

### 11.2 Inside, by condition

Every area is one of three conditions, so the hotel has a rhythm: you get used to safe-looking rooms, then the next door opens on something gross.

| Condition | What it looks like | Where |
|---|---|---|
| **Intact** (cozy, the false sense of safety) | Mahogany wainscot, green or red **damask**, coffered dark-wood ceilings, **red runners with gold borders** on dark boards, brass, herringbone parquet; dusty but whole | The library and reading room, the bar, the 2nd floor and its suites, the writing room, the reception desk, the penthouse, the lift cars |
| **Worn** (grand, then left) | The same, faded and stained: water marks, dust, cracked marble, scuffed panelling, threadbare carpet | The lobby, atrium and grand staircase, the dining room, the ballroom (cream and gold, peeling), the 3rd floor west, the attic |
| **Wrecked** (gross) | Grime, rust, mould, peeling plaster down to the lath, cracked tiles, pipes along the ceilings, charring | The kitchen (concrete and pipes, filthy), laundry, cold rooms, staff rooms, the spa (algae), the machinery, the burned east wing, **and some of the fine rooms**: the smoking room and billiard room |

### 11.3 Materials

The mod's own blocks, textured to match (painted in code, `./gradlew paintHotelTextures`):

- **Outside**: blue-grey stucco (and stained), limestone, rusticated limestone, keystones, cornice and pilaster pieces, columns, balusters, slate shingles (block, stairs, slab), wrought-iron railing, brass finials, the iron shutters.
- **Walls**: mahogany wainscot (intact, worn, charred), damask green and red and the ballroom's cream-and-gold panels (intact and worn), cream marble, white tiles (grimy), peeling plaster, concrete.
- **Floors**: dark boards, herringbone parquet, cream and black marble, grimy tiles, concrete, charred boards, algae mosaic; the corridor **runner** (red with a gold border) and **carpeted marble stairs** for the grand staircase.
- **Ceilings**: coffered mahogany, plaster (and cracked), the ballroom's gilded panels.
- **Structure**: fluted mahogany columns and pilasters, marble columns (Rauk can climb these, not the marble walls), iron lattice for the guest lift's cage, brass, iron pipes.

### 11.4 Bigger

- **A fourth storey, the attic**, under the mansard: servants' rooms in the west, lumber and water tanks in the east (burned through from the floor below), and the penthouse in the middle as before, still sealed but for the key lift and the glasshouse ladder. A gallery round the top of the atrium, under the glass roof. Reached by the servants' stair from the 3rd floor.
- **The atrium** rises a storey higher, to the new glass roof.
- Thicker walls and the pavilions make the building bigger outside; the rooms keep their layout, so everything Rauk learned of the routes still holds.

### 11.5 Later (in this order, after the structure)

1. **Furniture and fittings**: chairs and sofas, the bar counter and stools, the reception desk, tables, beds, wardrobes, the piano, chandeliers, sconces and **candles**, curtains, rugs, debris and grime for the wrecked parts. *(Done: section 12.)*
2. **Doors** with **procedural animation**: they swing open over time instead of snapping. Rauk opens them slowly, fast, or only an inch to look through, depending on what it's doing; players open them the same way, by hand. *(Done: section 13.)*
3. **Hiding places**: wardrobes and closets to get into, curtains to stand behind.
4. **Things to carry**: a **flashlight**, and other useful and story items found along the way.

## 12. Dressing (2026-10-06)

The furniture is the mod's own, built in code the way Rauk's body is: every piece is drawn as boxes (`HotelFurniture`, `FurnitureCatalog`), the game makes its block and outline from them, the asset writer its models, and the texture painter paints what it's made of. About a hundred pieces, most in more than one condition: the same chair in red leather, torn, or overturned; the same bed made up, stripped to its mattress, or burned.

### 12.1 What there is

| Group | Pieces |
|---|---|
| **Seating** | Club chairs (oxblood, green, torn), Chesterfields (whole and torn, joining into sofas of any length), gilt-and-velvet settees and armchairs, dining chairs (and overturned), the ballroom's gilt chairs, bar stools, upholstered and staff benches, kitchen chairs, chairs and sofas under dust sheets |
| **Tables** | Round pedestal tables (and overturned), tables that join into any shape: laid with a cloth, bare mahogany, pine, the kitchen's zinc, billiard tables (and wrecked); writing desks, nightstands, chests of drawers, console tables |
| **Counters** | The bar counter with its brass foot rail, the reception desk with its carved diamond panels (after the reference), kitchen ranges, scullery sinks, ice boxes |
| **Storage** | Wardrobes (two blocks wide and high: somewhere to hide, 4c), filing cabinets, staff lockers, steamer trunks, suitcases; and whole-block fittings: bookcases (and dusty ones), the key pigeonholes, wine racks, pantry, linen and bar shelves, bar cabinets, crates |
| **Beds** | Hotel beds (made, stripped, burned), Wennberg's four-poster, iron beds and bunks, a crib |
| **Light** | Wall sconces, chandeliers, grand chandeliers on chains, pendant lanterns, enamel kitchen lamps, caged bulbs, banker's lamps, silk-shaded and standard lamps (electric); candlesticks, candelabras, church candles, candles in bottles, oil lamps, the marble fireplace (flame) |
| **Walls** | Velvet curtains (red, green, torn) drawn across the windows, portraits in oils (four: Wennberg, a lady, an officer, a girl whose eyes are scratched out), a painting of the bay, gilt mirrors, wall clocks (all stopped at a quarter to twelve), notice boards, radiators, the light switches |
| **On tables** | Place settings, bottles, the open ledger, typewriter, telephone, service bell, cash register, gramophone, dead flowers, piles of books |
| **About the rooms** | Globe, smoker's stands, music stands, dead palms in brass urns, coat stands, the grandfather clock (puzzle 5), the grand piano (puzzle 3), a rocking horse, porcelain dolls, baths, washbasins, water closets, washstands, laundry coppers, mangles, baskets, pot racks, meat hooks, iron stoves, boilers |
| **Wreckage and floors** | Fallen plaster, broken glass, scattered papers, ash, broken chairs, the fallen chandelier, coal, sacks; Persian rugs (red, green, worn) that join into any shape with their border round the edge |

### 12.2 Room by room

Each room is furnished after what it was for, in its condition (11.2): the guest rooms with a bed against the wall away from the door, nightstands and a lamp, a wardrobe, a desk, a rug, curtains; the library walled with shelves round four reading tables with green lamps; the bar with its counter, stools, back bar and little tables; the dining room and ballroom laid for the gala (the band's stand with the piano at the ballroom's east end, three chandeliers hanging and the fourth on the floor where it fell); the kitchen gross, its barricade pulled apart, burned-down candles in bottles and ash where the lamp oil went up; the smoking and billiard rooms torn up and thrown about; dust sheets where rooms were shut up; char and ash in the burned wing; the staff's iron bunks, lockers, long tables; the attic's lumber and its nursery of cribs and dolls; Wennberg's penthouse kept as he left it. Furniture keeps to the walls and leaves doorways and the middles of rooms clear: Rauk still gets everywhere it could.

### 12.3 Light and switches

As section 6.3 has it: a lamp or two in each room, sconces every few doors along the corridors, bare bulbs in the service parts, chandeliers only in the big rooms (dining room, ballroom, library, reception, entrance hall, bar) and one long one down the middle of the atrium. Lit lamps glow. Every room's lamps are wired to a **switch** by its door (a brass plate of two push buttons); they light only while the hotel has **power**, which the generator gives (puzzle 2; for now `/rauk hotel power on|off`). With the power on, flicking a switch works its room; a lamp on its own can be clicked. Rauk works the switches as it works levers (its design 4.6), and counts the hotel's lamps as lights.

Before the power is back, only flames: the fire in the reading room, the oil lamps on the bar's tables, a candle by the bunks in the staff dormitory, one in the night porter's lodge. Candles can be pinched out and lit again with flint and steel; Rauk knocks them over.

## 13. Doors (2026-10-06)

Every door in the hotel swings. The panel turns on its hinges over time, gathering speed as it starts and slowing as it arrives; it counts as open, to walk through, once it's past halfway. Three kinds: **mahogany** with a brass number for the guests, **painted pine** for the staff, **glazed** for the spa. Double doors swing as a pair.

| Who | How | What it sounds like |
|---|---|---|
| **You**, a click | Swings it open, or shut | A door opening; Rauk hears it as one (24 blocks) |
| **You**, sneaking | A closed door opens **a crack** to look through; again, the rest of the way, slowly; an open door is **eased shut** | A long quiet creak; Rauk hears it only close by (10 blocks) |
| **Rauk**, stalking or searching | Slowly, creaking, as it creeps through; it waits at the door while it opens | A creak (it doesn't hear itself) |
| **Rauk**, walking | As a door is opened | A door |
| **Rauk**, charging | **Flung** back against the wall ahead of it, bouncing back an inch | A bang |
| **Rauk**, peeking | One finger draws it open **a crack**, slowly, and it looks through | A faint creak |

The doors only open towards a room, as they're hung. A crack isn't wide enough to get through, for you or for it. They don't work by redstone.

---

## 14. Reshaped (built, 2026-10-12)

Asked for: the entrance on a corner like the reference photo, the hotel shaped into a U whose bottom is a triangle with the entrance at its point, longer hallways, the earlier suggestions, a cinema, and an underground station later. Then on to the story and the puzzles. Drawn in [hotel/concept.png](hotel/concept.png) (floors 1, 2 and 3, B1) and [hotel/concept_corner.png](hotel/concept_corner.png) (the corner from the street), by `./gradlew drawHotelConcept`.

**The shape.** The building as it is stays as the north range, facing the sea. Two arms run south from its ends (18 wide, 34 long), then turn in at 45 degrees and meet at a rounded corner: the new front, with the entrance. Inside the U is a courtyard. From 80 by 50 the hotel grows to about 80 by 124, roughly twice the floor area. One per world, as now.

**The corner** (after the photo): a rounded bay of cream limestone, the ground floor rusticated; a canopy round it on branching brass columns, the doors under it; a tall arched window of iron lattice up its middle, with a sun lounge behind; arched windows on 3, iron balconies with dead plants; the slate mansard with dormers all round; on the corner a dome with the hotel's name in brass letters, and strings of bulbs from it along the roof edge, nearly all dead.

**Inside, by level:**

| Level | New |
|---|---|
| 1 | **Rotunda**: the new entrance hall under the dome, round, with reception (moved from the old lobby) and a round stair with a brass cage lift up its middle. **Palm court**: glass roofed, two floors high, in the courtyard's point, dead palms, and the **tea room** beside it. **The Arcade** (west diagonal): a long gallery of shops: barber's, telegraph and post office, florist, jeweller. **The Aurora** (east arm): a cinema two floors high, raked seats, screen, organ pit; its foyer, ticket hall and bar in the east diagonal. West arm: luggage hall, music room, manager's office, ladies' lounge. Stair towers where the arms turn in. The old entrance hall becomes the garden hall, its front doors opening onto the court. |
| 2 and 3 | Guest rooms and suites all round, and a **corridor ring round the courtyard on every floor**: the old south corridor, 45 blocks straight down each arm, 50 along each diagonal, round the rotunda (about 270 blocks a floor). **Children's playroom** in the west arm; **maids' closets** and linen rooms along the corridors (for hiding, later); **dumbwaiters** and a **laundry chute** in the stair towers. The cinema's balcony and projection booth on 2. On 3 the fire has gone on along the east wing; the corner suite is in the rotunda. |
| 4 | Attic rooms under the mansard all round; the lantern room under the dome, where the sign's machinery is. |
| B1 | The **servants' tunnel**, a ring under all the new wings joining the old service corridor; the **ice house** under the palm court; the laundry chute's foot; the organ blower and the cinema's boiler; stores, wine, cold rooms, porters' rooms. |
| B2 (later) | **Raukvik Station**: a private platform and its tunnels under the corner, reached by a stair down from the rotunda. Kept free now, built later. |
| Outside | The courtyard: a dead garden, gravel paths, a dry fountain, statues. You see across it into the other wings' windows (and what moves past them). |

**For Rauk:** the long corridors and the rings let it come at you from either way round; crawlspaces over every new corridor; the chute, the dumbwaiters and the rotunda's lift shaft are new ways up and down; the cinema (dark, rows of seats, behind the screen, the organ pit) and the palm court's glass roof (its shape on the glass above you) are new places to meet it; it crosses the courtyard on the walls outside.

**Decided** (2026-10-12): true 45-degree walls, smoothed with slanted wall pieces inside and out; cream limestone all round, the old front included; reception moves to the rotunda, and puzzle 1 with it.

**Built** (`HotelWings` lays out the new wings round the old building's plan; `./gradlew auditHotel` checks the outside for blocks out of place, and finds none):

- **The shape**: the arms, the 45-degree wings and the rotunda, 80 by 124; the slanted walls finished with their own pieces (`slant_*`), the windows in them shuttered at 45 degrees; corridors 6 wide along the diagonals so the long runs stay straight.
- **The corner**: the canopy on branching brass posts with its strings of bulbs, the brass lattice window, the dome on its drum with RAUKVIK GRAND in brass letters, the glazed front doors; the old front's balconies and turrets put right (the floating railings are gone).
- **Inside**: the rotunda with reception, the round stair and the cage lift; the palm court and tea room; the Arcade's shops (barber, telegraph, florist, jeweller); the Aurora cinema with its foyer, box office and projection room; the music room, manager's office, ladies' lounge, luggage hall; the guest rooms, maids' closets and linen rooms of 2 and 3, the burned east wing on 3, the attic rooms on 4; the servants' tunnel, ice house and stores on B1; stair towers B1 to 4, the dumbwaiter and laundry chute.
- **Outside**: the courtyard, with its dead hedges, gravel, dry fountain and statues; the old entrance hall is the garden hall, its doors onto the court.
- **New blocks**: ashlar, slanted wall pieces, brass letters, festoon bulbs, brass posts, the canopy, brass lattice, dead hedge, cinema screen, fitted carpet; furniture for the new rooms (cinema seats, organ, projector, barber's chair and pole, display cases, wicker chairs, statues, film reels).
- **Left for later**: Rauk's routes through the new wings (they wait for the pathfinding rework, rauk-design 15); Raukvik Station.

## 10. Build notes

### Step 2, the shell (2026-10-06)

- **Built in code** (`rauk.com.hotel`): `HotelBuilder` lays out all seven levels as a plan of blocks; the floor plans are drawn from it into [hotel/plans.png](hotel/plans.png) (`./gradlew drawHotelPlans`). Bare structure only: every room, corridor and crawlspace, both stairways, both lift shafts, the atrium and its galleries, doors, shuttered windows, the roof. Each style has its own floor, walls and ceiling for now; dressing comes in step 4.
- **In the world:** one hotel per world, placed by the seed 1,500 to 3,500 blocks out and moved to a coast, its back to the sea. The ground is shaped to meet it. It is gone over once when someone first comes near, to put right anything the world grew into it. `/locate structure rauk:raukvik_grand` finds it. No mobs spawn in or round it. Rauk is generated with it, in the Cabinet.
- **Getting in, staying in:** the coal chute; adventure mode inside; nobody but a creative player breaks its blocks.
- **Test commands:** `/rauk hotel build | tp <place> | info | cut <level> | restore | rauk`.
- **Rauk getting about:** the hotel is mapped as its spaces and the openings between them (`HotelNav`). Across the building Rauk goes from opening to opening, and its route finder only has to reach the next one; inside the hotel its routes keep to the building. Movement fixes made on the way: lining up exactly in doorways, climbing in and out of crawlspaces and lift shafts, dropping through holes only where its body fits, and not letting go of a wall too early.
- **Tested in game:** across every level by the grand stair, the galleries, the crawlspaces, the service lift (up and down all its floors), the spa to the Cabinet, the Cabinet's drain to B2, and back up to the penthouse; and the hotel generated in a normal world.

### Step 2b, the revamp (2026-10-06)

Built after the references in [_References/rauk_references](../../_References/rauk_references), as section 11 describes:

- **48 blocks of its own**, textured in code (`./gradlew paintHotelTextures` paints them, writes their models and names, and draws them all on [hotel/materials.png](hotel/materials.png)). The worn and wrecked ones come in several looks picked at random block by block, so stains, cracks and holes don't repeat.
- **Outside**: walls two blocks thick with every window in a deep reveal behind its shutter; the corner and central pavilions stand forward; rusticated limestone ground floor with keystoned windows, blue stucco above with sills and cornice hoods, limestone bands and quoins, pilasters up the pavilions, a string course and the main cornice; a balustrade along the foot of a slate mansard with dormers; iron cresting round the roof; four corner turrets with slate caps and brass finials; the central gable with its great window and pediment; the columned portico with a balcony over the front doors, and iron balconies above it.
- **Inside, by condition**: mahogany wainscot and green or red damask under coffered ceilings where it's intact, the same faded and stained where it's worn, peeling plaster down to the lath, grimy tiles, bare concrete and char where it's wrecked; marble in the lobby and atrium, cream and gilt in the ballroom; red runners with gold borders and mahogany pilasters along the good corridors; marble columns in the entrance hall and up the atrium (Rauk climbs these); the guest lift's cage in iron lattice banded with brass; pipes along the kitchen's and the machinery's ceilings.
- **Bigger**: the attic floor under the mansard, reached by the new servants' stair from 3; a fourth gallery round the atrium, which now rises to the glass roof over the attic.
- **Rauk** still gets everywhere: all ten test routes pass in the new building (a fix to how it takes hold of walls was needed on the way).
- **Next, as section 11.5 orders it**: furniture and fittings, then the doors, hiding places and things to carry.

### Step 4, dressing (2026-10-06)

- **The furniture** (12): about a hundred pieces as blocks, drawn as boxes in `FurnitureCatalog` and placed by `HotelDresser`. One block class does them all (`FurnitureBlock`): rows and stacks join their neighbours, tables join on every side, pieces of several blocks are placed and broken whole, and turn with the hotel. `./gradlew paintHotelTextures` paints their textures (sheet: [hotel/furniture_textures.png](hotel/furniture_textures.png)) and writes their models, the lit ones marked to glow. The floor plans show them.
- **Light**: lamps glow when lit (Fabric's renderer draws their shades and bulbs at full brightness); every room's lamps on a switch by its door; power off until the generator (`/rauk hotel power on|off` for testing).
- **Showroom**: `/rauk hotel showroom <pos>` lays out every piece for review.
- **Rauk** still gets everywhere: all ten test routes pass in the furnished building.
- **Tested in game**: every piece in the showroom, by day and lit at night; the furnished hotel room by room, with night vision and as it really looks with the power on; the lights switched on and off; the hotel generated in a normal world, turned, furnished.

### Step 4b, doors (2026-10-06)

- **The doors** (13): `HotelDoorBlock` (a door, so everything that knew about doors still does), its swing kept by `HotelDoorEntity` and drawn by `HotelDoorRenderer` on the client between ticks; their panels and faces written and painted with the rest. Every door in the plan is one of them.
- **Rauk**: opens doors as fast as it's going; looks ahead for the door its route goes through, further the faster it goes, so a door it charges is open before its head gets there; only doors it goes through, not ones it brushes past; peeks through a crack.
- **Test command**: `/rauk hotel door <pos> open|close|ajar|creep|slam|shut|peek`.
- **Lintels**: the wall over a doorway or a window's recess is now wall, not ceiling.
- **Tested in game**: doors clicked open and shut, and handled with care, by the test player; every swing by command; double doors; Rauk creeping in through one and charging out of another; all ten of its routes still pass.

### Step 3, the lifts (2026-10-06)

- **The cars** (`LiftCars`, placed by `HotelBuilder.lifts()`): the service lift, B2 to 4, a tread-plate floor, a caged bulb; the guest lift, 1 to 3, parquet and brass, a pendant lantern, seen through its lattice cage from the atrium. Each starts at its bottom floor. A brass scissor gate (a hotel door, `lift_door`) on every floor it stops at, which won't open unless the car is there; a call button beside each; a brass control in the car.
- **A ride** (`HotelLifts`): the lever sends the car up a floor, sneak-click down; the button calls it. The gate shuts, the car becomes block displays that slide up or down the shaft, eased at each end, with whoever's inside riding it (players in an invisible seat, so the camera rides smoothly), chain and hum sounds, a jolt at the start and the stop and a tremor between (`LiftRide` on the client), and the floor passing on the action bar; at the other floor it's blocks again, the bell rings, the gate opens. Where the car stood is open shaft again. No power, no ride; the lamps in the cars follow the power.
- **Rauk**: the shaft is mapped as the parts the car leaves it in, so its routes go round a car that's in the way (by the stairs) and through the shaft when it's clear; its movement no longer gives up when it slips off a sill it's stepping over, it grips the wall again. The rides make noise it hears.
- **Test command**: `/rauk hotel lift <service|guest> <B2|B1|1|2|3|4>` sends a car.
- **Not yet**: the service lift's crank handle (puzzle 1) and Wennberg's key for floor 4 (puzzle 4); for now it runs with power alone. The stuck car with something on its roof (3.2) comes with the story pass.
- **Tested in game**: the guest lift ridden 1 to 3 and called down; the service lift ridden B2 to 2 and sent up by its lever; the gates shutting and opening; all eleven of Rauk's routes pass with the cars in their shafts.

### Playtest fixes (2026-10-06 and 07)

- **Getting about** (`HotelNav`, `HotelRoutes`, `RaukMover`): the whole route across the hotel is worked out and followed, opening by opening; the map is redrawn when a lift car moves (a car cuts its shaft where it stands); an opening it can't reach or keeps getting stuck on is avoided for a minute and it re-routes at once. The guest lift's cage is no way between floors (it can't climb lattice); the pool is joined to the spa above (in water it climbs anything). Climbing round the edge of a landing works (the service stairs between 1 and 2).
- **Cutting off** (`Prediction`, `RaukBrain.cutOff`): someone running for a stairwell's door comes out on the next floor; it compares their way on foot with its own (lift shafts included, never through their stairwell) and goes there if it's as quick, waiting beside the door. Tested: a runner up the service stairs from 2 to 3 taken at the top, Rauk having come up the lift shaft.
- **With friends**: late arrivals slide in over the chute's rubble; the taken wake in the staff dormitory.
- **Test scenes**: `climbs` (the stairwell, the pool), `hunt_hotel` (RAUK_HUNTS: stairs, below, atrium, gallery, cutoff), `multiplayer`; RAUK_WORLD=<save> looks into a copy of a played world.

### The puzzles' groundwork (2026-10-12)

- **The front doors** at the corner are behind the lockdown's shutters now, the glazed doors set back behind them, as every window is.
- **Progress** (`HotelProgress`, saved in `HotelData`): what has been done, by name, and the locked doors opened with their keys. `/rauk hotel progress` shows it; `progress set <what> on|off` and `progress reset` change it.
- **Things to carry** (`RaukItems`), each with a line in the hotel's voice: the flashlight, the service lift's crank, the staff keyring, fuses, the study key, the penthouse lift key, the lockdown key. Their sprites are painted in code (`ItemAssets`, sheet in [hotel/items.png](hotel/items.png)). Dropped, they never despawn. `/rauk hotel items` gives you one of each.
- **Locks**: hotel doors can be locked to a key (`HotelDoorBlock.Lock`). Locked, a door rattles and stays shut, for Rauk too (to its route finder and its map it is a wall). Its key turns it once, for everyone, and Rauk's map counts it open from then on. Locked so far: the laundry and the cold room (the staff keyring, puzzle 1) and Wennberg's study (its key, puzzle 3). Rauk never starts the game behind a locked door.
- **The service lift** won't move until its crank is on the winding gear (the crank held to the car's lever), and won't go up to 4 until the penthouse key is turned in it.
- **The flashlight**: a click turns it on or off. One lies on the staff dormitory's table by the candle, next to the way in, another on the staff mess table; a click picks it up. Rauk sees a lit one in the dark from 48 blocks, and from 64 with the beam in its face. **Its beam doesn't show yet** (rauk-design 15), set aside for now.
- **Tested in game** (DevShot `groundwork`): the shutters outside and the doors behind them; picking up the flashlight; the laundry door locked, then opened with the keyring; the lift refusing without its crank and without the key, then going.

### Puzzle 1, the Night Ledger (2026-10-12)

- **Where**: reception in the rotunda. The night clerk's ledger lies open on the desk (`night_ledger`); the safe stands in the wall of pigeonholes behind the desk (`reception_safe`).
- **The ledger** (`NightLedger`): a click opens it as a book. A sheet folded into the cover: *the safe is the order they went, first to last, the last figure of each room.* Then the day's comings and goings in a neat hand (rooms that don't count), the gala, the shutters at 23:47 ("I will write them as I hear"), and four guests who "checked out", in the order he heard of them, not the order they went, his hand getting worse. Past midnight comes after 23:59, which is the trap.
- **Each world its own night**: the four guests, their rooms (real guest rooms on 2 and 3), their times and the order he wrote them in come from the world's seed, so the combination can't be looked up.
- **The safe** (`SafeBlock`, `SafeEntity`, drawn by `SafeRenderer`): a squat strongbox of near-black green enamelled iron, two blocks wide, a pair of brass-edged doors, four brass dials with a little enamel window over each showing its figure, first to last from the left. A click on a dial turns it on a figure, sneaking back one; each click is a sound Rauk hears from 10 blocks. On the right four figures the tumblers drop and both doors swing back (Rauk hears that from 24). A click on the open safe takes what's in it: **the service lift's crank, the staff keyring, and the night clerk's last entry** (a book to keep, pointing to the cold room, the laundry, Wennberg's key and the generator).
- **Saved**: the dials' figures with the safe; "night_ledger_solved" in the hotel's progress.
- **Tested in game** (DevShot `ledger`): the ledger read page by page; the safe dialled to this world's figures one click at a time, opened, emptied; the crank, the keyring and the last entry in hand.

### Puzzle 2, the Generator (2026-10-13)

- **The story** (the engineer's log, `engineers_log`, on the table in the generator and boiler room): the plant was far too big for a hotel because it also ran the lockdown, the Cabinet's locks and "the feeding hatch". At midnight Wennberg came down and had him pull the fuses so nobody could lift the lockdown. One went in the till at the bar, the night porter took one into the cold room, the third fell into the laundry's soaking tank. The log gives the way to start her.
- **The fuses** (`fuse_lying`, picked up with a click): in the **laundry** (behind the keyring door), at the bottom of a new soaking tank sunk in the floor, three deep, tiled, full of black water, so you dive for it; in the **cold room** (behind the keyring door), now a larder: the porter's remains (`porter_remains`: skull, ribs, coat, cap) in a pool of dried blood (`old_blood`, which doesn't fade) with a drag mark toward the door, the fuse by his skull; in the **bar's till** (`bar_till`), whose drawer rings out when it opens (Rauk hears it from 24, and the bar is open to the atrium).
- **The fuse board** (`fuse_board`) on the wall of the fuse room on B2: three porcelain holders that fill as you click them with fuses. Without all three the engine has no spark.
- **The engine** (`generator`, `Generator`): a dark green gas engine and dynamo on a brick plinth in the middle of the boiler room, its fuel pump, choke and starting crank on its front. Start her as the log says: three strokes of the pump, open the choke, swing the crank three times, and as she catches (about two seconds of sputtering) close the choke. Cranked unprimed or with the choke shut she coughs, a bang Rauk hears from 64 blocks, and you start again; leave the choke open as she catches and she races and dies the same way.
- **When she runs**: the power is on for good (the lights, both lifts), she thrums and smokes, and every Rauk in the hotel hears it wherever it is and comes to see (`RaukMind.hearFar`).
- **Saved**: "fuses_in" and "generator_running" in the hotel's progress; the board, the choke and the till as their blocks stand.
- **Tested in game** (DevShot `generator`): the till, the cold room and the laundry unlocked with the keyring and their fuses picked up (three); the board filled; a cough from an unprimed crank; then the start as the log says; the power on and the library lit upstairs.

### Puzzle 3, the Last Waltz (2026-10-13)

- **The story**: in the bandleader's room in the burned wing on 3, his unsent letter on the desk (`bandleader_letter`): while the band played the last waltz, it stood at the edge of the ballroom in the dark and listened; when they faltered, it moved; they played for three hours. Wennberg ran past the band and dropped his study key, and the bandleader locked it in the drawer in the bandstand, Wennberg's anniversary toy that opens only to the last waltz. His music is on the stand in the corner (`waltz_stand`), written out for the page-turner who couldn't read music: white keys counted from the left, 3 5 5 / 2 4 4 / 1 3 5 / 8 5 3, "once through, without stopping".
- **The piano** (`ballroom_piano`) on the bandstand: the grand piano with eight broad keys, C to C, the black keys in their places; a click on a key plays it (`LastWaltz`). Played through without a gap of five seconds, the drawer in the front of the bandstand (`bandstand_drawer`) springs open, with Wennberg's study key in it. The study key opens Wennberg's study in the penthouse (puzzle 5).
- **Rauk and the music** (design 4.3): when someone starts to play, every Rauk in the hotel hears it wherever it is and comes. Arriving, if it can't see who is playing, one time in three (`LastWaltz.spell`) it stops at the edge of the room, upright, its head turned to the music, and stands listening for as long as the music lasts (a new intent, LISTEN). When the music stops, it comes on.
- **Saved**: "waltz_played"; the drawer as its block stands.
- **Tested in game** (DevShot `waltz`): the letter and the music read; the waltz played key by key with Rauk loose in the ballroom (made to listen for the test): LISTEN and upright through the waltz, STALK once it stopped; the drawer open and the key taken.

### Puzzle 4, the Collector's Shelves (2026-10-13)

- **The catalogue** (`cabinet_catalogue`), open on a library table: the Cabinet's six specimens by number (which has which number comes from the world's seed), and No. 7 with no entry: "not a curiosity; it has its own book, and its own room". The volumes stand on the catalogue shelf by his study "in their numbers: the top row one to three, the bottom four to six, as one reads".
- **The six volumes** (`catalogue_volume_0` to `_5`), each a short book in Wennberg's hand about one specimen (the Visby crane, the bog woman of Lojsta, the two-headed calf, the Raukar eel, the glass hand, the sleeping lynx), titled by name: on the library's tables, on the floor by its chairs, on the reading room's console table and floor. A click picks one up.
- **The catalogue shelf**: a vanilla chiseled bookshelf in the library's east wall. With all six in their numbers (checked once a second, `CollectorsShelves`), the section of shelves beside it swings open: two bookcase doors (`bookcase_door`, a new kind of hotel door with no knob or hinges showing, locked SECRET: no key opens it, a click on it does nothing), flush with the shelving round them.
- **The hidden study** behind: his book on No. 7 on the desk (`no_seven`: dug out of a chamber under the sea stacks in 1911, asleep; it learns; the hotel and the lockdown were built round it; the release code is in two halves); the card with the code's **first half** (`code_card`, four figures from the world's seed, `LockdownCode`); and the **penthouse lift key** (`penthouse_key_lying`), which lets the service lift go up to 4.
- **Saved**: "catalogue_shelved"; the doors unlocked (Rauk's map then counts the study open).
- **Tested in game** (DevShot `shelves`): the catalogue read; a volume picked up; all six shelved in this world's numbers; the shelves swung open; the study's key taken.

### Puzzle 5, the Stopped Clock (2026-10-13)

- **Getting there**: the service lift to 4 needs its crank (puzzle 1), power (2) and Wennberg's penthouse key (4); his study door needs the study key (3).
- **The journal** (`wennberg_journal`) on his desk: the gala, "23:47: It is out. I threw the lockdown", the fuses pulled, the band still playing, then "the key and the second half are behind the clock. I set it to open at the minute it all began, and stopped it, so no one sets it by chance." (His book on No. 7, from puzzle 4, already said the second half is "upstairs, with the key, behind the clock".)
- **The clock** (`study_clock`, `ClockBlock`, its hands drawn by `ClockRenderer`): stopped at twenty past six. A click moves the minute hand on a minute (hold the click to run it on), sneaking the hour hand on an hour. At 11:47 it strikes and slides aside along the wall, and the panel door behind it (a hotel door locked SECRET) swings open into the lockdown control room.
- **The control room** (`controlRoom`): Wennberg's remains on the floor, a pocket watch on his chain; the end of his journal on the desk (`wennberg_last_pages`: it came up the lift shaft and stood at the study door till dawn; the panel is in the night clerk's office; "do it only if you can run"); the card with the code's **second half** (`code_card_2`); the **lockdown key** (`lockdown_key_lying`), dropped by the table.
- **Saved**: "clock_set"; the clock's hands with it; the door unlocked.
- **Tested in game** (DevShot `clock`): the journal read; the hour hand to eleven sneaking, the minutes to 47; the clock aside and the room open; the lockdown key taken.

### The finale, the Lockdown (2026-10-13)

- **The release panel** (`release_panel`) and its **lever** (`release_lever`) on the east wall of the night clerk's office, by the old front doors (as decided in 4.8). The panel is dead without power. Hold the lockdown key to it and the key turns and stays. Then key in the eight figures on its keypad (1 to 9, 0, and a red clear key), the first half from the card in Wennberg's hidden study, the second from the card in the control room; what's keyed shows as you go. Right, a lamp lights; wrong, a buzz and it clears. The lever won't move until all three are done.
- **The lever** (`LockdownRelease`): the alarm rings at every ear in the building for about seventy seconds; every Rauk hears it wherever it is and comes, and while the alarm rings it only hunts, it doesn't play. The shutters rise one window at a time all over the hotel, bottom up, each with the clank of its chain, over about fifty seconds; the windows behind them are glass (`slant_glass` on the diagonals), so the only way out is the front doors, whose shutters rise last, about a minute after the lever.
- **Out of the front doors**: it's dawn ("Dawn. You got out of the Raukvik Grand." Behind you the shutters stand open on the dark; it is still in there). The hotel stays open; you can go back in.
- **Saved**: "lockdown_key_in", "lockdown_code_ok", "lockdown_released", "escaped", and when the lever was pulled, so the shutters go on rising after a restart.
- **Tested in game** (DevShot `finale`, ten times as fast): the key in, the code keyed, the lever; the court side's windows rising; the front doors open; out, and dawn.
- **Still to come** (build order step 7): the dying and the wake-up minigame, and the ending's polish.

### The atmosphere (2026-10-07)

As 6.3 and 6.4 have it:

- **Darkness stays dark** (`LightTextureMixin`): inside the hotel the brightness setting counts as its lowest, for everyone. Night vision still works (it's an effect, not a setting).
- **Light, measured with the power on** (DevShot `light_survey`): corridors about 3 on average and 5 at a sconce; rooms about 3 with 7 by the lamp (table, desk and floor lamps two brighter than before); the big rooms with chandeliers about 8 on average, 11 to 13 under the chandeliers, the only bright places. A chandelier's light is carried down through its room by invisible light blocks every six blocks, two above the floor, on the room's switch (`HotelDresser.spreadChandelierLight`; Rauk pays them no attention). With the power off it's black but for candles, the bar's oil lamps and the reading room's fire, as designed: you'll want a light.
- **Fog** (`FogRendererMixin`): a faint dark haze from about 4 to 40 blocks; ash in the burned wing (0 to 18), damp below ground, in the spa and the Cabinet (0 to 15), dust in the crawlspaces (0 to 12), grey in the service and machine rooms. It eases in as you enter and out as you leave.
- **No music** in the hotel (`MusicManagerMixin`).
- **Sound** (`HotelAtmosphere`, `HotelSounds`), quiet and positional, by the kind of space you're in: creaks, a pipe knocking, plaster trickling, wind at the shutters, the sea booming below (on 1 and below ground), drips, a loose shutter, burnt timbers and ash in the burned wing; a low rumble under everything below ground and a draught through the burned wing and the attic. Candles flicker audibly; lit bulbs buzz. The good rooms are the quietest. All built from vanilla samples, none of the ones Rauk's sounds use. They play on the Ambient/Environment slider.
- **Clocks**: every clock in the hotel stopped at a quarter to midnight (puzzle 5 is the stopped clock), so none ticks; 6.4's ticking clock is left out.
- The server tells each client where the hotel is and whether the power is on (`HotelStatePayload`, `HotelSync`).

## 9. Decided in review (2026-10-06)

1. **Setting and name**: kept: the Raukvik Grand, 1911, on the limestone coast, sealed since the 1931 gala.
2. **Where it is**: in the Overworld, one per world, 1,500 to 3,500 blocks from spawn (7).
3. **Why you're there**: nothing says. You find it. *(Planned for the story pass: an old map to it, sold by cartographers and found in shipwrecks, like the explorer maps.)*
4. **Dying**: no death screen; the wake-up minigame; progress kept; your things in its larder (6.2).
5. **No building or breaking** inside: adventure mode (6.1).
6. **The waltz** draws it in to look for you; sometimes it stops and listens (4.3).
7. **After escaping**: you walk out at dawn, with a short ending. The hotel stays where it is, shutters up and open, and Rauk is still inside. You can go back.
8. **Size**: about 80 x 48 blocks a floor, one to two hours the first time.
