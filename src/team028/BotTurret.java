package team028;

import battlecode.common.*;

public class BotTurret extends Globals {
	public static void loop() {
		Debug.init("spotting");
		while (true) {
			try {
				Globals.update();
				processSignals();
				if (rc.getType() == RobotType.TURRET) {
				    turnTurret();
				} else {
					turnTTM();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			Clock.yield();
		}
	}
	
	private static MapLocation attackTarget = null;
	private static int attackTargetReceivedRound = -9999;	

	private static int lastKnownArchonId = -1;
	private static MapLocation lastKnownArchonLocation = null;
	private static int lastKnownArchonLocationRound = -999999;
	
	private static void turnTurret() throws GameActionException {
		if (!rc.isWeaponReady() && !rc.isCoreReady()) return;
		
		RobotInfo[] attackableEnemies = rc.senseHostileRobots(here, myAttackRadiusSquared);
		if (attackableEnemies.length > 0) {
			if (rc.isWeaponReady()) {
				attackAnEnemy(attackableEnemies);
				return;
			}
		} else {
			if (rc.isCoreReady()) {
				rc.pack();	
				return;
			}
		}
	}
	
	private static void turnTTM() throws GameActionException {
		RobotInfo[] attackableEnemies = rc.senseHostileRobots(here, myAttackRadiusSquared);
		if (attackableEnemies.length > 0) {
			rc.unpack();
			return;
		}
		
		lookForAFight();
	}

	public static boolean attackAnEnemy(RobotInfo[] attackableEnemies) throws GameActionException {
		MapLocation bestTarget = null;
		double minHealth = Double.MAX_VALUE;
		for (RobotInfo hostile : attackableEnemies) {
			if (!rc.canAttackLocation(hostile.location)) continue;
			if (hostile.health < minHealth) {
				minHealth = hostile.health;
				bestTarget = hostile.location;
			}
			if (hostile.type == RobotType.ARCHON) {
				bestTarget = hostile.location;
				break;
			}
		}
		if (bestTarget != null) {
			rc.attackLocation(bestTarget);
			return true;
		}
		return false;
	}

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
	
	private static boolean tryGoToCenterOfMass() throws GameActionException {
		RobotInfo[] allies = rc.senseNearbyRobots(mySensorRadiusSquared, us);
		if (allies.length == 0) return false;
		int avgX = 0;
		int avgY = 0;
		int N = 0;
		for (RobotInfo ally : allies) {
			if (ally.type == RobotType.TURRET || ally.type == RobotType.TTM) continue;
			avgX += ally.location.x;
			avgY += ally.location.y;
			++N;
		}
		if (N == 0) return false;
		avgX /= N;
		avgY /= N;
		Bug.goTo(new MapLocation(avgX, avgY));
		return true;
	}
	
	private static void lookForAFight() throws GameActionException {
		if (!rc.isCoreReady()) return;

		if (attackTarget != null) {
			if (rc.canSenseLocation(attackTarget)) {
				RobotInfo targetInfo = rc.senseRobotAtLocation(attackTarget);
				if (targetInfo == null || targetInfo.team == us) {
					attackTarget = null;
				}
			}
		}
		
		if (attackTarget != null) {
			Bug.goTo(attackTarget);
			return;
		}
		
		if (tryGoToCenterOfMass()) {
			return;
		}
		
		if (lastKnownArchonLocation != null) {
			Bug.goTo(lastKnownArchonLocation);
		}
	}
}
