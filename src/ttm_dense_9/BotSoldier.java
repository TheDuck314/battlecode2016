package ttm_dense_9;

import battlecode.common.*;

public class BotSoldier extends Globals {
	
	public static int motherId = 0;
	public static MapLocation motherLocation = null;
	public static boolean isHappyShooting = false;

	public static void loop() {
		updateMotherId(8);
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
	
	public static void updateMotherId(int range) {
		RobotInfo[] infos = rc.senseNearbyRobots(range, us);
		for (RobotInfo info : infos) {
			if (info.type == RobotType.ARCHON) {
				motherId = info.ID;
				motherLocation = info.location;
				break;
			}
		}
	}
	
	public static void updateMotherLocation() throws GameActionException {
		if (rc.canSenseRobot(motherId)) {
			motherLocation = rc.senseRobot(motherId).location;
		} else {
			updateMotherId(mySensorRadiusSquared);
			Bug.reset();
			DBug.reset();
		}
	}
	
	public static boolean shootEnemy() throws GameActionException {
		MapLocation bestLoc = null;
		double bestScore = 100000000;
		RobotInfo[] infos = rc.senseHostileRobots(here, myAttackRadiusSquared);
		for (RobotInfo info : infos) {
			double score = info.health / (info.attackPower + 1);
			if (score < bestScore) {
				bestLoc = info.location;
				bestScore = score;
			}
		}
		if (bestLoc != null) {
			rc.attackLocation(bestLoc);
			return true;
		}
		return false;
	}

	public static void followMother() throws GameActionException {
		if (rc.isCoreReady()) {
			updateMotherLocation();
			DBug.goTo(motherLocation);
		}
	}
	
	private static void turn() throws GameActionException {
		processSignals();
		
		if (tryToMicro()) {
			return;
		}

		if (rc.getHealth() != myType.maxHealth || !isHappyShooting) {
			followMother();
		}
	}
	
	private static MapLocation attackTarget = null;
	private static int attackTargetReceivedRound = -9999;
	
	private static Direction wanderDirection = null;

	private static boolean inHealingState = false;

	private static int lastKnownArchonId = -1;
	private static MapLocation lastKnownArchonLocation = null;
	private static int lastKnownArchonLocationRound = -999999;
	
	private static void processSignals() {
		Signal[] signals = rc.emptySignalQueue();
		for (Signal sig : signals) {
			if (sig.getTeam() != us) continue;

			int[] data = sig.getMessage();
			if (data != null) {
				switch(data[0] & Messages.CHANNEL_MASK) {
				case Messages.CHANNEL_ATTACK_TARGET:
					MapLocation suggestedTarget = Messages.parseAttackTarget(data);
					if (attackTarget == null || here.distanceSquaredTo(suggestedTarget) < here.distanceSquaredTo(attackTarget)) {
						attackTarget = suggestedTarget;
						attackTargetReceivedRound = rc.getRoundNum();
					}
					break;
					
				case Messages.CHANNEL_ARCHON_LOCATION:
					MapLocation archonLoc = Messages.parseArchonLocation(data);
					Debug.indicate("heal", 2, "got archonLoc = " + archonLoc);
					if (lastKnownArchonLocation == null 
							|| (lastKnownArchonLocationRound < rc.getRoundNum() - 50)
							|| here.distanceSquaredTo(lastKnownArchonLocation) > here.distanceSquaredTo(archonLoc)) {
						lastKnownArchonLocation = archonLoc;
						lastKnownArchonLocationRound = rc.getRoundNum();
						Debug.indicateAppend("heal", 2, "; new best");
					}
					break;
					
				default:
				}
			}
		}
	}

	private static boolean tryToMicro() throws GameActionException {
		RobotInfo[] attackableHostiles = rc.senseHostileRobots(here, myAttackRadiusSquared);

		if (attackableHostiles.length > 0) {
			if (rc.isWeaponReady()) {	
				chooseTargetAndAttack(attackableHostiles);
			}
		}

		if (!rc.isCoreReady()) {
			return false;
		}
		
		RobotInfo[] visibleHostiles = rc.senseHostileRobots(here, mySensorRadiusSquared);
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
			if (Bug.tryMoveInDirection(here.directionTo(closestHostile))) {
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
		
		if (Bug.tryMoveInDirection(here.directionTo(closestHostile.location))) {
			Debug.indicate("micro", 0, "moving to attack helpless " + closestHostile.type + " at " + closestHostile.location);
			return true;
		}
		return false;
	}
}
