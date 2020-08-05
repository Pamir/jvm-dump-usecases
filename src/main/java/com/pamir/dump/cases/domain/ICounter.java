package com.pamir.dump.cases.domain;

public interface ICounter {

	String message();

	int plusPlus();

	int counter();

	ICounter copy(ICounter example);

	ILeak leak();
}