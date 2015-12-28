package scraps;

import battlecode.common.*;

public class RobotPlayer extends Info {
	static java.util.Random rand;
	
	public static void run(RobotController theRC) throws GameActionException {
		rc = theRC;
		ourHQ = rc.senseHQLocation();
		theirHQ = rc.senseEnemyHQLocation();
		rand = new java.util.Random(theRC.getID());
		FastMath.initRand(rc);
		
		while(true) {
			here = rc.getLocation();
			switch(rc.getType()) {
			case HQ:
				turnHQ();
				break;
			case BEAVER:
				turnBeaver();
				break;
			default:
				break;
			}
			rc.yield();
		}
	}
	
	static void turnHQ() throws GameActionException {
		if (!rc.isCoreReady()) return;
		if (Clock.getRoundNum() % 2000 > 0) return;
		for (Direction dir : Direction.values()) {
			if (rc.canSpawn(dir, RobotType.BEAVER)) {
				rc.spawn(dir, RobotType.BEAVER);
				return;
			}
		}
	}
	
	static void turnBeaver() throws GameActionException {
		if (rc.isCoreReady()) LookaheadBug.goTo(theirHQ);
	}
}
