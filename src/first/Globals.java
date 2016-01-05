package first;

import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.Team;

public class Globals {
	public static RobotController rc;
	public static MapLocation here;
	public static Team us;
	public static Team them;
	
	public static void init(RobotController theRC) {
		rc = theRC;
		us = rc.getTeam();
		them = us.opponent();
	}
	
	public static void update() {
		here = rc.getLocation();
	}
}
