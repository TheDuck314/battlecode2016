package ttm_dense_9;

import battlecode.common.*;

public class Globals {
	public static RobotController rc;
	public static MapLocation here;
	public static Team us;
	public static Team them;
	public static int myID;
	public static RobotType myType;
	public static int myAttackRadiusSquared;
	public static int mySensorRadiusSquared;
	
	public static boolean isGoodTurretLocation(MapLocation a) {
		return a.x % 2 == 0 && (a.x / 2 + a.y) % 2 == 0;
	}
	
	public static void init(RobotController theRC) {
		rc = theRC;
		us = rc.getTeam();
		them = us.opponent();
		myID = rc.getID();
		myType = rc.getType();
		myAttackRadiusSquared = myType.attackRadiusSquared;
		mySensorRadiusSquared = myType.sensorRadiusSquared;
	}
	
	public static void update() {
		here = rc.getLocation();
	}
}
