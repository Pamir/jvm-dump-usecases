package com.pamir.dump.cases.domain;

public class Counter implements ICounter {

	private int counter;
	private ILeak leak;

	private static final long[] cache = new long[5000000];

	@Override
	public String message() {
		return "Version 1";
	}

	@Override
	public int plusPlus() {
		return counter++;
	}

	@Override
	public int counter() {
		return counter;
	}

	@Override
	public ICounter copy(ICounter example) {
		if (example != null) {
			counter = example.counter();
			leak = example.leak();
		}
		return this;
	}

	@Override
	public ILeak leak() {
		return new Leak(leak);
	}
}