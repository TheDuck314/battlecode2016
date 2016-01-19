package seeding_base18;

import battlecode.common.*;

public class BotSoldier extends Globals {
	public static void loop() {
		Debug.init("archon");		
		FastMath.initRand(rc);
		rc.emptySignalQueue(); // flush signal backlog
		while (true) {
			int startTurn = rc.getRoundNum();
			try {
				Globals.update();
				turn();
				setIndicator();

			} catch (Exception e) {
				e.printStackTrace();
			}
			int endTurn = rc.getRoundNum();
			if (startTurn != endTurn) {
				System.out.println("OVER BYTECODE LIMIT");
			}
			Clock.yield();
		}
	}
	
	private static MapLocation attackTarget = null;
	
	private static Direction wanderDirection = null;

	private static boolean inHealingState = false;

	private static int lastKnownArchonId = -1;
	private static MapLocation lastKnownArchonLocation = null;
	private static int lastKnownArchonLocationRound = -999999;
	
	private static int numTurnsBlocked = 0;
	
	private static MapLocation closestEnemyTurretLocation = null;
	
	private static MapLocationHashSet destroyedZombieDens = new MapLocationHashSet();
	private static boolean isAttackingZombieDen = false;
	

	private static void turn() throws GameActionException {
		processSignals();

		manageHealingState();
		
		if (tryToMicro()) {
			return;
		}
		
		Radar.removeDistantEnemyTurrets(9 * RobotType.SCOUT.sensorRadiusSquared);
		
		FastTurretInfo closestEnemyTurret = Radar.findClosestEnemyTurret();
		if (closestEnemyTurret != null) {
			closestEnemyTurretLocation = closestEnemyTurret.location;
		} else {
			closestEnemyTurretLocation = null;
		}
		
//		Debug.indicate("micro", 2, "inHealingState = " + inHealingState);
		if (inHealingState) {
			if (tryToHealAtArchon()) {
				return;
			}
		}
		
		lookForAttackTarget();
	}
	
	private static void setIndicator() {
		if (myID % 2 != 0) {
//			Debug.indicateDot("dens", here, 100, 0, 0);
			if (attackTarget != null) {
//				Debug.indicateLine("dens", here, attackTarget, 100, 0, 0);
			}
		} else {
//			Debug.indicateDot("dens", here, 0, 100, 0);
			if (attackTarget != null) {
//				Debug.indicateLine("dens", here, attackTarget, 0, 100, 0);
			}
		}
	}
	
	private static void addAttackTarget(MapLocation targetNew, boolean isZombieDen) {
		if (isZombieDen && destroyedZombieDens.contains(targetNew)) {
			return;
		}
		if (attackTarget == null) {
			attackTarget = targetNew;
			isAttackingZombieDen = isZombieDen;
		}
		if (myID % 2 == 0) {
			if (here.distanceSquaredTo(targetNew) < here.distanceSquaredTo(attackTarget)) {
				isAttackingZombieDen = isZombieDen;
				attackTarget = targetNew;
			}
		} else {
			if (!isAttackingZombieDen && isZombieDen) {
				isAttackingZombieDen = true;
				attackTarget = targetNew;
			} else if (isAttackingZombieDen && !isZombieDen) {
				return;
			} else if (here.distanceSquaredTo(targetNew) < here.distanceSquaredTo(attackTarget)) {
				isAttackingZombieDen = isZombieDen;
				attackTarget = targetNew;
			}
		}
	}

	private static void processSignals() throws GameActionException {
		Radar.clearEnemyCache();

		Signal[] signals = rc.emptySignalQueue();
		for (Signal sig : signals) {
			if (sig.getTeam() != us) continue;

			int[] data = sig.getMessage();
			if (data != null) {
				switch(data[0] & Messages.CHANNEL_MASK) {
				/*case Messages.CHANNEL_ATTACK_TARGET:
					MapLocation suggestedTarget = Messages.parseAttackTarget(data);
					if (attackTarget == null || here.distanceSquaredTo(suggestedTarget) < here.distanceSquaredTo(attackTarget)) {
						attackTarget = suggestedTarget;
						attackTargetReceivedRound = rc.getRoundNum();
					}
					break;*/
				case Messages.CHANNEL_RADAR:
					MapLocation closest = Messages.getClosestRadarHit(data, sig.getLocation());
					addAttackTarget(closest, false);
					break;
					
				case Messages.CHANNEL_DEN_ATTACK_COMMAND:
					MapLocation denTarget = Messages.parseDenAttackCommand(data);
					addAttackTarget(denTarget, true);
					break;
					
				case Messages.CHANNEL_ZOMBIE_DEN:
					MapLocation denLoc = Messages.parseZombieDenLocation(data);
					if (Messages.parseZombieDenWasDestroyed(data)) {
						destroyedZombieDens.add(denLoc);
						if (denLoc.equals(attackTarget) && isAttackingZombieDen) {
							isAttackingZombieDen = false;
							attackTarget = null;
						}
					}
					break;
					
				case Messages.CHANNEL_ARCHON_LOCATION:
					MapLocation archonLoc = Messages.parseArchonLocation(data);
					if (lastKnownArchonLocation == null 
							|| (lastKnownArchonLocationRound < rc.getRoundNum() - 50)
							|| here.distanceSquaredTo(lastKnownArchonLocation) > here.distanceSquaredTo(archonLoc)) {
						lastKnownArchonLocation = archonLoc;
						lastKnownArchonLocationRound = rc.getRoundNum();
					}
					break;
					
				case Messages.CHANNEL_ENEMY_TURRET_WARNING:
					Messages.processEnemyTurretWarning(data);
					break;
					
				case Messages.CHANNEL_ROBOT_LOCATION:
					Messages.processRobotLocation(sig, data);
					break;
					
				default:
				}
			}
		}
	}

	private static boolean tryToMicro() throws GameActionException {		
		RobotInfo[] visibleHostiles = rc.senseHostileRobots(here, mySensorRadiusSquared);
		if (visibleHostiles.length == 0) return false;

		if (rc.isCoreReady()) {
			double leftHealth = rc.getHealth() - rc.getViperInfectedTurns() * 2.0;
			for (RobotInfo h: visibleHostiles) {
				if (h.type.canAttack() && h.location.distanceSquaredTo(here) <= h.type.attackRadiusSquared) {
					leftHealth -= h.attackPower;
				}
			}
			if (leftHealth <= 0) {
				Debug.indicate("micro", 0, "Doomed by infection");
				if (tryChargeToEnemy()) {
					Debug.indicate("micro", 1, "Going for enemies");
					return true;
				}
				if (tryGoAwayFromAlly()) {
					Debug.indicate("micro", 1, "Cannot find enemy, keep distance from allies.");
					return true;
				}
			}
		}
		
		if (inHealingState) {
			// if we are in the healing state, then if we are under attack we should retreat.
			// but if coreDelay >= cooldown delay, then we can optionally attack			
			if (rc.isCoreReady()) {
				if (visibleHostiles.length > 0) {
					if (fleeInHealingState(visibleHostiles)) {
						Debug.indicate("micro", 0, "fleeing in healing state");
						return true;
					}
				}
			}
			if (rc.isWeaponReady() && rc.getCoreDelay() >= myType.cooldownDelay) {
				RobotInfo[] attackableHostiles = rc.senseHostileRobots(here, myAttackRadiusSquared);
				if (attackableHostiles.length > 0) {
					Debug.indicate("micro", 0, "attacking in healing state");
					chooseTargetAndAttack(attackableHostiles);
					return true;
				}
			}
			return false;
		}

		RobotInfo[] attackableHostiles = rc.senseHostileRobots(here, myAttackRadiusSquared);
		
		if (rc.isCoreReady()) {
			if (retreatIfOutnumbered(visibleHostiles)) {
				Debug.indicate("micro", 0, "retreating because outnumbered");
				return true;
			}
		}
		
		if (rc.isWeaponReady()) {
			if (attackableHostiles.length > 0) { // we can shoot someone
 			    // retreat if there is slow zombie adjacent to us
				if (rc.isCoreReady()) {
					if (retreatFromSlowZombiesIfNecessary()) {
						Debug.indicate("micro", 0, "retreating from slow zombies");
						return true;
					}
				}
				// otherwise just shoot someone
				Debug.indicate("micro", 0, "attacking");
				chooseTargetAndAttack(attackableHostiles);
				return true;
			}
			// we can't shoot anyone. try to help an ally or attack a helpless target
			if (rc.isCoreReady()) {
				if (tryMoveToHelpAlly(visibleHostiles)) {
					Debug.indicate("micro", 0, "moving to help ally");
					return true;
				}
				if (tryMoveToAttackHelplessTarget(visibleHostiles)) {
					Debug.indicate("micro", 0, "moving to attack helpless target");
					return true;
				}
			}
		} else if (rc.isCoreReady()) { // weapon is not ready
			// since our weapon is on cooldown, we should retreat if there is
			// ANYONE who can attack and is closer than max attack range. Next
			// turn our weapon will be ready again and we can attack them
			// from a safer distance
			if (attackableHostiles.length > 0) {
				if (tryToBackUpToMaintainMaxRange(attackableHostiles)) {
					Debug.indicate("micro", 0, "backing up to maintain max range");
					return true;
				}
				if (tryMoveToAttackHelplessNonDenTarget(visibleHostiles)) {
					Debug.indicate("micro", 0, "moving to attack helpless non den target");
					return true;
				}
				if (tryGetCloserToZombieDen(attackableHostiles)) {
					Debug.indicate("micro", 0, "getting closer to zombie den");
					return true;
				}
				return true; // we are fighting, don't move
			}
			
			// otherwise try to help an ally or attack a helpless target
			if (tryMoveToHelpAlly(visibleHostiles)) {
				Debug.indicate("micro", 0, "moving to help ally");
				return true;
			}
			if (tryMoveToEngageOutnumberedEnemy(visibleHostiles)) {
				Debug.indicate("micro", 0, "moving to engage outnumbered enemy");
				return true;
			}
			if (tryMoveToAttackHelplessTarget(visibleHostiles)) {
				Debug.indicate("micro", 0, "moving to attack helpless target");
				return true;
			}
		}
		
		return false;
	}
	
	private static boolean tryChargeToEnemy() throws GameActionException {
		RobotInfo[] enemies = rc.senseNearbyRobots(mySensorRadiusSquared, them);
		MapLocation target = here;
		for (RobotInfo enemy : enemies) {
			target = target.add(here.directionTo(enemy.location));
		}
		Direction dir = here.directionTo(target);
		if (Util.isGoodDirection(dir)) {
			return Nav.tryMoveInDirection(dir);
		}
		return false;
	}
	
	private static boolean tryGoAwayFromAlly() throws GameActionException {
		RobotInfo[] allies = rc.senseNearbyRobots(mySensorRadiusSquared, us);
		MapLocation target = here;
		for (RobotInfo ally : allies) {
			target = target.add(here.directionTo(ally.location));
		}
		Direction dir = target.directionTo(here);
		if (Util.isGoodDirection(dir)) {
			return Nav.tryMoveInDirection(dir);
		}
		return false;
	}

	private static double enemyScore(RobotType type, double health) {
		switch(type) {
		case ARCHON:
			return 0.0001;
		case ZOMBIEDEN:
			return 0.00001;
			
		case SCOUT:
			return 0.1 / (health * 1); // scouts are low priority
		case TTM:
			return 0.5 * RobotType.TURRET.attackPower / (health * RobotType.TURRET.attackDelay);
		case TURRET:
			return type.attackPower / (health * type.attackDelay);
		case VIPER:
			// vipers are dangerous and their attackPower and attackDelay
			// do not reflect their true strength
			return 10 / (health * 1);

		default:
			return type.attackPower / (health * type.attackDelay);
		}
	}
	
	private static void chooseTargetAndAttack(RobotInfo[] targets) throws GameActionException {
		RobotInfo bestTarget = null;
		double bestScore = -99;
		for (RobotInfo target : targets) {
			double score = enemyScore(target.type, target.health);
			if (score > bestScore) {
				bestScore = score;
				bestTarget = target;
			}
		}
		if (bestTarget != null) {
//			Debug.indicate("micro", 0, "attacking " + bestTarget.type + " at " + bestTarget.location);
			rc.attackLocation(bestTarget.location);
			if (bestTarget.type == RobotType.ZOMBIEDEN) {
				if (rc.senseRobotAtLocation(bestTarget.location) == null) {
					rc.broadcastSignal(20000);
				}
			}
		}
	}
	
	private static boolean retreatIfOutnumbered(RobotInfo[] visibleHostiles) throws GameActionException {
		RobotInfo closestHostileThatAttacksUs = null;
		int closestDistSq = Integer.MAX_VALUE;
		int numHostilesThatAttackUs = 0;
		for (RobotInfo hostile : visibleHostiles) {
			if (hostile.type.canAttack()) {
				int distSq = hostile.location.distanceSquaredTo(here);
				if (distSq <= hostile.type.attackRadiusSquared) {
					if (distSq < closestDistSq) {
						closestDistSq = distSq;
						closestHostileThatAttacksUs = hostile;
					}
					numHostilesThatAttackUs += 1;
				}
			}
		}
		
		if (numHostilesThatAttackUs == 0) {
			return false;
		}
		
		int numAlliesAttackingClosestHostile = 0;
		if (here.distanceSquaredTo(closestHostileThatAttacksUs.location) <= myAttackRadiusSquared) {
			numAlliesAttackingClosestHostile += 1;
		}
		RobotInfo[] nearbyAllies = rc.senseNearbyRobots(closestHostileThatAttacksUs.location, 13, us);
		for (RobotInfo ally : nearbyAllies) {
			if (ally.type.canAttack()) {
				if (ally.location.distanceSquaredTo(closestHostileThatAttacksUs.location)
						<= ally.type.attackRadiusSquared) {
					numAlliesAttackingClosestHostile += 1;
				}
			}
		}
		
		if (numAlliesAttackingClosestHostile > numHostilesThatAttackUs) {
			return false;
		} 
		if (numAlliesAttackingClosestHostile == numHostilesThatAttackUs) {
			if (numHostilesThatAttackUs == 1) {
				if (rc.getHealth() >= closestHostileThatAttacksUs.health) {
					return false;
				}
			} else {
				return false;
			}
		}
		
		// we have to retreat
		MapLocation retreatTarget = here;
		for (RobotInfo hostile : visibleHostiles) {
			if (!hostile.type.canAttack()) continue;			
			retreatTarget = retreatTarget.add(hostile.location.directionTo(here));
		}
		if (!here.equals(retreatTarget)) {
			Direction retreatDir = here.directionTo(retreatTarget);
			return Nav.tryHardMoveInDirection(retreatDir);
		}
		return false;
	}
	
	private static boolean tryMoveToEngageOutnumberedEnemy(RobotInfo[] visibleHostiles) throws GameActionException {
		RobotInfo closestHostile = Util.closest(visibleHostiles);
		if (closestHostile == null) return false;
		
		int numNearbyHostiles = 0;
		for (RobotInfo hostile : visibleHostiles) {
			if (hostile.type.canAttack()) {
				if (hostile.location.distanceSquaredTo(closestHostile.location) <= 24) {
					numNearbyHostiles += 1;
				}
			}
		}
		
		int numNearbyAllies = 1;
		RobotInfo[] nearbyAllies = rc.senseNearbyRobots(closestHostile.location, 24, us);
		for (RobotInfo ally : nearbyAllies) {
			if (ally.type.canAttack()) {
				numNearbyAllies += 1;
			}
		}
		
		if (numNearbyAllies > numNearbyHostiles 
				|| (numNearbyHostiles == 1 && rc.getHealth() > closestHostile.health)) {
			return Nav.tryMoveInDirection(here.directionTo(closestHostile.location));
		}
		return false;
	}
	
	private static boolean tryGetCloserToZombieDen(RobotInfo[] attackableHostiles) throws GameActionException {
		RobotInfo closestHostile = Util.closest(attackableHostiles);
		if (closestHostile.type == RobotType.ZOMBIEDEN) {
			int distSq = here.distanceSquaredTo(closestHostile.location);
			if (distSq > 8) {
				return Nav.tryMoveInDirection(here.directionTo(closestHostile.location));
			}
		}
		return false;
	}

	// prefer to retreat along orthogonal directions since these give smaller delays
	private static Direction[] retreatDirs = 
		{ Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST,
				Direction.NORTH_EAST, Direction.SOUTH_EAST, Direction.SOUTH_WEST, Direction.NORTH_WEST };
	
	// This function is called if we CAN shoot but we have to decide
	// whether to retreat instead.
	private static boolean retreatFromSlowZombiesIfNecessary() throws GameActionException {
		RobotInfo[] zombies = rc.senseNearbyRobots(myAttackRadiusSquared, Team.ZOMBIE);
//		Debug.indicate("micro", 1, "hello from retreatFromSlowZombiesIfNecessary");
		if (zombies.length == 0) return false;
		
		// check if we are being attacked by a non-fast zombie but could move 
		// out of its attack range.
		boolean mustRetreat = false;

		boolean[] directionIsAdjacentToZombie = new boolean[8];
		
		for (RobotInfo zombie : zombies) {
			switch (zombie.type) {
			case BIGZOMBIE:
			case STANDARDZOMBIE:
				// we can kite big zombies and standard zombies
				if (here.isAdjacentTo(zombie.location)) {
					mustRetreat = true;
					Direction zombieDir = here.directionTo(zombie.location);
					int zombieDirOrdinal = zombieDir.ordinal();
					directionIsAdjacentToZombie[zombieDirOrdinal] = true;
					directionIsAdjacentToZombie[(zombieDirOrdinal+1)%8] = true;
					directionIsAdjacentToZombie[(zombieDirOrdinal+7)%8] = true;
					if (!zombieDir.isDiagonal()) {
						directionIsAdjacentToZombie[(zombieDirOrdinal+2)%8] = true;
						directionIsAdjacentToZombie[(zombieDirOrdinal+6)%8] = true;
					}
				}
				break;

			case RANGEDZOMBIE:
				// ranged zombies have a short cooldown delay, so kiting them
				// isn't useful. However if there are big zombies or standard zombies
				// attacking us then we should kite.
				break;
				
			case FASTZOMBIE:
				if (here.distanceSquaredTo(zombie.location) <= zombie.type.attackRadiusSquared) {
					// if we are being attacked by a fast zombie we should probably just
					// stand and fight, since fast zombies will get more hits on us if we try
					// to kite
					return false; 
				}
				break;

			case ZOMBIEDEN: // zombie dens are not dangerous
		    default:	
			}
		}
		
		if (mustRetreat) {
//			Debug.indicate("micro", 2, "");
			//for (int i = 0; i < 8; ++i) {
//				Debug.indicateAppend("micro", 2, "" + Direction.values()[i] + ": " + directionIsAdjacentToZombie[i]);;
			//}

			// try to find a direction that isn't adjacent to any big or standard zombies
			for (Direction dir : retreatDirs) {
				if (rc.canMove(dir) && !directionIsAdjacentToZombie[dir.ordinal()]) {
					rc.move(dir);
//					Debug.indicate("micro", 0, "retreating from slow zombie(s)");
					return true;
				}
			}
		}
		return false;
	}
	
	private static boolean tryToBackUpToMaintainMaxRange(RobotInfo[] attackableHostiles) throws GameActionException {
		int closestHostileDistSq = Integer.MAX_VALUE;
		for (RobotInfo hostile : attackableHostiles) {
			if (!hostile.type.canAttack()) continue;
			int distSq = here.distanceSquaredTo(hostile.location);
			if (distSq < closestHostileDistSq) {
				closestHostileDistSq = distSq;
			}
		}
		
		if (closestHostileDistSq > 5) return false;
		
		Direction bestRetreatDir = null;
		int bestDistSq = closestHostileDistSq;
		boolean foundOrthogonalRetreatDir = false;
		for (Direction dir : retreatDirs) {
			if (!rc.canMove(dir)) continue;
			if (foundOrthogonalRetreatDir && dir.isDiagonal()) continue;
			MapLocation dirLoc = here.add(dir);			
			int smallestDistSq = Integer.MAX_VALUE;
			for (RobotInfo hostile : attackableHostiles) {
				if (!hostile.type.canAttack()) continue;
				int distSq = hostile.location.distanceSquaredTo(dirLoc);
				if (distSq < smallestDistSq) {
					smallestDistSq = distSq;
				}
			}
			if (smallestDistSq > bestDistSq) {
				bestDistSq = smallestDistSq;
				bestRetreatDir = dir;
				if (!dir.isDiagonal() && smallestDistSq >= 4) {
					foundOrthogonalRetreatDir = true;
				}
			}
		}
		if (bestRetreatDir != null) {
//			Debug.indicate("micro", 0, "backing up to maintain max range");
			rc.move(bestRetreatDir);
			return true;
		}
		return false;
	}

	private static boolean fleeInHealingState(RobotInfo[] visibleHostiles) throws GameActionException {
		boolean mustRetreat = false;
		MapLocation retreatTarget = here;
		for (RobotInfo hostile : visibleHostiles) {
			RobotType hostileType = hostile.type;
			if (!hostileType.canAttack()) continue;			
			mustRetreat = true;
			retreatTarget = retreatTarget.add(hostile.location.directionTo(here));
		}
		if (closestEnemyTurretLocation != null) {
			if (here.distanceSquaredTo(closestEnemyTurretLocation) <= RobotType.TURRET.attackRadiusSquared) {
				mustRetreat = true;
				retreatTarget = retreatTarget.add(closestEnemyTurretLocation.directionTo(here));
			}
		}
		if (mustRetreat) {
			if (!here.equals(retreatTarget)) {
				Direction retreatDir = here.directionTo(retreatTarget);
				Nav.tryHardMoveInDirection(retreatDir);
				return true;
			}
		}
		return false;
	}
	
	private static boolean retreatIfNecessary(RobotInfo[] visibleHostiles) throws GameActionException {
		boolean mustRetreat = false;
		for (RobotInfo hostile : visibleHostiles) {
			RobotType hostileType = hostile.type;
			if (!hostileType.canAttack()) {
				continue;				
			}
			int distSq = here.distanceSquaredTo(hostile.location);
			if (distSq <= hostileType.attackRadiusSquared) {
				mustRetreat = true;
				break;
			}
		}
		
		if (!mustRetreat) return false;

		Direction[] dirs = Direction.values();		
		MapLocation[] dirLocs = new MapLocation[8];
		int[] penalties = new int[8];
		boolean[] canMove = new boolean[8];
		for (int d = 0; d < 8; ++d) {
			dirLocs[d] = here.add(dirs[d]);
			canMove[d] = rc.canMove(dirs[d]);
		}

		for (RobotInfo hostile : visibleHostiles) {
			RobotType hostileType = hostile.type;
			if (!hostileType.canAttack()) {
				continue;				
			}
			MapLocation hostileLoc = hostile.location;
			int hostileRange = hostileType.attackRadiusSquared;
			for (int d = 0; d < 8; ++d) {
				if (canMove[d] && hostileLoc.distanceSquaredTo(dirLocs[d]) <= hostileRange) {
					penalties[d]++;
				}
			}
		}

		Direction bestDir = null;
		int minPenalty = 999999;
		for (int d = 0; d < 8; ++d) {
			if (canMove[d] && penalties[d] < minPenalty) {
				minPenalty = penalties[d];
				bestDir = dirs[d];
			}
		}
		if (bestDir != null) {
//			Debug.indicate("micro", 0, "retreating");
			rc.move(bestDir);
			return true;
		}

		return false;
	}
	
	private static boolean tryMoveToHelpAlly(RobotInfo[] visibleHostiles) throws GameActionException {
		MapLocation closestHostile = Util.closest(visibleHostiles).location;
		
		boolean allyIsFighting = false;
		RobotInfo[] alliesAroundHostile = 
				rc.senseNearbyRobots(closestHostile, myAttackRadiusSquared, us);
		for (RobotInfo ally : alliesAroundHostile) {
			if (ally.type.canAttack()) {
				if (ally.location.distanceSquaredTo(closestHostile) <= ally.type.attackRadiusSquared) {
					allyIsFighting = true;
					break;
				}
			}
		}
		
		if (allyIsFighting) {
			if (Nav.tryMoveInDirection(here.directionTo(closestHostile))) {
//				Debug.indicate("micro", 0, "moving to help ally fight hostile at " + closestHostile);
				return true;
			}
		}
		return false;
	}
	
	private static boolean tryMoveToAttackHelplessTarget(RobotInfo[] visibleHostiles) throws GameActionException {
		RobotInfo closestHostile = Util.closest(visibleHostiles);
		if (closestHostile.type.canAttack()) {
			return false;
		}
		
		if (Nav.tryMoveInDirection(here.directionTo(closestHostile.location))) {
//			Debug.indicate("micro", 0, "moving to attack helpless " + closestHostile.type + " at " + closestHostile.location);
			return true;
		}
		return false;
	}
	
	private static boolean tryMoveToAttackHelplessNonDenTarget(RobotInfo[] visibleHostiles) throws GameActionException {
		RobotInfo closestHostile = Util.closest(visibleHostiles);
		if (closestHostile.type.canAttack() || closestHostile.type == RobotType.ZOMBIEDEN) {
			return false;
		}
		
		if (Nav.tryMoveInDirection(here.directionTo(closestHostile.location))) {
//			Debug.indicate("micro", 0, "moving to attack helpless " + closestHostile.type + " at " + closestHostile.location);
			return true;
		}
		return false;
	}
	
	private static void manageHealingState() {
		if (rc.getHealth() < myType.maxHealth / 2) {
			inHealingState = true;
		}
		if (rc.getHealth() == myType.maxHealth) {
			inHealingState = false;
		}
	}
	
	private static void locateNearestArchon() throws GameActionException {
		// first look for our favorite archon
		if (rc.canSenseRobot(lastKnownArchonId)) {
			RobotInfo archon = rc.senseRobot(lastKnownArchonId);
			lastKnownArchonLocation = archon.location;
			lastKnownArchonLocationRound = rc.getRoundNum();
			return;
		}
		
		// else look for any nearby archon
		RobotInfo[] nearbyAllies = rc.senseNearbyRobots(mySensorRadiusSquared, us);
		for (RobotInfo ally : nearbyAllies) {
			if (ally.type == RobotType.ARCHON) {
				lastKnownArchonLocation = ally.location;
				lastKnownArchonLocationRound = rc.getRoundNum();
				lastKnownArchonId = ally.ID;
				return;
			}
		}
		
		// else hope that we have gotten an archon location broadcast
	}
	
	private static boolean tryToHealAtArchon() throws GameActionException {
		if (!rc.isCoreReady()) return false;
		
		locateNearestArchon();
		
		if (lastKnownArchonLocation == null) {
			return false;
		}
		
		Nav.swarmToAvoidingArchonsAndTurret(lastKnownArchonLocation, closestEnemyTurretLocation);
		return true;
	}
	
	private static void lookForAttackTarget() throws GameActionException {
		if (!rc.isCoreReady()) return;
		
		if (attackTarget == null) {
//			Debug.indicate("radar", 0, "lookForAttackTarget: attackTarget == null, numCachedEnemies = " + Radar.numCachedEnemies);
			MapLocation closest = null;
			int bestDistSq = Integer.MAX_VALUE;
			for (int i = 0; i < Radar.numCachedEnemies; ++i) {
				FastRobotInfo hostile = Radar.enemyCache[i];
				int distSq = here.distanceSquaredTo(hostile.location);
				if (distSq < bestDistSq) {
					bestDistSq = distSq;
					closest = hostile.location;
					isAttackingZombieDen = hostile.type == RobotType.ZOMBIEDEN;
				}
			}
			attackTarget = closest;
//			Debug.indicate("radar", 1, "now attackTarget = " + attackTarget);
		}

		// Not very good against felix
//		if (attackTarget == null) {
//			attackTarget = Radar.closestEnemyArchonLocation();
//			if (attackTarget != null) {
//				Debug.indicate("archon", 2, "No target, going to attack enemy archon at " + attackTarget);
//				// Debug.println("archon", "attackTarget=" + attackTarget);
//			}
//		}
		
		if (attackTarget != null) {
			if (rc.canSenseLocation(attackTarget)) {
				RobotInfo targetInfo = rc.senseRobotAtLocation(attackTarget);
				if (targetInfo == null || targetInfo.team == us) {
					if (isAttackingZombieDen) {
						destroyedZombieDens.add(attackTarget);
					}
					attackTarget = null;
					isAttackingZombieDen = false;
				} else if (targetInfo.type != RobotType.ZOMBIEDEN) {
					isAttackingZombieDen = false;
				}
			}
		}
		
		if (attackTarget != null) {
			//if (Nav.goToDirect(attackTarget)) {
			if (Nav.goToDirectSafelyAvoidingTurret(attackTarget, closestEnemyTurretLocation)) {
				numTurnsBlocked = 0;
//				Debug.indicate("block", 0, "not blocked!");
			} else {
				numTurnsBlocked += 1;
//				Debug.indicate("block", 0, "blocked! numTurnsBlocked = " + numTurnsBlocked);
				if (numTurnsBlocked >= 40) {
//					Debug.indicate("block", 1, "waited too long. setting attackTarget = null");
					attackTarget = null;
					isAttackingZombieDen = false;
					numTurnsBlocked = 0;
				}
			}
			return;
		}
		
		/*if (wanderDirection == null) {
			wanderDirection = Direction.values()[FastMath.rand256() % 8];
		}
		
		MapLocation fakeTarget = here.add(wanderDirection, 10);
		
//		Debug.indicateDot("micro", here, 0, 100, 0);
		
		if (Nav.goToDirectSafelyAvoidingTurret(fakeTarget, closestEnemyTurretLocation)) {
//			Debug.indicate("micro", 0, "wandering");
//			Debug.indicateLine("micro", here, fakeTarget, 100, 100, 0);
		} else {
			wanderDirection = Direction.values()[FastMath.rand256() % 8];
		}*/
		tryToHealAtArchon();
	}
}
