package br.inf.ufes.ppd;

import java.util.UUID;

public class SlaveAttack extends Thread {
	private UUID myId;
	private Slave slave;
	private SlaveInfo slaveInfo;
	private byte[] ciphertext;
	private byte[] knowntext;
	private int initialindex;
	private int finalindex;	
	private int attackNumber;
	private int lastCheckedIndex;
	private SlaveManager callbackinterface;
	
	public SlaveAttack(UUID myId, Slave slave, SlaveInfo slaveInfo, byte[] ciphertext, byte[] knowntext, int initialindex, int finalindex, int attackNumber,  SlaveManager callbackinterface) {
		this.myId = myId;
		this.slave = slave;
		this.slaveInfo = slaveInfo;
		this.ciphertext = ciphertext;
		this.knowntext = knowntext;
		this.initialindex = initialindex;
		this.finalindex = finalindex;
		this.attackNumber = attackNumber;
		this.lastCheckedIndex = initialindex - 1;
		this.callbackinterface = callbackinterface;
	}
	  

	@Override
	public void run() {
		try {
			slave.startSubAttack(ciphertext, knowntext, initialindex, finalindex, attackNumber, callbackinterface);
			// o comando acima eh substituido pelo comando abaixo quando queremos saber apenas o tempo de overhead
			//callbackinterface.checkpoint(slaveInfo.getId(), attackNumber, finalindex);
		} catch (Exception e) {
			System.out.println("Escravo " +slaveInfo.getId()+ " falhou!");
		}
		
	}


	public Slave getSlave() {
		return slave;
	}


	public void setSlave(Slave slave) {
		this.slave = slave;
	}


	public SlaveInfo getSlaveInfo() {
		return slaveInfo;
	}


	public void setSlaveInfo(SlaveInfo slaveInfo) {
		this.slaveInfo = slaveInfo;
	}


	public byte[] getCiphertext() {
		return ciphertext;
	}


	public void setCiphertext(byte[] ciphertext) {
		this.ciphertext = ciphertext;
	}


	public byte[] getKnowntext() {
		return knowntext;
	}


	public void setKnowntext(byte[] knowntext) {
		this.knowntext = knowntext;
	}


	public int getInitialindex() {
		return initialindex;
	}


	public void setInitialindex(int initialindex) {
		this.initialindex = initialindex;
	}


	public int getFinalindex() {
		return finalindex;
	}


	public void setFinalindex(int finalindex) {
		this.finalindex = finalindex;
	}


	public int getAttackNumber() {
		return attackNumber;
	}


	public void setAttackNumber(int attackNumber) {
		this.attackNumber = attackNumber;
	}


	public int getLastCheckedIndex() {
		return lastCheckedIndex;
	}


	public void setLastCheckedIndex(int lastCheckedIndex) {
		this.lastCheckedIndex = lastCheckedIndex;
	}


	public UUID getMyId() {
		return myId;
	}


	public void setMyId(UUID myId) {
		this.myId = myId;
	}


	public SlaveManager getCallbackinterface() {
		return callbackinterface;
	}


	public void setCallbackinterface(SlaveManager callbackinterface) {
		this.callbackinterface = callbackinterface;
	}


}
