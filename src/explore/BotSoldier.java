package explore;

import battlecode.common.*;
import explore.Messages.PartsLocation;;

public class BotSoldier extends Globals {
	
	public static int motherId = 0;
	public static MapLocation motherLocation = null;
	public static boolean isHappyShooting = false;
	
	private static int targetReceivedRound = -999999;
	private static MapLocation receivedTarget = null;
	
	public static void loop() {
		updateMotherId(8);
		while (true) {
			try {
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
		}
	}
	
	public static boolean shootEnemy() throws GameActionException {
		MapLocation bestLoc = null;
		RobotInfo[] infos = rc.senseNearbyRobots(myAttackRadiusSquared, them);
		for (RobotInfo info : infos) {
			if (info.type == RobotType.ARCHON && bestLoc == null) {
				if (bestLoc == null) {
					bestLoc = info.location;
				}
			} else {
				rc.attackLocation(info.location);
				return true;
			}
		}
		infos = rc.senseNearbyRobots(myAttackRadiusSquared, Team.ZOMBIE);
		for (RobotInfo info : infos) {
			 if (info.type == RobotType.ZOMBIEDEN) {
				 if (bestLoc == null) {
					 bestLoc = info.location;
				 }
			 } else {
				 rc.attackLocation(info.location);
				 return true;
			 }
		}
		if (bestLoc != null) {
			if (bestLoc.equals(receivedTarget)) {
				if (here.distanceSquaredTo(bestLoc) > 8) {
					// try to move closer to attack target so that more allies can hit it
					if (rc.isCoreReady() && Bug.tryMoveInDirection(here.directionTo(bestLoc))) {
						return true;
					}
				}
			}
			rc.attackLocation(bestLoc);
			return true;
		}
		return false;
	}

	public static void followMother() throws GameActionException {
		if (rc.isCoreReady()) {
			if (rc.canSenseRobot(motherId)) {
				receivedTarget = rc.senseRobot(motherId).location;
			}
			
			if (receivedTarget != null) {			
			    DirectNav.politelySwarmToAvoidingArchons(receivedTarget);
			}
		}
	}

	private static void processSignals() {
		Signal[] signals = rc.emptySignalQueue();
		for (Signal sig : signals) {
			if (sig.getTeam() != us) continue;

			int[] data = sig.getMessage();
			if (data != null) {
				switch(data[0] & Messages.CHANNEL_MASK) {
				case Messages.CHANNEL_ATTACK_TARGET:
					targetReceivedRound = rc.getRoundNum();
					receivedTarget = Messages.parseAttackTarget(data);
					break;
				default:
				}
			}
		}
	}

	private static void turn() throws GameActionException {
		Globals.update();
		if (rc.isWeaponReady()) {
			if (shootEnemy()) {
				isHappyShooting = true;
			} else {
				isHappyShooting = false;
			}
		}
		processSignals();
		if (rc.getHealth() != myType.maxHealth || !isHappyShooting) {
			followMother();
		}
	}
}
