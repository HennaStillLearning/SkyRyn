package com.ryn.skyryn.fusion;

public class FusionState {
	public static String currentShard = null;
	public static int currentAmount = 0;
	public static String forcedTopA = null;
	public static String forcedTopB = null;
	public static int version = 0;

	public static void set(String shard, int amount) {
		set(shard, amount, null, null);
	}

	public static void set(String shard, int amount, String topA, String topB) {
		currentShard = shard;
		currentAmount = amount;
		forcedTopA = topA;
		forcedTopB = topB;
		version++;
	}
}
