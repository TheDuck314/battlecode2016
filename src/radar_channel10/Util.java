package radar_channel10;

import battlecode.common.*;

public class Util extends Globals {
	public static RobotInfo closest(RobotInfo[] infos) {
		RobotInfo ret = null;
		int minDistSq = Integer.MAX_VALUE;
		for (int i = infos.length; i --> 0; ) {
			int distSq = here.distanceSquaredTo(infos[i].location);
			if (distSq < minDistSq) {
				minDistSq = distSq;
				ret = infos[i];
			}
		}
		return ret;
	}
	
	
	public static RobotInfo[] truncateArray(RobotInfo[] arr, int maxLength) {
		RobotInfo[] ret = new RobotInfo[maxLength];
		for (int i = 0; i < maxLength; ++i) {
			ret[i] = arr[i];
		}
		return ret;
	}
}
