package zombieattack7;

import battlecode.common.*;

public class DBug extends Globals {
	private static MapLocation dest = null;

	private static boolean tracing = false;
	private static MapLocation lastWall = null;
	private static int closestDistWhileBugging = Integer.MAX_VALUE;
	private static int numTurnsWithNoWall = 0;

	public static void goTo(MapLocation theDest) throws GameActionException {
		if (!theDest.equals(dest)) {
			dest = theDest;
			tracing = false;
		}

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


	public static boolean tryMoveInDirection(Direction dir) throws GameActionException {
        Direction forward = dir;
		Direction left = forward.rotateLeft();
		Direction right = forward.rotateRight();
	    MapLocation forwardLoc = here.add(forward);
		MapLocation leftLoc = here.add(left);
		MapLocation rightLoc = here.add(right);
	    double forwardRubble = rc.senseRubble(forwardLoc);
		double leftRubble = rc.senseRubble(leftLoc);
		double rightRubble = rc.senseRubble(rightLoc);
	    
		Direction[] dirs;
		double[] rubbles;
		
		dirs = new Direction[] { forward, left, right };
		rubbles = new double[] { forwardRubble, leftRubble, rightRubble };
	    
	    Direction bestDir = null;
	    double bestRubble = Double.MAX_VALUE;
	    for (int i = 0; i < 3; ++i) {
	    	if (rc.canMove(dirs[i]) && rubbles[i] < GameConstants.RUBBLE_SLOW_THRESH) {
	    		rc.move(dirs[i]);
	    		return true;
	    	} else if (rubbles[i] >= GameConstants.RUBBLE_SLOW_THRESH && rubbles[i] < bestRubble) {
	    		bestRubble = rubbles[i];
	    		bestDir = dirs[i];
	    	}
	    }
	    if (bestDir != null) {
	    	rc.clearRubble(bestDir);
	    	return true;
	    }
	    return false;
	}
	
	public static boolean tryMoveClearDir(Direction dir) throws GameActionException {
	    MapLocation dirLoc = here.add(dir);
	    double rubble = rc.senseRubble(dirLoc);
	    if (rc.canMove(dir) && rubble < GameConstants.RUBBLE_SLOW_THRESH) {
	    	rc.move(dir);
	    	return true;
	    } else if (rubble >= GameConstants.RUBBLE_SLOW_THRESH) {
	    	rc.clearRubble(dir);
	    	return true;
	    } else {
	    	return false;
	    }
	}


	static void startTracing() {
		tracing = true;
		lastWall = here.add(here.directionTo(dest));
		closestDistWhileBugging = here.distanceSquaredTo(dest);
		numTurnsWithNoWall = 0;
	}

	static void traceMove() throws GameActionException {
		if (!rc.isCoreReady()) return;
		Direction tryDir = here.directionTo(lastWall);
		if (rc.canMove(tryDir)) {
			++numTurnsWithNoWall;
		} else {
			numTurnsWithNoWall = 0;
		}
		for (int i = 0; i < 8; ++i) {
			tryDir = tryDir.rotateRight();
			if (tryMoveClearDir(tryDir)) {
				return;
			} else {
				lastWall = here.add(tryDir);
			}
		}
	}
}