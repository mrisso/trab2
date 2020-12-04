package br.inf.ufes.ppd;

public class SubAttack {
	private String ciphertext;
	private String knowntext;
	private int initialindex;
	private int finalindex;	
	private int attacknumber;
	
	public SubAttack(String ciphertext, String knowntext, int initialindex, int finalindex, int attacknumber)
	{
		this.ciphertext = ciphertext;
		this.knowntext = knowntext;
		this.initialindex = initialindex;
		this.finalindex = finalindex;
		this.attacknumber = attacknumber;
	}

	public String getCiphertext() {
		return ciphertext;
	}

	public String getKnowntext() {
		return knowntext;
	}

	public int getInitialindex() {
		return initialindex;
	}

	public int getFinalindex() {
		return finalindex;
	}

	public int getAttacknumber() {
		return attacknumber;
	}
}