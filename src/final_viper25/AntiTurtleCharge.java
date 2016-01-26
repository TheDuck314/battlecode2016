package final_viper25;

import battlecode.common.*;

public class AntiTurtleCharge extends Globals {
	public static MapLocation chargeCenter = null;
	
    public static int chargeRound = 0; // what round to begin the charge
    public static int endRound = 0; // what round to end the charge
	public static int lastProposalRound = 0; // time last proposal was made
    
	// how long we will spend gathering after the veto wait period is over
	public static final int GATHER_WAIT_ROUNDS = 100;
	
	// how long the charge itself will last once it begins
	public static final int CHARGE_LENGTH = 75;
	
	private static void planCharge(MapLocation center, int round) {
		chargeCenter = center;
		chargeRound = round;
		endRound = chargeRound + CHARGE_LENGTH;
	}

	public static void processAntiTurtleChargeMessage(int[] data) {
		if (Messages.parseNotATurtle(data)) {
			enemyMightBeATurtle = false;
			haveBroadcastedNotATurtle = true;
			chargeCenter = null;
		}
		if (!enemyMightBeATurtle) return;
		
		if (Messages.parseAntiTurtleChargeVeto(data)) {
//			Debug.indicate("charge", 0, "received a charge veto!!");
			chargeCenter = null;
		} else {
			AntiTurtleChargePlan plan = Messages.parseAntiTurtleChargePlan(data);
			planCharge(plan.chargeCenter, plan.chargeRound);
//			Debug.indicate("charge", 0, "received a charge proposal! center = " + chargeCenter + ", chargeRound = " + chargeRound);
		}
	}
	
	public static void proposeCharge(MapLocation proposalCenter) throws GameActionException {
		planCharge(proposalCenter, rc.getRoundNum() + GATHER_WAIT_ROUNDS);
		Messages.proposeAntiTurtleChargePlan(proposalCenter, chargeRound, MapEdges.maxRangeSq);
	}
	
	public static boolean enemyMightBeATurtle = true;
	public static boolean haveBroadcastedNotATurtle = false;
	private static MapLocation centerOfMassOfSeenEnemies = new MapLocation(0, 0);
	private static int numSeenEnemies = 0;
	
	public static void runTurtleDetector(RobotInfo[] enemies, RobotInfo[] hostiles) throws GameActionException {
		if (enemyMightBeATurtle) {
			if (enemies.length > 0) {
				int newCenterX = centerOfMassOfSeenEnemies.x * numSeenEnemies;
				int newCenterY = centerOfMassOfSeenEnemies.y * numSeenEnemies;
				int numNewEnemies = 0;
				for (RobotInfo enemy : enemies) {
					if (enemy.type == RobotType.ARCHON || enemy.type == RobotType.SCOUT) continue;
					if (numSeenEnemies > 0 
							&& enemy.location.distanceSquaredTo(centerOfMassOfSeenEnemies) >= 150) {
						enemyMightBeATurtle = false;
//						Debug.indicate("detector", 0, "enemy at " + enemy.location + " is too far from COM at " + centerOfMassOfSeenEnemies + " so NOT A TURTLE!");
						return;
					}
					newCenterX += enemy.location.x;
					newCenterY += enemy.location.y;
					numNewEnemies += 1;
				}
				if (numNewEnemies > 0) {
					numSeenEnemies += numNewEnemies;
					newCenterX /= numSeenEnemies;
					newCenterY /= numSeenEnemies;
					centerOfMassOfSeenEnemies = new MapLocation(newCenterX, newCenterY);
//					Debug.indicate("detector", 1, "saw " + numNewEnemies + " new enemies, new com at " + centerOfMassOfSeenEnemies);
//					Debug.indicateLine("detector", here, centerOfMassOfSeenEnemies, 0, 255, 0);
				} else {
//					Debug.indicate("detector", 0, "only scouts and archons are visible");
				}
			} else {
//				Debug.indicate("detector", 0, "no enemies");
			}

		} else {
			if (!haveBroadcastedNotATurtle) {
				boolean dangerousHostileNearby = false;
				for (RobotInfo hostile : hostiles) {
					if (hostile.type.canAttack()) {
						dangerousHostileNearby = true;
						break;
					}
				}
				if (!dangerousHostileNearby) {
					Messages.sendNotATurtle(MapEdges.maxRangeSq);
					chargeCenter = null;
					haveBroadcastedNotATurtle = true;
				}
			}
		}
	}
}
