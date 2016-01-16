package scout_viper14;

import battlecode.common.*;

public class Globals {
	
	public static final int checkUnpairedScoutInterval = 50;
	
	public static RobotController rc;
	public static MapLocation here;
	public static Team us;
	public static Team them;
	public static int myID;
	public static RobotType myType;
	public static int myAttackRadiusSquared;
	public static int mySensorRadiusSquared;
	
	public static int numberOfInitialArchon;
	
	public static MapLocation[] ourInitialArchonLocations;
	public static MapLocation[] theirInitialArchonLocations;
	
	public static MapLocation centerOfOurInitialArchons;
	public static MapLocation centerOfTheirInitialArchons;
	public static MapLocation centerOfAllInitialArchons;
	
	public static RobotInfo[] visibleHostiles = null;
	public static RobotInfo[] visibleEnemies = null;
	public static RobotInfo[] visibleZombies = null;
	public static RobotInfo[] visibleAllies = null;
	public static RobotInfo[] attackableHostiles = null;
	
	public static void init(RobotController theRC) {
		rc = theRC;
		us = rc.getTeam();
		them = us.opponent();
		myID = rc.getID();
		myType = rc.getType();
		myAttackRadiusSquared = myType.attackRadiusSquared;
		mySensorRadiusSquared = myType.sensorRadiusSquared;
		ourInitialArchonLocations = rc.getInitialArchonLocations(us);
		theirInitialArchonLocations = rc.getInitialArchonLocations(them);
		numberOfInitialArchon = ourInitialArchonLocations.length;
		centerOfOurInitialArchons = new MapLocation(0,0);
		centerOfTheirInitialArchons = new MapLocation(0,0);
		centerOfAllInitialArchons = new MapLocation(0,0);
		for (MapLocation a : ourInitialArchonLocations) {
			centerOfOurInitialArchons = FastMath.addVec(centerOfOurInitialArchons, a);
		}
		for (MapLocation a : theirInitialArchonLocations) {
			centerOfTheirInitialArchons = FastMath.addVec(centerOfTheirInitialArchons, a);
		}
		centerOfAllInitialArchons = FastMath.addVec(centerOfOurInitialArchons, centerOfTheirInitialArchons);
		centerOfOurInitialArchons = FastMath.multiplyVec(1.0 / (double)numberOfInitialArchon, centerOfOurInitialArchons);
		centerOfTheirInitialArchons = FastMath.multiplyVec(1.0 / (double)numberOfInitialArchon, centerOfTheirInitialArchons);
		centerOfAllInitialArchons = FastMath.multiplyVec(0.5 / (double)numberOfInitialArchon, centerOfAllInitialArchons);
		here = rc.getLocation();
	}
	
	public static void update() {
		here = rc.getLocation();
	}
	
	public static void updateRobotInfos() {
		visibleAllies = rc.senseNearbyRobots(mySensorRadiusSquared, us);
		visibleEnemies =  rc.senseNearbyRobots(mySensorRadiusSquared, them);
		visibleZombies = rc.senseNearbyRobots(mySensorRadiusSquared, Team.ZOMBIE);
		visibleHostiles = rc.senseHostileRobots(here, mySensorRadiusSquared);
		attackableHostiles = rc.senseHostileRobots(here, myAttackRadiusSquared);
	}
}
