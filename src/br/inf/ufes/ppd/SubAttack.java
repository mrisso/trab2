package br.inf.ufes.ppd;
import java.io.Serializable;

public class SubAttack implements Serializable{
	private static final long serialVersionUID = 1L;
	private byte[] ciphertext;
	private byte[] knowntext;
	private int initialindex;
	private int finalindex;	
	private int attacknumber;
	
	public SubAttack(byte[] ciphertext, byte[] knowntext, int initialindex, int finalindex, int attacknumber)
	{
		this.ciphertext = ciphertext;
		this.knowntext = knowntext;
		this.initialindex = initialindex;
		this.finalindex = finalindex;
		this.attacknumber = attacknumber;
	}

	public byte[] getCiphertext() {
		return ciphertext;
	}

	public byte[] getKnowntext() {
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