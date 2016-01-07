package explore;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.GameConstants;
import battlecode.common.MapLocation;

public class DirectNav extends Globals {
	
	public static void goTo(MapLocation dest) throws GameActionException {
		if (here.equals(dest)) return;

        Direction forward = here.directionTo(dest);
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
		
	    if (leftLoc.distanceSquaredTo(dest) < rightLoc.distanceSquaredTo(dest)) {
	    	dirs = new Direction[] { forward, left, right };
	    	rubbles = new double[] { forwardRubble, leftRubble, rightRubble };
	    } else {
	    	dirs = new Direction[] { forward, right, left };
	    	rubbles = new double[] { forwardRubble, rightRubble, leftRubble };
	    }
	    
	    Direction bestDir = null;
	    double bestRubble = Double.MAX_VALUE;
	    for (int i = 0; i < 3; ++i) {
	    	if (rc.canMove(dirs[i]) && rubbles[i] < GameConstants.RUBBLE_SLOW_THRESH) {
	    		rc.move(dirs[i]);
	    		return;
	    	} else if (rubbles[i] >= GameConstants.RUBBLE_SLOW_THRESH && rubbles[i] < bestRubble) {
	    		bestRubble = rubbles[i];
	    		bestDir = dirs[i];
	    	}
	    }
	    
	    if (bestDir != null) {
	    	rc.clearRubble(bestDir);
	    }
	}
}