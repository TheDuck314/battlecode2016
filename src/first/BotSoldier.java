package first;

import battlecode.common.*;;

public class BotSoldier extends Globals {
	
	public static int motherId;
	public static MapLocation motherLocation;

	public static void loop() {
		setMotherId();
		while (true) {
			try {
			    turn();
			} catch (Exception e) {
				e.printStackTrace();
			}
			Clock.yield();
		}
	}
	
	public static void setMotherId() {
		RobotInfo[] infos = rc.senseNearbyRobots(8, us);
		for (RobotInfo info : infos) {
			if (info.type == RobotType.ARCHON) {
				motherId = info.ID;
				motherLocation = info.location;
				break;
			}
		}
	}
	
	private static void turn() {
	}
}
