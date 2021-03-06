package radar_follow9;

import battlecode.common.*;

public class BotSoldier extends Globals {
	public static void loop() {
		Debug.init("radar");
		FastMath.initRand(rc);
		while (true) {
			try {
				Globals.update();
				turn();
			} catch (Exception e) {
				e.printStackTrace();
			}
			Clock.yield();
		}
	}
	
	private static MapLocation attackTarget = null;
	private static int attackTargetReceivedRound = -9999;
	
	private static Direction wanderDirection = null;

	private static boolean inHealingState = false;

	private static int lastKnownArchonId = -1;
	private static MapLocation lastKnownArchonLocation = null;
	private static int lastKnownArchonLocationRound = -999999;
	
	private static void turn() throws GameActionException {
		processSignals();

		manageHealingState();
		
		if (tryToMicro()) {
			return;
		}
		
		if (inHealingState) {
			if (tryToHealAtArchon()) {
				return;
			}
		}
		
		lookForZombieDens();
	}

	private static void processSignals() {
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
					if (attackTarget == null
							|| here.distanceSquaredTo(closest) < here.distanceSquaredTo(attackTarget)) {
						attackTarget = closest;
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
					
				default:
				}
			}
		}
	}

	private static boolean tryToMicro() throws GameActionException {		
		RobotInfo[] attackableHostiles = rc.senseHostileRobots(here, myAttackRadiusSquared);
		RobotInfo[] visibleHostiles = rc.senseHostileRobots(here, mySensorRadiusSquared);

		if (inHealingState) {
			// if we are in the healing state, then if we are under attack we should retreat.
			// but if coreDelay >= cooldown delay, then we can optionally attack			
			if (rc.isCoreReady()) {
				if (retreatIfNecessary(visibleHostiles)) {
				    return true;
				}
			} else if (rc.isWeaponReady() && rc.getCoreDelay() >= myType.cooldownDelay) {
				if (attackableHostiles.length > 0) {
					chooseTargetAndAttack(attackableHostiles);
					return true;
				}
			}
			return false;
		}
		
		if (attackableHostiles.length > 0) {
			if (rc.isWeaponReady()) {	
				chooseTargetAndAttack(attackableHostiles);
			}
		}

		if (!rc.isCoreReady()) {
			return false;
		}
		
		if (visibleHostiles.length == 0) {
			return false;
		}		
		if (retreatIfNecessary(visibleHostiles)) {
			return true;
		}
		if (attackableHostiles.length > 0) {
			return true; // if in combat, stay and fight (since we decided not to retreat)
		}
		if (tryHelpAlly(visibleHostiles)) {
			return true;
		}
		if (tryAttackHelplessTarget(visibleHostiles)) {
			return true;
		}
		
		return false;
	}
	
	private static void chooseTargetAndAttack(RobotInfo[] targets) throws GameActionException {
		RobotInfo bestTarget = null;
		double minHealth = Double.MAX_VALUE;
		for (RobotInfo target : targets) {
			if (target.health < minHealth) {
				minHealth = target.health;
				bestTarget = target;
			}
		}
		if (bestTarget != null) {
			Debug.indicate("micro", 0, "attacking " + bestTarget.type);
			rc.attackLocation(bestTarget.location);
		}
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
			Debug.indicate("micro", 0, "retreating");
			rc.move(bestDir);
			return true;
		}

		return false;
	}
	
	private static boolean tryHelpAlly(RobotInfo[] visibleHostiles) throws GameActionException {
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
				Debug.indicate("micro", 0, "helping ally fight hostile at " + closestHostile);
				return true;
			}
		}
		return false;
	}
	
	private static boolean tryAttackHelplessTarget(RobotInfo[] visibleHostiles) throws GameActionException {
		RobotInfo closestHostile = Util.closest(visibleHostiles);
		if (closestHostile.type.canAttack()) {
			return false;
		}
		
		if (Nav.tryMoveInDirection(here.directionTo(closestHostile.location))) {
			Debug.indicate("micro", 0, "moving to attack helpless " + closestHostile.type + " at " + closestHostile.location);
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
		
		Nav.swarmToAvoidingArchons(lastKnownArchonLocation);
		return true;
	}
	
	
	private static void lookForZombieDens() throws GameActionException {
		if (!rc.isCoreReady()) return;
		
		if (attackTarget == null) {
			Debug.indicate("radar", 0, "lookForZombieDens: attackTarget == null, numCachedEnemies = " + Radar.numCachedEnemies);
			MapLocation closest = null;
			int bestDistSq = Integer.MAX_VALUE;
			for (int i = 0; i < Radar.numCachedEnemies; ++i) {
				FastRobotInfo hostile = Radar.enemyCache[i];
				int distSq = here.distanceSquaredTo(hostile.location);
				if (distSq < bestDistSq) {
					bestDistSq = distSq;
					closest = hostile.location;
				}
			}
			attackTarget = closest;
			Debug.indicate("radar", 1, "now attackTarget = " + attackTarget);
		}

		if (attackTarget != null) {
			if (rc.canSenseLocation(attackTarget)) {
				RobotInfo targetInfo = rc.senseRobotAtLocation(attackTarget);
				if (targetInfo == null || targetInfo.team == us) {
					attackTarget = null;
				}
			}
		}
		
		if (attackTarget != null) {
			Nav.goToDirect(attackTarget);
			return;
		}
		
		if (wanderDirection == null || !rc.canMove(wanderDirection)) {
			wanderDirection = Direction.values()[FastMath.rand256() % 8];
		}
		
		if (rc.canMove(wanderDirection)) {
			Debug.indicate("micro", 0, "wandering");
			rc.move(wanderDirection);
		}
	}
}
