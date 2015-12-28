package scraps;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.GameConstants;
import battlecode.common.MapLocation;
import battlecode.common.TerrainTile;

public class LookaheadBug extends Info {
	static MapLocation dest = null;
	
	static class BugSim {
		public static MapLocation simLoc;
		static boolean bugging = false;
		static boolean wallOnLeft = false;
		static MapLocation lastWall = null;
		static int closestDistSqWhileBugging = Integer.MAX_VALUE;
		
		
		static void init(MapLocation startLoc) {
			simLoc = startLoc;
			bugging = false;
			notifyMove();
		}

		public static void notifyMove() {
			canSee = new boolean[GameConstants.MAP_MAX_WIDTH][GameConstants.MAP_MAX_HEIGHT];
		}

		

		private enum DirectMoveResult { MOVED, MUST_WAIT, START_BUGGING }
		
		private static DirectMoveResult tryDirectMove() {
			Direction dirToDest = dirToDiagFirst(simLoc, dest);
			MapLocation simLocPlusDir = simLoc.add(dirToDest);
			switch (rc.senseTerrainTile(simLocPlusDir)) {
				case NORMAL:
					if (isVisibleFromHere(simLocPlusDir)) {
						rc.setIndicatorLine(simLoc, simLocPlusDir, 0, 255, 0);
						simLoc = simLocPlusDir;
						return DirectMoveResult.MOVED;
					} else {
						return DirectMoveResult.MUST_WAIT;
					}
				case UNKNOWN:
					return DirectMoveResult.MUST_WAIT; // can't advance till we know the terrain there
				default:
					return DirectMoveResult.START_BUGGING;
			}
		}
		
		// guesses what the best value of wallOnLeft is
		// returns false if it hits an UNKNOWN and therefore
		// can't decide yet
		private static boolean setWallFollowDir() {
			Direction forward = dirToDiagFirst(simLoc, dest);
			
			Direction dir = forward.rotateLeft();
			int leftDistSq = Integer.MAX_VALUE;
			left: for (int i = 0; i < 8; ++i) {
				MapLocation leftLoc = simLoc.add(dir);
				switch (rc.senseTerrainTile(leftLoc)) {
					case NORMAL:
						leftDistSq = leftLoc.distanceSquaredTo(dest);
						break left;
					case UNKNOWN:
						return false;
					default:
						break;
				}
				dir = dir.rotateLeft();
			}

			dir = forward.rotateRight();
			int rightDistSq = Integer.MAX_VALUE;
			left: for (int i = 0; i < 8; ++i) {
				MapLocation rightLoc = simLoc.add(dir);
				switch (rc.senseTerrainTile(rightLoc)) {
					case NORMAL:
						rightDistSq = rightLoc.distanceSquaredTo(dest);
						break left;
					case UNKNOWN:
						return false;
					default:
						break;
				}
				dir = dir.rotateRight();
			}

			wallOnLeft = rightDistSq < leftDistSq;
			return true;
		}
		
		// try to advance the simulated bot. reasons we might fail to 
		// advance:
		//  - we run into an UNKNOWN
		//  - we couldn't see the bot's location if it advanced
		//  - no place to move to??
		public static boolean tryMove() {
			if (simLoc.equals(dest)) return false;
			
			if (!bugging) {
				// we are moving directly. try to keep doing so
				switch (tryDirectMove()) {
					case MOVED: return true;
					case MUST_WAIT: return false;
					case START_BUGGING: 
						if (!setWallFollowDir()) return false;
						bugging = true;
						lastWall = simLoc.add(dirToDiagFirst(simLoc, dest));					
						closestDistSqWhileBugging = simLoc.distanceSquaredTo(dest);
						break;
				}
			} else {
				// we are already bugging. see if we can stop
				int distSq = simLoc.distanceSquaredTo(dest);
				if (distSq < closestDistSqWhileBugging) {
					switch (tryDirectMove()) {
						case MOVED: 
							bugging = false; // stop bugging
							return true; 
						case MUST_WAIT: return false;
						case START_BUGGING: 
							closestDistSqWhileBugging = distSq;
							break; // can't stop bugging
					}
				}
			}
			
			// we are bugging. try to move along the wall
			Direction tryDir = simLoc.directionTo(lastWall);
			for (int i = 0; i < 8; ++i) {
				tryDir = wallOnLeft ? tryDir.rotateRight() : tryDir.rotateLeft();
				MapLocation simLocPlusDir = simLoc.add(tryDir);
				switch (rc.senseTerrainTile(simLocPlusDir)) {
					case NORMAL:
						if (isVisibleFromHere(simLocPlusDir)) {
							rc.setIndicatorLine(simLoc, simLocPlusDir, 0, 0, 255);
							simLoc = simLocPlusDir;
							return true;
						} else {
							return false; // must wait
						}
					case UNKNOWN:
						return false; // must wait
					case OFF_MAP:
						// on hitting the map edge, reverse wall follow dir
						// and count this as a move
						wallOnLeft = !wallOnLeft;
						return true;
					default:
						lastWall = simLocPlusDir;	
						break;
				}
			}
			
			// somehow we couldn't find anywhere to bug
			return false;
		}
		
		

		static boolean[][] canSee;
		static int arrX(int x) {
			return (x + 1000000000) % GameConstants.MAP_MAX_WIDTH;		
		}
		static int arrY(int y) {
			return (y + 1000000000) % GameConstants.MAP_MAX_HEIGHT;
		}
		
		private static boolean isVisibleFromHere(MapLocation loc) {
			loc = loc.add(dirToOrthogFirst(loc, here));
			while (!loc.equals(here)) {
				int aX = arrX(loc.x);
				int aY = arrY(loc.y);
				if (canSee[aX][aY]) return true;
				if (rc.senseTerrainTile(loc) != TerrainTile.NORMAL) return false;
				canSee[aX][aY] = true;
				//rc.setIndicatorDot(loc, 0, 0, 255);
				loc = loc.add(dirToOrthogFirst(loc, here));
			}
			return true;
		}
	}

	public static void goTo(MapLocation theDest) throws GameActionException {
		dest = theDest;
		if (here.equals(dest)) return;

		if (BugSim.simLoc == null) {
			BugSim.init(here);
		}
		
		while(BugSim.tryMove());
		
		// TODO: DETECT AND DEAL WITH CASE WHEN WE NO LONGER
		// HAVE A PATH TO THE SIMULATED BUG
		
		rc.setIndicatorLine(here, BugSim.simLoc, 255, 0, 0);
		rc.setIndicatorDot(BugSim.simLoc, 0, 255, 0);
		boolean moved = tryMoveInDirection(dirToDiagFirst(here, BugSim.simLoc));
		if(moved) BugSim.notifyMove();
	}
	

	static Direction dirToDiagFirst(MapLocation from, MapLocation to) {
		int dx = to.x - from.x;
		int dy = to.y - from.y;
		if (dx > 0) {
			if (dy > 0) return Direction.SOUTH_EAST;
			else if (dy < 0) return Direction.NORTH_EAST;
			else return Direction.EAST;
		} else if (dx < 0) {
			if (dy > 0) return Direction.SOUTH_WEST;
			else if (dy < 0) return Direction.NORTH_WEST;
			else return Direction.WEST;
		} else {
			if (dy > 0) return Direction.SOUTH;
			else return Direction.NORTH;
		}
	}
	
	static Direction dirToOrthogFirst(MapLocation from, MapLocation to) {
		int dx = to.x - from.x;
		int dy = to.y - from.y;
		int absdx = Math.abs(dx);
		int absdy = Math.abs(dy);
		if (absdx > absdy) {
			if (dx > 0) return Direction.EAST;
			else return Direction.WEST;
		} else if (absdx < absdy) {
			if (dy > 0) return Direction.SOUTH;
			else return Direction.NORTH;
		} else {
			if (dx > 0) {
				if (dy > 0) return Direction.SOUTH_EAST;
				else return Direction.NORTH_EAST;
			} else {
				if (dy > 0) return Direction.SOUTH_WEST;
				else return Direction.NORTH_WEST;
			}
		}
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
	

	

	
	static boolean isTileOpen(MapLocation loc) {
		TerrainTile tt = rc.senseTerrainTile(loc);
		return tt == TerrainTile.NORMAL || tt == TerrainTile.UNKNOWN;
	}
	
	
}
