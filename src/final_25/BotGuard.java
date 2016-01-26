package final_25;

import battlecode.common.*;;

public class BotGuard extends Globals {
	
	public static int motherId;
	public static MapLocation motherLocation;

	private static int lastKnownArchonId = -1;
	private static MapLocation lastKnownArchonLocation = null;
	private static int lastKnownArchonLocationRound = -999999;

	public static void loop() {
		rc.emptySignalQueue(); // flush signal backlog
		while (true) {
			int startTurn = rc.getRoundNum();
			try {
				Globals.update();
			    turn();
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
	
	private static void turn() throws GameActionException {
		processSignals();
		
		if (tryToMicro()) {
			return;
		}
		tryToHealAtArchon();
	}
	
	private static void processSignals() throws GameActionException {
		Radar.clearEnemyCache();

		Signal[] signals = rc.emptySignalQueue();
		for (Signal sig : signals) {
			if (sig.getTeam() != us) continue;

			int[] data = sig.getMessage();
			if (data != null) {
				switch(data[0] & Messages.CHANNEL_MASK) {
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
		RobotInfo[] visibleHostiles = rc.senseHostileRobots(here, mySensorRadiusSquared);
		if (visibleHostiles.length == 0) return false;
		
		RobotInfo[] attackableHostiles = rc.senseHostileRobots(here, myAttackRadiusSquared);
		if (attackableHostiles.length > 0) {
			if (rc.isWeaponReady()) {
				attackAHostile(attackableHostiles);
			}
		} else {
			if (rc.isCoreReady()) {
				RobotInfo closestHostile = Util.closest(visibleHostiles);
				Nav.tryMoveInDirection(here.directionTo(closestHostile.location));
			}
		}
		return true;
	}
	
	private static void attackAHostile(RobotInfo[] attackableHostiles) throws GameActionException {
		double minHealth = Double.MAX_VALUE;
		MapLocation target = null;
		for (RobotInfo hostile : attackableHostiles) {
			if (hostile.health < minHealth) {
				minHealth = hostile.health;
				target = hostile.location;
			}
		}
		if (target != null) {
			rc.attackLocation(target);
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
		
		Nav.swarmToAvoidingArchonsAndTurret(lastKnownArchonLocation, null);
		return true;
	}
}
