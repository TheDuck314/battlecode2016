## future perfect
[Greg McGlynn](https://github.com/TheDuck314) and [Luchang Jin](https://github.com/waterret)

This is the code for our Battlecode 2016 entry as team "future perfect". Our final code is in [`src/final_25`](src/final_25) and won the final tournament. Our submission for the Bloomberg Zombie Armageddon contest is in [`src/armageddon_26`](src_armageddon_26) and won first place in that contest.

### Highlights

**BotArchon.java:** All archon behaviors, including build order, picking up parts and neutrals, and the all-important archon retreat code.

**BotSoldier.java** and **BotViper.java:** These files are pretty similar and there is a lot of duplicated code between them. Here you can find our soldier and viper micro.

**BotTurret.java:** Turret code, including turret target selection using the enemy cache. Our turrets were quite aggressive and followed a fairly simple plan: pack up, move toward an enemy until you are in range to shoot, then unpack and start shooting until the enemies are gone.

**BotScout.java:** All scout behaviors, including map exploration, broadcasting locations of hostiles, parts, neutrals, and zombie dens, and pairing with turrets. We paired one scout with each turret to provide turrets with long-range vision. To establish ownership, scouts would periodically broadcast the ID of the turret they were following. Every so often we took a census of scouts to see how many "unpaired" scouts we had that weren't following turrets. Archons would then build enough scouts to make sure that we always had a few unpaired ones to provide vision of the rest of the map. A very useful scout behavior we implemented is that scouts would try to follow defenseless enemy archons and broadcast their location to get our army to come attack them. This helped us end games more quickly and avoid the dangerous, uncertain late game when zombies might just kill everyone.

**Radar.java:** A system for sending and storing info about important enemy robots. It had three parts:
- **General enemy cache:** Scouts sent the location and robot type of all nearby enemy robots. Turrets would listen for this info and store it in their enemy cache, and use this cache to choose targets, since they could shoot farther than they could see. Also, all units would move toward and attack the nearest enemy they heard about through these scout broadcasts. The basic messaging function for this is `Messages.sendRadarData`, which packs up to five enemy robot types and locations into a single message signal.
- **Enemy turrets:** Scouts sent warnings about enemy turrets and most of our units tried to stay out of enemy turret range. (Our turrets were very aggressive and mostly ignored this information, but occasionally used it to pick a target to shoot at). Enemy turrets were tracked by ID, so we could tell if a certain turret had moved locations.
- **Enemy archons:** We wrote a system for separately keeping track of enemy archons, but in the end we didn't use it; we just used the basic nearby enemy broadcasts above

**Messages.java:** Here you can see all our messaging functions. The first four bits of each message signal was a channel number, and each channel carried a different kind of message. All bots had a function called `processSignals` which looped through the signal queue and switched on the channel to process messages.

**Anti-turtle charge:** Inspired by the seeding tournament games of [mid high diamonds](https://bitbucket.org/maxnelso/battlecode2016), we wrote some code to detect turtle teams, surround them, and then charge all at once with massed soldiers and vipers. Unfortunately we never faced a turtle team in the qualifying or final tournaments so this turned out to be a waste of time. At a high level this anti-turtle charge was controlled by the code in `AntiTurtleCharge.java`. The code that decides when to charge is in `BotScout.java` and the actual charge is in `BotSoldier.java` and `BotViper.java`.

**Nav.java:** Here you can see our navigation code. Our navigation wasn't too fancy this year, since in the worst case we could just dig through rubble to get where we wanted to go. TTMs couldn't dig, so they used Bug.

**MapLocationHashSet.java:** This is a reasonably bytecode-efficient set we used for storing zombie den locations and neutral archon locations.

**FastMath.java:** some vector math functions and also some silly functions implementing unnecessarily optimized square roots and random numbers via lookups into gigantic strings. See [here](http://www.anatid.net/2015/12/battlecode-idioms.html).

###Other versions

The [`src`](src) directory contains many other intermediate or testing versions of our bot. A few are named after strategies we saw other teams using. Numbers in bot names are the dates in January on which the version was made. 

* The `supercowpowers` versions are direct ancestors of our final bot and were written to imitate "Super Cow Powers'" early bot. 
* We tested a lot against our turtle bots `ttm_heal`, `ttm_dense_9`, and `ttm_dense_pure_9`. 
* The `simplesoldier18` bot is an imitation of the scout-based zombie-pulling strategy we saw "The Simple Soldier" using on the scrimmage server; this strategy was used very successfully in the final tournament by "foundation." 
* A frequent test opponent was `felix15`, our imitation of the aggressive soldier/viper strategy we first saw used very well by "Felix & The Buggers"; this strategy was very popular in the final tournament. 

###Other Battlecode 2016 repositories

* [mid high diamonds](https://bitbucket.org/maxnelso/battlecode2016) (5th-6th place)
