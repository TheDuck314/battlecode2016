package first;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.RobotType;

public class BotArchon extends Globals {
	public static void loop() {
		while (true) {
			try {
			    turn();
			} catch (Exception e) {
				e.printStackTrace();
			}
			Clock.yield();
		}
	}
	
	private static void turn() throws GameActionException {
		trySpawn();
	}
	
	private static void trySpawn() throws GameActionException {
		if (!rc.isCoreReady()) return;
		for (Direction dir : Direction.values()) {
			if (rc.canBuild(dir, RobotType.SOLDIER)) {
				rc.build(dir, RobotType.SOLDIER);
				return;
			}
		}
	}
}
