package zombieattack7;

import battlecode.common.*;
import zombieattack7.Messages.PartsLocation;

public class BotArchon extends Globals {
	private static MapLocationHashSet knownZombieDens = new MapLocationHashSet();
	private static MapLocation[] denAttackQueue = new MapLocation[1000];
	private static int denAttackQueueHead = 0;
	private static int denAttackQueueTail = 0;

	private static int spawnCount = 0;
	private static int numVipersSpawned = 0;

	private static int lastArchonLocationMessageRound = 0;

	private static int nArchons = 0;
	private static MapLocation[] archonsLoc = new MapLocation[10];
	private static int[] archonsId = new int[10];
	private static int archonOrder = 0;
	private static MapLocation rallyPoint = null;
	
	public static void loop() throws GameActionException {
		Debug.init("heal");
		FastMath.initRand(rc);
		initArchons();

		while (true) {
			try {
				Globals.update();
			    turn();
			} catch (Exception e) {
				e.printStackTrace();
			}
			Clock.yield();
		}
	}
	
	private static void initArchons() throws GameActionException {
		Globals.update();
		archonsLoc[nArchons] = here;
		archonsId[nArchons] = myID;
		nArchons += 1;
		Messages.sendArchonLocation(here, MapEdges.maxBroadcastDistSq());
		Clock.yield();
		Signal[] signals = rc.emptySignalQueue();
		for (Signal sig : signals) {
			if (sig.getTeam() != us) continue;
			int[] data = sig.getMessage();
			if (data != null) {
				if ((data[0] & Messages.CHANNEL_MASK) == Messages.CHANNEL_ARCHON_LOCATION) {
					archonsLoc[nArchons] = sig.getLocation();
					archonsId[nArchons] = sig.getID();
					nArchons += 1;
				}
			}
		}
		rallyPoint = here;
		for (int i = 1; i < nArchons; ++i) {
			rallyPoint = FastMath.addVec(rallyPoint, archonsLoc[i]);
		}
		rallyPoint = FastMath.multiplyVec(1.0/(double)nArchons, rallyPoint);
		rallyPoint = rallyPoint.add(rallyPoint.directionTo(here), 0);
		rc.setIndicatorDot(rallyPoint, 255, 0, 0);
		for (int i = 0; i < nArchons; ++i) {
			if (archonsId[i] < myID) {
				archonOrder += 1;
			}
		}
	}
	
	private static void turn() throws GameActionException {
		if (rc.getRoundNum() <= 100 && rallyPoint != null && here.distanceSquaredTo(rallyPoint) > 2) {
			if (rc.isCoreReady()) {
			    DBug.goTo(rallyPoint);
			}
			return;
		}
		
		processSignals();
		MapEdges.detectAndBroadcastMapEdges(5); // visionRange = 5

		trySendAttackTarget();
		
		trySendArchonLocationMessage();

		if (rc.getRoundNum() > 2000) {
			if (ZombieRush.enemyCenterOfMass != null) {
				if (rc.isCoreReady()) {
					Bug.tryHardMoveInDirection(ZombieRush.enemyCenterOfMass.directionTo(here));
				}
			}
			if (rc.getRoundNum() < 2500) {
				trySpawn();
			}
			return;
		}
		
		trySpawn();
		
		tryRepairAlly();
		tryConvertNeutrals();
		

		//exploreForNeutralsAndParts();	
	}
	
	private static void trySendArchonLocationMessage() throws GameActionException {
		if (lastArchonLocationMessageRound < rc.getRoundNum() - 40) {
			Messages.sendArchonLocation(here, MapEdges.maxBroadcastDistSq());
			lastArchonLocationMessageRound = rc.getRoundNum();
			Debug.indicate("heal", 0, "sent archon location");
		}
	}
	
	private static void trySendAttackTarget() throws GameActionException {
		RobotInfo[] targets = rc.senseHostileRobots(here, mySensorRadiusSquared);
		int numTargets = (targets.length < 3 ? targets.length : 3);
		for (int i = 0; i < numTargets; ++i) {
			Messages.sendAttackTarget(targets[i].location, 9 * mySensorRadiusSquared);
		}
	}
	
	private static void tryAttackZombieDen() throws GameActionException {
		if (!rc.isCoreReady()) return;
		
		MapLocation target = denAttackQueue[denAttackQueueHead];
		if (target == null) {
			Debug.indicate("dens", 0, "target is null :(");
			return;
		}

		if (rc.canSenseLocation(target)) {
			RobotInfo botAtTarget = rc.senseRobotAtLocation(target);
			if (botAtTarget == null || botAtTarget.type != RobotType.ZOMBIEDEN) {
				// this den has been destroyed. Move on to the next one.
				++denAttackQueueHead;
				Debug.indicate("dens", 0, "den at " + target + " seems to be destroyed. now head = " + denAttackQueueHead);
				return;
			}
		}
		
		if (here.distanceSquaredTo(target) <= 20) {
			Debug.indicate("dens", 0, "stationed near den at " + target);
			//Messages.sendAttackTarget(target, 100*mySensorRadiusSquared);
			return;
		}
		
		Debug.indicate("dens", 0, "going to den at " + target);
		//Bug.goTo(target);
		DirectNav.goTo(target);
		//Messages.sendAttackTarget(target, 2*mySensorRadiusSquared);
	}

	private static void trySpawn() throws GameActionException {
		if (!rc.isCoreReady()) return;

		RobotType spawnType = (spawnCount % 10 == 0 ? RobotType.SCOUT : RobotType.SOLDIER);

		if (rc.getRoundNum() > 1000) {
			spawnType = RobotType.VIPER;
			if (numVipersSpawned >= 2) {
				spawnType = RobotType.SCOUT;
			}
		}
		
		if (!rc.hasBuildRequirements(spawnType)) return;

		Direction dir = Direction.values()[FastMath.rand256() % 8];
		for (int i = 0; i < 8; ++i) {
			if (rc.canBuild(dir, spawnType)) {
				rc.build(dir, spawnType);
				++spawnCount;
				if (spawnType == RobotType.SCOUT) {
					Messages.sendKnownMapEdges(2); // tell scout known map edges
				}
				if (spawnType == RobotType.VIPER) {
					++numVipersSpawned;
				}
			}
		}
	}
	
	private static void tryRepairAlly() throws GameActionException {
		RobotInfo[] healableAllies = rc.senseNearbyRobots(RobotType.ARCHON.attackRadiusSquared, us);
		MapLocation bestLoc = null;
		double mostHealth = 0;
		for (RobotInfo ally : healableAllies) {
			if (ally.type == RobotType.ARCHON) continue;
			if (ally.health < ally.maxHealth && ally.health > mostHealth) {
				bestLoc = ally.location;
				mostHealth = ally.health;
			}
		}
		if (bestLoc != null) {
			rc.repair(bestLoc);
		}
	}
	
	private static void tryConvertNeutrals() throws GameActionException {
		if (!rc.isCoreReady()) return;
		RobotInfo[] adjacentNeutrals = rc.senseNearbyRobots(2, Team.NEUTRAL);
		for (RobotInfo neutral : adjacentNeutrals) {
			rc.activate(neutral.location);
			return;
		}
	}
	
	
	private static void processSignals() {
		Signal[] signals = rc.emptySignalQueue();
		for (Signal sig : signals) {
			if (sig.getTeam() != us) continue;
			
			int[] data = sig.getMessage();
			if (data != null) {
				switch(data[0] & Messages.CHANNEL_MASK) {
				case Messages.CHANNEL_FOUND_PARTS:
					PartsLocation partsLoc = Messages.parsePartsLocation(data);
					Debug.indicate("explore", 0, "heard about " + partsLoc.numParts + " parts at " + partsLoc.location);
					break;
				case Messages.CHANNEL_ZOMBIE_DEN:
					MapLocation zombieDenLoc = Messages.parseZombieDenLocation(data);
					Debug.indicate("explore", 1, "heard about a zombie den at " + zombieDenLoc);
					if (knownZombieDens.add(zombieDenLoc)) {
						denAttackQueue[denAttackQueueTail++] = zombieDenLoc;
					} else {
						Debug.indicateAppend("explore", 1, "; but we already knew about it");
					}
					break;
				case Messages.CHANNEL_ENEMY_TURRET_WARNING:
					MapLocation enemyTurretLoc = Messages.parseEnemyTurretWarning(data);
					Debug.indicate("explore", 2, "heard about an enemy turret at " + enemyTurretLoc);
					break;
					
				case Messages.CHANNEL_MAP_MIN_X:
					Messages.processMapMinX(data);
					break;
				case Messages.CHANNEL_MAP_MAX_X:
					Messages.processMapMaxX(data);
					break;
				case Messages.CHANNEL_MAP_MIN_Y:
					Messages.processMapMinY(data);
					break;
				case Messages.CHANNEL_MAP_MAX_Y:
					Messages.processMapMaxY(data);
					break;
					
				default:
				}
			} else {
				// simple signal with no message
			}
		}
	}
}
