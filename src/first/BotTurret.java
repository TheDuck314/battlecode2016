package first;

import battlecode.common.Clock;

public class BotTurret {
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
	
	private static void turn() {
		
	}
}
