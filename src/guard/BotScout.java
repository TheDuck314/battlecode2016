package guard;

import battlecode.common.Clock;

public class BotScout {
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
