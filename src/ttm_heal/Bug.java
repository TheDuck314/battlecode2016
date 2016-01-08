package ttm_heal;

import battlecode.common.*;

public class Bug extends Globals {
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
		if (rc.canMove(dir)) {
			rc.move(dir);
			return true;
		}
		Direction left = dir.rotateLeft();
		if (rc.canMove(left)) {
			rc.move(left);
			return true;
		}
		Direction right = dir.rotateRight();
		if (rc.canMove(right)) {
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
		if (rc.canMove(tryDir)) {
			++numTurnsWithNoWall;
		} else {
			numTurnsWithNoWall = 0;
		}
		for (int i = 0; i < 8; ++i) {
			tryDir = tryDir.rotateRight();
			if (rc.canMove(tryDir)) {
				rc.move(tryDir);
				return;
			} else {
				lastWall = here.add(tryDir);
			}
		}
	}
}