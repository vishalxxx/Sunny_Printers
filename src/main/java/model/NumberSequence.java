package model;

public class NumberSequence {

	private String sequenceKey;
	private String displayName;
	private String prefix;
	private long currentNumber;
	private int digitWidth;
	private String financialYear;
	/** Local-only counter when Supabase is unreachable (TEMP-* numbers). */
	private long offlineCurrentNumber;

	public String getSequenceKey() { return sequenceKey; }
	public void setSequenceKey(String sequenceKey) { this.sequenceKey = sequenceKey; }
	public String getDisplayName() { return displayName; }
	public void setDisplayName(String displayName) { this.displayName = displayName; }
	public String getPrefix() { return prefix; }
	public void setPrefix(String prefix) { this.prefix = prefix; }
	public long getCurrentNumber() { return currentNumber; }
	public void setCurrentNumber(long currentNumber) { this.currentNumber = currentNumber; }
	public int getDigitWidth() { return digitWidth; }
	public void setDigitWidth(int digitWidth) { this.digitWidth = digitWidth; }
	public String getFinancialYear() { return financialYear; }
	public void setFinancialYear(String financialYear) { this.financialYear = financialYear; }
	public long getOfflineCurrentNumber() { return offlineCurrentNumber; }
	public void setOfflineCurrentNumber(long offlineCurrentNumber) { this.offlineCurrentNumber = offlineCurrentNumber; }
}