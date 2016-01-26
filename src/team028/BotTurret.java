package team028;

import battlecode.common.*;

public class BotTurret extends Globals {
	public static void loop() {
//		Debug.init("robotinfo");
		rc.emptySignalQueue(); // flush signal backlog
		int maxBytecodesUsed = 0;
		int maxBytecodeUsedTurn = 0;
		while (true) {
			int startTurn = rc.getRoundNum();
			try {
				Globals.update();
//				Debug.indicate("bytecodes", 0, "start: " + Clock.getBytecodeNum());
				processSignals();
//				Debug.indicateAppend("bytecodes", 0, "; after processSignals: " + Clock.getBytecodeNum());
//				Radar.indicateEnemyTurretLocation(0, 200, 200);
				manageHealingState();
				if (rc.getType() == RobotType.TURRET) {
				    turnTurret();
				} else {
					turnTTM();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			int endTurn = rc.getRoundNum();
			if (startTurn != endTurn) {
				System.out.println("OVER BYTECODE LIMIT");
			} else {
				int bytecodesUsed = Clock.getBytecodeNum();
				if (bytecodesUsed > maxBytecodesUsed) {
					maxBytecodesUsed = bytecodesUsed;
					maxBytecodeUsedTurn = rc.getRoundNum();
//					Debug.println("bytecodes", "new max bytecode use = " + maxBytecodesUsed + " on turn " + maxBytecodeUsedTurn);
				}
			}
			Clock.yield();
		}
	}
	
	private static MapLocation attackTarget = null;
	private static int attackTargetReceivedRound = -9999;	

	private static int lastKnownArchonId = -1;
	private static MapLocation lastKnownArchonLocation = null;
	private static int lastKnownArchonLocationRound = -999999;
	
	private static final int PACK_DELAY = 20;
	private static int packCountdown = PACK_DELAY;

	private static int numAttacksOnRememberedTurret = 0;
	
	private static MapLocation lastAttackLocation = null;
	
	private static boolean inHealingState = false;
	
	
	private static void turnTurret() throws GameActionException {
		if (!rc.isWeaponReady() && !rc.isCoreReady()) return;
		
//		Radar.removeDistantEnemyTurrets(9 * RobotType.SCOUT.sensorRadiusSquared);
//		Debug.indicate("bytecodes", 1, "; after removeDistantTurrets: " + Clock.getBytecodeNum());
		//Radar.removeOldEnemyTurrets(Radar.TURRET_MEMORY_ROUNDS);
		
		RobotInfo[] attackableEnemies = rc.senseHostileRobots(here, myAttackRadiusSquared);
		if (rc.isWeaponReady()) {
//			Debug.indicateAppend("bytecodes", 1, "; after tryToAttack: " + Clock.getBytecodeNum());
			if (tryToAttackAnEnemy(attackableEnemies)) {
//				Debug.indicateAppend("bytecodes", 1, "; after tryToAttack: " + Clock.getBytecodeNum());
				packCountdown = PACK_DELAY;
				return;
			}
//			Debug.indicateAppend("bytecodes", 1, "; after tryToAttack: " + Clock.getBytecodeNum());
			if (attackableEnemies.length == 0 && rc.isCoreReady()) {
				--packCountdown;
				if (packCountdown == 0) {
					rc.pack();
					return;
				}
			}		
		}
	}
	
	private static void turnTTM() throws GameActionException {
		if (!rc.isCoreReady()) return;
				
//		Radar.removeDistantEnemyTurrets(9 * RobotType.SCOUT.sensorRadiusSquared);
		//Radar.removeOldEnemyTurrets(Radar.TURRET_MEMORY_ROUNDS);
		
		RobotInfo[] attackableEnemies = rc.senseHostileRobots(here, RobotType.TURRET.attackRadiusSquared);
		if (attackableEnemies.length > 0) {
			rc.unpack();
			packCountdown = PACK_DELAY;
			return;
		}
		for (int i = 0; i < Radar.numCachedEnemies; ++i) {
			FastRobotInfo hostile = Radar.enemyCache[i];
			if (here.distanceSquaredTo(hostile.location) <= RobotType.TURRET.attackRadiusSquared) {
				rc.unpack();
				packCountdown = PACK_DELAY;
				return;
			}
		}
		
		if (inHealingState) {
			Radar.updateClosestEnemyTurretInfo();

			if (tryToHealAtArchon()) {
				return;
			}
		}
		
		lookForAFight();
	}
	
	private static void manageHealingState() {
		if (rc.getHealth() < 0.46 * myType.maxHealth) {
			inHealingState = true;
		}
		if (rc.getHealth() == myType.maxHealth) {
			inHealingState = false;
		}
//		Debug.indicate("heal", 0, "inHealingState = " + inHealingState);
	}
	
	private static void locateNearestArchon() throws GameActionException {
		// first look for our favorite archon
		if (rc.canSenseRobot(lastKnownArchonId)) {
			RobotInfo archon = rc.senseRobot(lastKnownArchonId);
			lastKnownArchonLocation = archon.location;
			lastKnownArchonLocationRound = rc.getRoundNum();
			return;
		}
		
		// else look for any nearby archon
		RobotInfo[] nearbyAllies = rc.senseNearbyRobots(mySensorRadiusSquared, us);
		for (RobotInfo ally : nearbyAllies) {
			if (ally.type == RobotType.ARCHON) {
				lastKnownArchonLocation = ally.location;
				lastKnownArchonLocationRound = rc.getRoundNum();
				lastKnownArchonId = ally.ID;
				return;
			}
		}
		
		// else hope that we have gotten an archon location broadcast
	}
	
	private static boolean tryToHealAtArchon() throws GameActionException {
		if (!rc.isCoreReady()) return false;
		
		locateNearestArchon();
		
		if (lastKnownArchonLocation == null) {
			return false;
		}
		
		//Nav.swarmToAvoidingArchonsAndTurret(lastKnownArchonLocation, Radar.closestEnemyTurretLocation);
		if (here.distanceSquaredTo(lastKnownArchonLocation) > 24) {
			Nav.goToBug(lastKnownArchonLocation);			
		} else {
			Nav.swarmToAvoidingArchonsAndTurret(lastKnownArchonLocation, Radar.closestEnemyTurretLocation);
		}
//		Debug.indicate("heal", 1, "going to heal at " + lastKnownArchonLocation);
		return true;
	}

	private static double enemyScore(RobotType type, double health) {
		switch(type) {
		case ARCHON:
			return 0.0001;
		case ZOMBIEDEN:
			return 0.00001;
			
		case SCOUT:
			return 0.25 * RobotType.TURRET.attackPower / (health * RobotType.TURRET.attackDelay);
		case TTM:
			return 0.5 * RobotType.TURRET.attackPower / (health * RobotType.TURRET.attackDelay);
		case TURRET:
			return type.attackPower / (health * type.attackDelay);
		case VIPER:
			// vipers are dangerous and their attackPower and attackDelay
			// do not reflect their true strength
			return 10 / (health * 1);

		default:
			return type.attackPower / (health * type.attackDelay);
		}
	}
	
	public static boolean tryToAttackAnEnemy(RobotInfo[] attackableEnemies) throws GameActionException {
//		Debug.indicateAppend("bytecodes", 1, "; tryToAttack start: " + Clock.getBytecodeNum());
		MapLocation bestTarget = null;
		double maxScore = -99;
		boolean weAreAttacked = false;
//		Debug.indicate("target", 0, "visible targets: ");
		for (RobotInfo hostile : attackableEnemies) {
			if (!rc.canAttackLocation(hostile.location)) continue;
			boolean hostileIsAttackingUs = hostile.location.distanceSquaredTo(here) <= hostile.type.attackRadiusSquared;
			if (weAreAttacked) {
				if (!hostileIsAttackingUs) {
					continue;
				}
			} else {
				if (hostileIsAttackingUs) {
					weAreAttacked = true;
					maxScore = -99;
				}
			}
			
			double score = enemyScore(hostile.type, hostile.health);
//			Debug.indicateAppend("target", 0, String.format("%s(%.1f)=%.5f, ", hostile.type, hostile.health, score));
			if (score > maxScore) {
				maxScore = score;
				bestTarget = hostile.location;				
			}
		}
//		Debug.indicate("target", 1, "radar targets: ");
//		Debug.indicateAppend("bytecodes", 1, "; after visible targets: " + Clock.getBytecodeNum());
//		Debug.indicate("bytecodes", 2, "; numCachedEnemies = " + Radar.numCachedEnemies);
		for (int i = 0; i < Radar.numCachedEnemies; ++i) {
			FastRobotInfo hostile = Radar.enemyCache[i];
			if (here.distanceSquaredTo(hostile.location) <= mySensorRadiusSquared) continue;
			if (!rc.canAttackLocation(hostile.location)) continue;
			
			boolean hostileIsAttackingUs = hostile.location.distanceSquaredTo(here) <= hostile.type.attackRadiusSquared;
			if (weAreAttacked) {
				if (!hostileIsAttackingUs) {
					continue;
				}
			} else {
				if (hostileIsAttackingUs) {
					weAreAttacked = true;
					maxScore = -99;
				}
			}

			double score = enemyScore(hostile.type, hostile.type.maxHealth);
			if (hostile.location.equals(lastAttackLocation)) {
				score *= 2; // prefer to finish off enemies
			}
//			Debug.indicateAppend("target", 1, String.format("%s=%.5f, ", hostile.type, score));
			if (score > maxScore) {
				maxScore = score;
				bestTarget = hostile.location;	
			}			
		}
//		Debug.indicateAppend("bytecodes", 1, "; after radar targets: " + Clock.getBytecodeNum());
		if (bestTarget == null) {
			BigRobotInfo closestEnemyTurret = Radar.updateClosestEnemyTurretInfo();
			if (closestEnemyTurret != null && rc.canAttackLocation(closestEnemyTurret.location)) {
				bestTarget = closestEnemyTurret.location;
				numAttacksOnRememberedTurret += 1;
				if (numAttacksOnRememberedTurret >= 10) {
					closestEnemyTurret.location = null;
//					Radar.removeEnemyTurret(closestEnemyTurret.ID);
				}
		    }
		} else {
			numAttacksOnRememberedTurret = 0;
		}
		lastAttackLocation = bestTarget;
//		Debug.indicate("target", 2, "bestTarget = " + bestTarget);
//		Debug.indicateAppend("bytecodes", 1, "; after selection: " + Clock.getBytecodeNum());
		if (bestTarget != null) {
			rc.attackLocation(bestTarget);
			return true;
		}
		return false;
	}

	private static void processSignals() throws GameActionException {
		Radar.clearEnemyCache();
		boolean processRadar = (rc.getType() == RobotType.TTM) || rc.isWeaponReady();
		
		Signal[] signals = rc.emptySignalQueue();
//		Debug.indicateAppend("bytecodes", 0, "; signals.length=" + signals.length);
		for (Signal sig : signals) {
			if (sig.getTeam() != us) continue;

			int[] data = sig.getMessage();
			if (data != null) {
				switch(data[0] & Messages.CHANNEL_MASK) {
				case Messages.CHANNEL_RADAR:
					if (processRadar) {
						//MapLocation closest = Messages.addRadarDataToEnemyCacheAndReturnClosestHit(data, sig.getLocation(), myAttackRadiusSquared);
						MapLocation closest = Messages.addRadarDataToEnemyCacheAndReturnClosestNonScoutHit(data, sig.getLocation(), myAttackRadiusSquared);
						if (closest != null) {
							if (attackTarget == null
									|| here.distanceSquaredTo(closest) < here.distanceSquaredTo(attackTarget)) {
								attackTarget = closest;
							}
						}
					}
					break;
					
				case Messages.CHANNEL_ARCHON_LOCATION:
					MapLocation archonLoc = Messages.parseArchonLocation(data);
//					Debug.indicate("heal", 2, "got archonLoc = " + archonLoc);
					if (lastKnownArchonLocation == null 
							|| (lastKnownArchonLocationRound < rc.getRoundNum() - 50)
							|| here.distanceSquaredTo(lastKnownArchonLocation) > here.distanceSquaredTo(archonLoc)) {
						lastKnownArchonLocation = archonLoc;
						lastKnownArchonLocationRound = rc.getRoundNum();
//						Debug.indicateAppend("heal", 2, "; new best");
					}
					break;
					
//				case Messages.CHANNEL_ENEMY_TURRET_WARNING:
//					Messages.processEnemyTurretWarning(data);
//					break;
					
				case Messages.CHANNEL_ROBOT_LOCATION:
					Messages.processRobotLocation(sig, data);
					break;
					
				default:
				}
			}
		}
		
		/*MapLocation closestRadarHit = null;
		int bestDistSq = Integer.MAX_VALUE;
		for (int i = 0; i < Radar.numCachedEnemies; ++i) {
			MapLocation hitLoc = Radar.enemyCache[i].location;
			int distSq = here.distanceSquaredTo(hitLoc);
			if (distSq < bestDistSq) {
				bestDistSq = distSq;
				closestRadarHit = hitLoc;
			}
		}
		Debug.indicate("radar", 0, "numCachedEnemies = " + Radar.numCachedEnemies + "; closestRadarHit = " + closestRadarHit);
		if (closestRadarHit != null) {
			if (attackTarget == null
					|| here.distanceSquaredTo(closestRadarHit) < here.distanceSquaredTo(attackTarget)) {
				attackTarget = closestRadarHit;
			}
		}
		Debug.indicate("radar", 1, "attackTarget = " + attackTarget);
		if (attackTarget != null) {
			Debug.indicateLine("radar", here, attackTarget, 255, 0, 0);
		}*/
	}
	
	private static boolean tryGoToCenterOfMass() throws GameActionException {
		RobotInfo[] allies = rc.senseNearbyRobots(mySensorRadiusSquared, us);
		if (allies.length == 0) return false;
		int avgX = 0;
		int avgY = 0;
		int N = 0;
		for (RobotInfo ally : allies) {
			if (ally.type == RobotType.TURRET || ally.type == RobotType.TTM
					|| ally.type == RobotType.SCOUT) continue;
			avgX += ally.location.x;
			avgY += ally.location.y;
			++N;
		}
		if (N == 0) return false;
		avgX /= N;
		avgY /= N;
		Nav.goToBug(new MapLocation(avgX, avgY));
		return true;
	}
	
	private static void lookForAFight() throws GameActionException {
		if (!rc.isCoreReady()) return;

		if (attackTarget != null) {
			if (rc.canSenseLocation(attackTarget)) {
				RobotInfo targetInfo = rc.senseRobotAtLocation(attackTarget);
				if (targetInfo == null || targetInfo.team == us) {
					attackTarget = null;
				}
			}
		}
		
		if (attackTarget != null) {
			Nav.goToBug(attackTarget);
			return;
		}
		
		if (tryGoToCenterOfMass()) {
			return;
		}
		
		if (lastKnownArchonLocation != null) {
			Nav.goToBug(lastKnownArchonLocation);
		}
	}
}
