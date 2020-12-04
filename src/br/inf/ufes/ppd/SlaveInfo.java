package br.inf.ufes.ppd;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SlaveInfo {
	private Slave sref;
	private String name;
	private UUID id;
//	private int initialindex;
//	private int finalindex;	
//	private SlaveManager callbackinterface;
//	private int attackNumber;
	private long lastCheckIn; // momento do envio da ultima mensagem ao mestre
//	private int lastCheckedIndex;
	private List<UUID> attacks;
	private int active;
	
	
	public SlaveInfo(Slave sref, String name, UUID id) {
		this.sref = sref;
		this.name = name;
		this.id = id;
		this.lastCheckIn = System.nanoTime();
		this.attacks = new ArrayList<UUID>();
		this.active = 0;
	}
	
	public UUID getId() {
		return id;
	}
	public void setId(UUID id) {
		this.id = id;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public Slave getSref() {
		return sref;
	}
	public void setSref(Slave sref) {
		this.sref = sref;
	}
/*
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
*/
/*	public SlaveManager getCallbackinterface() {
		return callbackinterface;
	}

	public void setCallbackinterface(SlaveManager callbackinterface) {
		this.callbackinterface = callbackinterface;
	}

	public int getAttackNumber() {
		return attackNumber;
	}

	public void setAttackNumber(int attackNumber) {
		this.attackNumber = attackNumber;
	}
*/
	public long getLastCheckIn() {
		return lastCheckIn;
	}

	public void setLastCheckIn(long lastCheckIn) {
		this.lastCheckIn = lastCheckIn;
	}
/*
	public int getLastCheckedIndex() {
		return lastCheckedIndex;
	}

	public void setLastCheckedIndex(int lastCheckedIndex) {
		this.lastCheckedIndex = lastCheckedIndex;
	}
*/
	public List<UUID> getAttacks() {
		return attacks;
	}

	public void setAttacks(List<UUID> attacks) {
		this.attacks = attacks;
	}

	public int getActive() {
		return active;
	}

	public void setActive(int n) {
		this.active += n;
	}
	
	public void clearActive() {
		this.active = 0;
	}
}
