package seeding_base18;

import battlecode.common.*;

public class BotTurret extends Globals {
	public static void loop() {
		Debug.init("target");
		while (true) {
			try {
				Globals.update();
				processSignals();
				if (rc.getType() == RobotType.TURRET) {
				    turnTurret();
				} else {
					turnTTM();
				}
			} catch (Exception e) {
				e.printStackTrace();
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
	
	private static void turnTurret() throws GameActionException {
		if (!rc.isWeaponReady() && !rc.isCoreReady()) return;
		
		Radar.removeDistantEnemyTurrets(9 * RobotType.SCOUT.sensorRadiusSquared);
		//Radar.removeOldEnemyTurrets(Radar.TURRET_MEMORY_ROUNDS);
		
		RobotInfo[] attackableEnemies = rc.senseHostileRobots(here, myAttackRadiusSquared);
		if (rc.isWeaponReady()) {
			if (tryToAttackAnEnemy(attackableEnemies)) {
				packCountdown = PACK_DELAY;
				return;
			}
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
		
		Radar.removeDistantEnemyTurrets(9 * RobotType.SCOUT.sensorRadiusSquared);
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
		
		lookForAFight();
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
		MapLocation bestTarget = null;
		double maxScore = -99;
		boolean weAreAttacked = false;
		Debug.indicate("target", 0, "visible targets: ");
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
			Debug.indicateAppend("target", 0, String.format("%s(%.1f)=%.5f, ", hostile.type, hostile.health, score));
			if (score > maxScore) {
				maxScore = score;
				bestTarget = hostile.location;				
			}
		}
		Debug.indicate("target", 1, "radar targets: ");
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
			Debug.indicateAppend("target", 1, String.format("%s=%.5f, ", hostile.type, score));
			if (score > maxScore) {
				maxScore = score;
				bestTarget = hostile.location;	
			}			
		}
		if (bestTarget == null) {
			FastTurretInfo closestEnemyTurret = Radar.findClosestEnemyTurret();
			if (closestEnemyTurret != null && rc.canAttackLocation(closestEnemyTurret.location)) {
				bestTarget = closestEnemyTurret.location;
				numAttacksOnRememberedTurret += 1;
				if (numAttacksOnRememberedTurret >= 10) {
					Radar.removeEnemyTurret(closestEnemyTurret.ID);
				}
		    }
		} else {
			numAttacksOnRememberedTurret = 0;
		}
		lastAttackLocation = bestTarget;
		Debug.indicate("target", 2, "bestTarget = " + bestTarget);
		if (bestTarget != null) {
			rc.attackLocation(bestTarget);
			return true;
		}
		return false;
	}

	private static void processSignals() throws GameActionException {
		Radar.clearEnemyCache();
		
		Signal[] signals = rc.emptySignalQueue();
		for (Signal sig : signals) {
			if (sig.getTeam() != us) continue;

			int[] data = sig.getMessage();
			if (data != null) {
				switch(data[0] & Messages.CHANNEL_MASK) {
				/*case Messages.CHANNEL_ATTACK_TARGET:
					MapLocation suggestedTarget = Messages.parseAttackTarget(data);
					if (attackTarget == null || here.distanceSquaredTo(suggestedTarget) < here.distanceSquaredTo(attackTarget)) {
						attackTarget = suggestedTarget;
						attackTargetReceivedRound = rc.getRoundNum();
					}
					break;*/
				case Messages.CHANNEL_RADAR:
					Messages.addRadarDataToEnemyCache(data, sig.getLocation(), myAttackRadiusSquared);
					MapLocation closest = Messages.getClosestRadarHit(data, sig.getLocation());
					if (attackTarget == null
							|| here.distanceSquaredTo(closest) < here.distanceSquaredTo(attackTarget)) {
						attackTarget = closest;
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
					
				case Messages.CHANNEL_ENEMY_TURRET_WARNING:
					Messages.processEnemyTurretWarning(data);
					break;
					
				case Messages.CHANNEL_ROBOT_LOCATION:
					Messages.processRobotLocation(sig, data);
					break;
					
				default:
				}
			}
		}
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
