package service;

import java.sql.Connection;

import service.sync.UniversalTemporaryNumberEngine;

/**
 * @deprecated Use {@link UniversalTemporaryNumberEngine#getInstance()} instead.
 */
@Deprecated
public class TemporaryNumberEngine {

	private TemporaryNumberEngine() {
	}

	public static TemporaryNumberEngine getInstance() {
		return Holder.INSTANCE;
	}

	public String generate(Connection con, String sequenceKey) {
		return UniversalTemporaryNumberEngine.getInstance().generate(con, sequenceKey);
	}

	private static final class Holder {
		static final TemporaryNumberEngine INSTANCE = new TemporaryNumberEngine();
	}
}
