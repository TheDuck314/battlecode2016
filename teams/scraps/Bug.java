package scraps;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;

public class Bug extends Info {
	static MapLocation dest = null;
	
	private enum State {
		DIRECT, BUGGING
	}
	static State state = State.DIRECT;
	
	static MapLocation lastWall = null;
	static int closestDistWhileBugging = Integer.MAX_VALUE;
	

	
	public static void goTo(MapLocation theDest) throws GameActionException {
		dest = theDest;
		if (state == State.DIRECT) {
			// try to go direct; start bugging on failure
			if (tryMoveInDirection(here.directionTo(dest))) {
				return;
			} else {
				startBug();
			}
		} else { // state == State.BUGGING
			// try to stop bugging
			if (here.distanceSquaredTo(dest) < closestDistWhileBugging) {
				if (tryMoveInDirection(here.directionTo(dest))) {
					state = State.DIRECT;
					return;
				}
			}
		}
		bugMove();
	}
	

	static boolean tryMoveInDirection(Direction dir) throws GameActionException {
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
	
	
	static void startBug() {
		state = State.BUGGING;
		lastWall = here.add(here.directionTo(dest));
		closestDistWhileBugging = here.distanceSquaredTo(dest);
	}
	
	static void bugMove() throws GameActionException {
		Direction tryDir = here.directionTo(lastWall);
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
