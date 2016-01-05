package first;

import battlecode.common.*;;

public class BotSoldier extends Globals {
	
	public static int motherId;
	public static MapLocation motherLocation;

	public static void loop() {
		updateMotherId();
		while (true) {
			try {
			    turn();
			} catch (Exception e) {
				e.printStackTrace();
			}
			Clock.yield();
		}
	}
	
	public static void updateMotherId() {
		RobotInfo[] infos = rc.senseNearbyRobots(8, us);
		for (RobotInfo info : infos) {
			if (info.type == RobotType.ARCHON) {
				motherId = info.ID;
				motherLocation = info.location;
				break;
			}
		}
	}
	
	public static void updateMotherLocation() throws GameActionException {
		motherLocation = rc.senseRobot(motherId).location;
	}
	
	private static void turn() throws GameActionException {
		update();
		updateMotherLocation();
		Bug.goTo(motherLocation);
	}
}
