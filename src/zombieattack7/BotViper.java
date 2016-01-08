package zombieattack7;

import battlecode.common.*;;

public class BotViper extends Globals {
	private static int lastKnownArchonId = -1;
	private static MapLocation lastKnownArchonLocation = null;
	private static int lastKnownArchonLocationRound = -999999;

	public static void loop() {
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
	
	private static void turn() throws GameActionException {
		processSignals();

		if (rc.getRoundNum() < 2500) {
			tryToHealAtArchon();
		} else {
			ZombieRush.suicideZombieRushViper(lastKnownArchonLocation);
		}
	}
	
	private static void processSignals() {
		Signal[] signals = rc.emptySignalQueue();
		for (Signal sig : signals) {
			if (sig.getTeam() != us) continue;

			int[] data = sig.getMessage();
			if (data != null) {
				switch(data[0] & Messages.CHANNEL_MASK) {
				case Messages.CHANNEL_ARCHON_LOCATION:
					MapLocation archonLoc = Messages.parseArchonLocation(data);
					Debug.indicate("heal", 2, "got archonLoc = " + archonLoc);
					if (lastKnownArchonLocation == null 
							|| (lastKnownArchonLocationRound < rc.getRoundNum() - 50)
							|| here.distanceSquaredTo(lastKnownArchonLocation) > here.distanceSquaredTo(archonLoc)) {
						lastKnownArchonLocation = archonLoc;
						lastKnownArchonLocationRound = rc.getRoundNum();
						Debug.indicateAppend("heal", 2, "; new best");
					}
					break;
					
				default:
				}
			}
		}
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
			Debug.indicate("heal", 1, "want to heal, but lastKnownArchonLocation is null!");
			return false;
		}
		
		Debug.indicate("heal", 1, "going to heal at archon at " + lastKnownArchonLocation);
		DirectNav.swarmToAvoidingArchons(lastKnownArchonLocation);
		return true;
	}
	
	
}
