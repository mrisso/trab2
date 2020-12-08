package br.inf.ufes.ppd;



/**
 * Guess.java
 */


import java.io.Serializable;

public class Guess implements Serializable {
	private static final long serialVersionUID = 1L;

	private String key;
	// chave candidata

	private byte[] message;
	// mensagem decriptografada com a chave candidata
	
	private int attackNumber;
	
	public Guess(int attackNumber) {
		this.attackNumber = attackNumber;
	}

	public String getKey() {
		return key;
	}
	public void setKey(String key) {
		this.key = key;
	}
	public byte[] getMessage() {
		return message;
	}
	public void setMessage(byte[] message) {
		this.message = message;
	}

	public int getAttackNumber() {
		return attackNumber;
	}

}
