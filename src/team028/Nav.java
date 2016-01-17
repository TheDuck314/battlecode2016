package team028;

import battlecode.common.*;

public class Nav extends Globals {
	
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
	
	public static boolean tryHardMoveInDirection(Direction dir) throws GameActionException {
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
		Direction leftLeft = left.rotateLeft();
		if (rc.canMove(leftLeft)) {
			rc.move(leftLeft);
			return true;
		}
		Direction rightRight = right.rotateRight();
		if (rc.canMove(rightRight)) {
			rc.move(rightRight);
			return true;
		}
		return false;
	}
	
	// Go to the destination. At each step, move either forward
	// or 45 degrees left or right. Clear rubble if necessary.
	// Returns true if it either moved or cleared rubble
    /*public static boolean goToDirect(MapLocation dest) throws GameActionException {
		if (here.equals(dest)) return false;

        Direction forward = here.directionTo(dest);
	    MapLocation forwardLoc = here.add(forward);
		if (here.isAdjacentTo(dest)) {
			if (rc.canMove(forward)) {
				rc.move(forward);
				return true;
			} else if (rc.senseRubble(forwardLoc) > GameConstants.RUBBLE_OBSTRUCTION_THRESH) {
				rc.clearRubble(forward);
				return true;
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
	}*/
	
	public static boolean goToDirect(MapLocation dest) throws GameActionException {
		if (here.equals(dest)) return false;

        Direction forward = here.directionTo(dest);
	    MapLocation forwardLoc = here.add(forward);
		if (here.isAdjacentTo(dest)) {
			if (rc.canMove(forward)) {
				rc.move(forward);
				return true;
			} else if (rc.senseRubble(forwardLoc) > GameConstants.RUBBLE_OBSTRUCTION_THRESH) {
				rc.clearRubble(forward);
				return true;
			}
		}	
		
		Direction[] dirs;
		if (preferLeft(dest)) {
			dirs = new Direction[] { forward, forward.rotateLeft(), forward.rotateRight(),
					forward.rotateLeft().rotateLeft(), forward.rotateRight().rotateRight() };			
		} else {
			dirs = new Direction[] { forward, forward.rotateRight(), forward.rotateLeft(), 
					forward.rotateRight().rotateRight(), forward.rotateLeft().rotateLeft() };
		}
		
		Direction bestDir = null;
	    double bestRubble = Double.MAX_VALUE;
	    int currentDistSq = here.distanceSquaredTo(dest);
	    for (Direction dir : dirs) {
	    	MapLocation dirLoc = here.add(dir);
	    	if (dirLoc.distanceSquaredTo(dest) > currentDistSq) continue;
	    	double rubble = rc.senseRubble(dirLoc);
	    	if (rc.canMove(dir) && rubble < GameConstants.RUBBLE_SLOW_THRESH) {
	    		rc.move(dir);
	    		return true;
	    	} else if (rubble >= GameConstants.RUBBLE_SLOW_THRESH && rubble < bestRubble) {
	    		bestRubble = rubble;
	    		bestDir = dir;
	    	}
	    }
	    
	    if (bestDir != null) {
	    	rc.clearRubble(bestDir);
	    	return true;
	    }
	    return false;
	}
	
	private static boolean preferLeft(MapLocation dest) {
		Direction toDest = here.directionTo(dest);
		MapLocation leftLoc = here.add(toDest.rotateLeft());
		MapLocation rightLoc = here.add(toDest.rotateRight());
		return (dest.distanceSquaredTo(leftLoc) < dest.distanceSquaredTo(rightLoc));
	}
	
	public static boolean enemyAttacksLocation(MapLocation loc, RobotInfo[] hostiles) {
		for (RobotInfo hostile : hostiles) {
			int distSq = hostile.location.distanceSquaredTo(loc);
			if (distSq <= hostile.type.attackRadiusSquared) {
				return true;
			}		
		}
		return false;
	}
	
	public static boolean enemyOrTurretAttacksLocation(MapLocation loc, RobotInfo[] hostiles,
			MapLocation turretLocation) {
		for (RobotInfo hostile : hostiles) {
			int distSq = hostile.location.distanceSquaredTo(loc);
			if (distSq <= hostile.type.attackRadiusSquared) {
				return true;
			}		
		}
		if (turretLocation != null) {
			if (turretLocation.distanceSquaredTo(loc) <= RobotType.TURRET.attackRadiusSquared) {
				return true;
			}
		}
		return false;
	}
	
	// Go to the destination. At each step, move either forward
	// or 45 degrees left or right. Clear rubble if necessary.
	// Avoids going in range of any hostile robot that we can see.
	public static boolean goToDirectSafely(MapLocation dest) throws GameActionException {
		if (dest == null) {
			System.out.println("goToDirectSafely dest = null");
			return false;
		}
		if (here.equals(dest)) return false;

		RobotInfo[] hostiles = rc.senseHostileRobots(here, mySensorRadiusSquared);

        Direction forward = here.directionTo(dest);
	    MapLocation forwardLoc = here.add(forward);
		if (here.isAdjacentTo(dest)) {
			if (rc.canMove(forward) && !enemyAttacksLocation(dest, hostiles)) {
				rc.move(forward);
				return true;
			} else if (rc.senseRubble(forwardLoc) > GameConstants.RUBBLE_OBSTRUCTION_THRESH) {
				rc.clearRubble(forward);
				return true;
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
	    		if (!enemyAttacksLocation(here.add(dirs[i]), hostiles)) {
	    			rc.move(dirs[i]);
	    			return true;
	    		}
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
	
	
	// Go to the destination. At each step, move either forward
	// or 45 degrees left or right. Clear rubble if necessary.
	// Avoids going in range of any hostile robot that we can see.
	// Also avoids going in range of a turret at the location turretLocation.
	/*public static boolean goToDirectSafelyAvoidingTurret(MapLocation dest,
			MapLocation turretLocation) throws GameActionException {
		if (here.equals(dest)) return false;

		RobotInfo[] hostiles = rc.senseHostileRobots(here, mySensorRadiusSquared);

        Direction forward = here.directionTo(dest);
	    MapLocation forwardLoc = here.add(forward);
		if (here.isAdjacentTo(dest)) {
			if (rc.canMove(forward) && !enemyOrTurretAttacksLocation(dest, hostiles, turretLocation)) {
				rc.move(forward);
				return true;
			} else if (rc.senseRubble(forwardLoc) > GameConstants.RUBBLE_OBSTRUCTION_THRESH) {
				rc.clearRubble(forward);
				return true;
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
	    		if (!enemyOrTurretAttacksLocation(here.add(dirs[i]), hostiles, turretLocation)) {
	    			rc.move(dirs[i]);
	    			return true;
	    		}
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
	}*/
	
	public static boolean goToDirectSafelyAvoidingTurret(MapLocation dest,
			MapLocation turretLocation) throws GameActionException {
		if (here.equals(dest)) return false;

		RobotInfo[] hostiles = rc.senseHostileRobots(here, mySensorRadiusSquared);
		
        Direction forward = here.directionTo(dest);
	    MapLocation forwardLoc = here.add(forward);
		if (here.isAdjacentTo(dest)) {
			if (rc.canMove(forward) && !enemyOrTurretAttacksLocation(dest, hostiles, turretLocation)) {
				rc.move(forward);
				return true;
			} else if (rc.senseRubble(forwardLoc) > GameConstants.RUBBLE_OBSTRUCTION_THRESH) {
				rc.clearRubble(forward);
				return true;
			}
		}	
		
		Direction[] dirs;
		if (preferLeft(dest)) {
			dirs = new Direction[] { forward, forward.rotateLeft(), forward.rotateRight(),
					forward.rotateLeft().rotateLeft(), forward.rotateRight().rotateRight() };			
		} else {
			dirs = new Direction[] { forward, forward.rotateRight(), forward.rotateLeft(), 
					forward.rotateRight().rotateRight(), forward.rotateLeft().rotateLeft() };
		}
		
		
		Direction bestDir = null;
	    double bestRubble = Double.MAX_VALUE;
	    int currentDistSq = here.distanceSquaredTo(dest);
	    for (Direction dir : dirs) {
	    	MapLocation dirLoc = here.add(dir);
	    	if (dirLoc.distanceSquaredTo(dest) >= currentDistSq) continue;
			double rubble = rc.senseRubble(dirLoc);
			if (rc.canMove(dir) && rubble < GameConstants.RUBBLE_SLOW_THRESH) {
	    		if (!enemyOrTurretAttacksLocation(dirLoc, hostiles, turretLocation)) {
	    			rc.move(dir);
	    			return true;
	    		} 
	    	} else if (rubble >= GameConstants.RUBBLE_SLOW_THRESH && rubble < bestRubble) {
	    		bestRubble = rubble;
	    		bestDir = dir;
	    	}
	    }
	    
	    if (bestDir != null) {
	    	rc.clearRubble(bestDir);
	    	return true;
	    }
	    return false;
	}
	
	// Always move if possible, but prefer to move toward the destination
	// Don't move next to an archon.
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
	
	// Always move if possible, but prefer to move toward the destination
	// Don't move next to an archon, or in range of the given turret
	public static void swarmToAvoidingArchonsAndTurret(MapLocation dest, MapLocation turretLocation) throws GameActionException {
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
			MapLocation dirLoc = here.add(dir);
			for (int i = 0; i < numArchons; ++i) {
				if (dirLoc.isAdjacentTo(nearbyArchons[i])) {
					continue dirSearch;
				}
				if (turretLocation != null &&
						dirLoc.distanceSquaredTo(turretLocation) <= RobotType.TURRET.attackRadiusSquared) {
					continue dirSearch;
				}
			}
			if (tryMoveClearDir(dir)) {
				return;
			}
		}
	}
	
	// Always move if possible, but prefer to move toward the destination
	// Don't move next to an archon.
	// Try to be polite and let robots who are more injure than us get to the
	// destination first.
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
	
	// If we can move freely in direction dir, do so.
	// If we can move in direction dir but rubble would slow us, clear the rubble.
	// If we can't move in direction dir and there is rubble, clear the rubble.
	// If we can't move in direction dir and there isn't rubble, return false.
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
	
	private static MapLocation bugDest = null;
	
	private static boolean bugTracing = false;
	private static MapLocation bugLastWall = null;
	private static int closestDistWhileBugging = Integer.MAX_VALUE;	
	private static int bugNumTurnsWithNoWall = 0;
	private static boolean bugWallOnLeft = true; // whether the wall is on our left or our right
		
	public static void goToBug(MapLocation theDest) throws GameActionException {
		if (!theDest.equals(bugDest)) {
			bugDest = theDest;
			bugTracing = false;
		}
		
		if (here.equals(bugDest)) {
			return;
		}
		
		if (!bugTracing) {
			// try to go direct; start bugging on failure
			Direction destDir = here.directionTo(bugDest);
			if (tryMoveInDirection(destDir)) {
				return;
			} else {
				bugStartTracing();
			}
		} else { // state == State.BUGGING
			// try to stop bugging
			if (here.distanceSquaredTo(bugDest) < closestDistWhileBugging) {
				if (tryMoveInDirection(here.directionTo(bugDest))) {
					bugTracing = false;
					return;
				}
			}
		}
		bugTraceMove(false);
	    
	    if (bugNumTurnsWithNoWall >= 2) {
	    	bugTracing = false;
	    }
	}

	public static void bugReset() {
		bugTracing = false;
	}

	static void bugStartTracing() {
		bugTracing = true;
		
		closestDistWhileBugging = here.distanceSquaredTo(bugDest);
		bugNumTurnsWithNoWall = 0;
		
		Direction dirToDest = here.directionTo(bugDest);
		Direction leftDir = dirToDest;
		int leftDistSq = Integer.MAX_VALUE;
		for (int i = 0; i < 8; ++i) {
			leftDir = leftDir.rotateLeft();
			if (rc.canMove(leftDir)) {
				leftDistSq = here.add(leftDir).distanceSquaredTo(bugDest);
				break;
			}
		}
		Direction rightDir = dirToDest;
		int rightDistSq = Integer.MAX_VALUE;
		for (int i = 0; i < 8; ++i) {
			rightDir = rightDir.rotateRight();
			if (rc.canMove(rightDir)) {
				rightDistSq = here.add(rightDir).distanceSquaredTo(bugDest);
				break;
			}
		}
		if (rightDistSq < leftDistSq) {
			bugWallOnLeft = true;
			bugLastWall = here.add(rightDir.rotateLeft());
		} else {
			bugWallOnLeft = false;
			bugLastWall = here.add(leftDir.rotateRight());
		}
	}
	
	static void bugTraceMove(boolean recursed) throws GameActionException {
		Direction tryDir = here.directionTo(bugLastWall);
		if (rc.canMove(tryDir)) {
			bugNumTurnsWithNoWall += 1;
		} else {
			bugNumTurnsWithNoWall = 0;
		}
		for (int i = 0; i < 8; ++i) {
			if (bugWallOnLeft) {
				tryDir = tryDir.rotateRight();
			} else {
				tryDir = tryDir.rotateLeft();
			}
			MapLocation dirLoc = here.add(tryDir);
			if (!rc.onTheMap(dirLoc) && !recursed) {
				// if we hit the edge of the map, reverse direction and recurse
				bugWallOnLeft = !bugWallOnLeft;
				bugTraceMove(true);
				return;
			}
			if (rc.canMove(tryDir)) {
				rc.move(tryDir);
				return;
			} else {
				bugLastWall = here.add(tryDir);
			}
		}
	}
	
	
	
}
