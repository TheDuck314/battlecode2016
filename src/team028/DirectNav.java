package team028;

import battlecode.common.*;

public class DirectNav extends Globals {
	
	public static void goTo(MapLocation dest) throws GameActionException {
		if (here.equals(dest)) return;

        Direction forward = here.directionTo(dest);
	    MapLocation forwardLoc = here.add(forward);
		if (here.isAdjacentTo(dest)) {
			if (rc.canMove(forward)) {
				rc.move(forward);
				return;
			} else if (rc.senseRubble(forwardLoc) > GameConstants.RUBBLE_OBSTRUCTION_THRESH) {
				rc.clearRubble(forward);
				return;
			}
		}
		
		Direction left = forward.rotateLeft();
		Direction right = forward.rotateRight();
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
	
	public static void swarmToAvoidingArchons(MapLocation dest) throws GameActionException {
		MapLocation[] nearbyArchons = new MapLocation[10];
		int numArchons = 0;
		RobotInfo[] allies = rc.senseNearbyRobots(8, us);
		for (RobotInfo ally : allies) {
			if (ally.type == RobotType.ARCHON) {
				nearbyArchons[numArchons++] = ally.location;
			}
		}		
		
		Direction forward = here.equals(dest) ? Direction.EAST : here.directionTo(dest);
		Direction[] dirs = { forward, forward.rotateLeft(), forward.rotateRight(),
				forward.rotateLeft().rotateLeft(), forward.rotateRight().rotateRight(),
				forward.rotateRight().opposite(), forward.rotateLeft().opposite(),
				forward.opposite() };
		dirSearch: for (Direction dir : dirs) {
			for (int i = 0; i < numArchons; ++i) {
				if (here.add(dir).isAdjacentTo(nearbyArchons[i])) {
					continue dirSearch;
				}
			}
			if (tryMoveClearDir(dir)) {
				return;
			}
		}
	}
	
	public static void politelySwarmToAvoidingArchons(MapLocation dest) throws GameActionException {
		MapLocation[] nearbyArchons = new MapLocation[10];
		int numArchons = 0;
		MapLocation[] penalizedSquares = new MapLocation[50];
		int numPenalizedSquares = 0;
		RobotInfo[] allies = rc.senseNearbyRobots(8, us);
		for (RobotInfo ally : allies) {
			if (ally.type == RobotType.ARCHON) {
				nearbyArchons[numArchons++] = ally.location;
			} else if (ally.health < rc.getHealth()) {
				penalizedSquares[numPenalizedSquares++] = 
						ally.location.add(ally.location.directionTo(dest));
			}
		}		
		
		Direction forward = here.equals(dest) ? Direction.EAST : here.directionTo(dest);
		Direction[] dirs = { forward, forward.rotateLeft(), forward.rotateRight(),
				forward.rotateLeft().rotateLeft(), forward.rotateRight().rotateRight(),
				forward.rotateRight().opposite(), forward.rotateLeft().opposite(),
				forward.opposite() };
		Direction bestDir = null;
		int fewestPenalties = 99999;
		dirSearch: for (int d = 0; d < 8; ++d) {
			Direction dir = dirs[d];
			MapLocation dirLoc = here.add(dir);
			for (int i = 0; i < numArchons; ++i) {
				if (dirLoc.isAdjacentTo(nearbyArchons[i])) {
					continue dirSearch;
				}
			}
			int numPenalties = 0;
			if (d < 5) {
				for (int i = 0; i < numPenalizedSquares; ++i) {
					if (dirLoc.equals(penalizedSquares[i])) {
						numPenalties++;
					}
				}
			}
			
			if (numPenalties == 0) {
			    if (tryMoveClearDir(dir)) {
				    return;
			    } else {
			    	continue;
			    }
			} else {
				if (numPenalties < fewestPenalties) {
					fewestPenalties = numPenalties;
					bestDir = dir;
				}
			}
		}
		// only move onto a penalized square if we are adjacent to an archon
		if (bestDir != null) {
			for (int i = 0; i < numArchons; ++i) {
				if (here.isAdjacentTo(nearbyArchons[i])) {
			        tryMoveClearDir(bestDir);
			        return;
				}
			}
		}
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
}