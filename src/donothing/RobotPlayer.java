package donothing;

import battlecode.common.Clock;
import battlecode.common.RobotController;

public class RobotPlayer {
	public static void run(RobotController rc) {
		while(true) Clock.yield();
	}
}
