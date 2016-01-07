package explore;

import battlecode.common.*;

public class SafeBug extends Globals {
	private static MapLocation dest = null;
	
	private static boolean tracing = false;
	private static MapLocation lastWall = null;
	private static int closestDistWhileBugging = Integer.MAX_VALUE;	
	private static int numTurnsWithNoWall = 0;
	private static boolean[] isSquareSafe = new boolean[8];	
	
	public static void goTo(MapLocation theDest) throws GameActionException {
		if (!theDest.equals(dest)) {
			dest = theDest;
			tracing = false;
		}
		
		checkWhichSquaresAreSafe();
		
		if (!tracing) {
			// try to go direct; start bugging on failure
			if (tryMoveInDirection(here.directionTo(dest))) {
				return;
			} else {
				startTracing();
			}
		} else { // state == State.BUGGING
			// try to stop bugging
			if (here.distanceSquaredTo(dest) < closestDistWhileBugging) {
				if (tryMoveInDirection(here.directionTo(dest))) {
					tracing = false;
					return;
				}
			}
		}
	    traceMove();
	    
	    if (numTurnsWithNoWall >= 3) {
	    	tracing = false;
	    }
	}
	
	private static void checkWhichSquaresAreSafe() {
		RobotInfo[] hostiles = rc.senseHostileRobots(here, mySensorRadiusSquared);

		Debug.indicate("safebug", 2, "hostiles.length = " + hostiles.length);
		Direction[] dirs = Direction.values();
		for (int d = 0; d < 8; ++d) {
			Direction dir = dirs[d];
			if (rc.canMove(dir)) {
				isSquareSafe[d] = true;
				MapLocation dirLoc = here.add(dir);
				rc.setIndicatorDot(dirLoc, 0, 255, 0);
				for (RobotInfo hostile : hostiles) {
					int unsafeRadiusSq = 8;
					if (hostile.type.attackRadiusSquared > 8) unsafeRadiusSq = hostile.type.attackRadiusSquared;
					if (hostile.location.distanceSquaredTo(dirLoc) <= unsafeRadiusSq) {
						Debug.indicateAppend("safebug", 2, "; d=" + d + " is unsafe");
						isSquareSafe[d] = false;
						rc.setIndicatorDot(dirLoc, 255, 0, 0);
						break;
					}
				}
			} else {
				isSquareSafe[d] = false;
				rc.setIndicatorDot(here.add(dir), 255, 0, 0);
			}
		}
	}

	public static boolean tryMoveInDirection(Direction dir) throws GameActionException {
		if (isSquareSafe[dir.ordinal()]) {
			rc.move(dir);
			return true;
		}
		Direction left = dir.rotateLeft();
		if (isSquareSafe[left.ordinal()]) {
			rc.move(left);
			return true;
		}
		Direction right = dir.rotateRight();
		if (isSquareSafe[right.ordinal()]) {
			rc.move(right);
			return true;
		}
		return false;
	}
	
	
	static void startTracing() {
		tracing = true;
		lastWall = here.add(here.directionTo(dest));
		closestDistWhileBugging = here.distanceSquaredTo(dest);
		numTurnsWithNoWall = 0;
	}
	
	static void traceMove() throws GameActionException {
		Direction tryDir = here.directionTo(lastWall);
		if (isSquareSafe[tryDir.ordinal()]) {
			++numTurnsWithNoWall;
		} else {
			numTurnsWithNoWall = 0;
		}
		for (int i = 0; i < 8; ++i) {
			tryDir = tryDir.rotateRight();
			if (isSquareSafe[tryDir.ordinal()]) {
				rc.move(tryDir);
				return;
			} else {
				lastWall = here.add(tryDir);
			}
		}
	}
}