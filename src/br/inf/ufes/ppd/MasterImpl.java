package br.inf.ufes.ppd;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.google.gson.Gson;
import javax.jms.*;

import com.sun.messaging.ConnectionConfiguration;

public class MasterImpl implements Master {
	private static Master mref;
	private static MasterImpl master;
	protected ConcurrentHashMap<UUID, SlaveInfo> slavesInfo = new ConcurrentHashMap<UUID, SlaveInfo>();
	protected ConcurrentHashMap<UUID, Thread> subattacks = new ConcurrentHashMap<UUID, Thread>();
	protected ConcurrentHashMap<UUID, Thread> checks = new ConcurrentHashMap<UUID, Thread>();
	protected List<UUID> failedAttacks = new ArrayList<UUID>();
	protected ConcurrentHashMap<Integer, ArrayList<Guess>> guessList = new ConcurrentHashMap<Integer, ArrayList<Guess>>();
	private static List<String> dictionary = new ArrayList<String>();
	private static int attackNumber = 0;
	private int active = 0;
	private int m;
	private static JMSContext context;
	private static JMSProducer producer;
	private static Queue subAttackQueue;
	private static Queue guessesQueue;
	
	public MasterImpl(int m/*, JMSContext context, JMSProducer producer, Queue subAttackQueue, Queue guessesQueue*/) { 
		this.m = m;
		/*
		MasterImpl.context = context;
		MasterImpl.producer = producer;
		MasterImpl.subAttackQueue = subAttackQueue;
		MasterImpl.guessesQueue = guessesQueue;
		*/
    }
	
	@Override
	public void foundGuess(UUID slaveKey, int attackNumber, long currentindex, Guess currentguess)
			throws RemoteException {
		int localindex = (int) currentindex;
		System.out.println("FOUNDGUESS: slaveKey -> " + slaveKey + " attackNumber -> " + attackNumber + " currentIndex -> " + localindex + " key -> " + currentguess.getKey());
		SlaveInfo si = slavesInfo.get(slaveKey);
		synchronized(si) {
			si.setLastCheckIn(System.nanoTime());
			
			List<UUID> attacksId = si.getAttacks();
			
			// percorre a lista de ataques do escravo ate encontrar o ataque na qual esse checkpoint pertence, 
			// e atualiza seu ultimo indice checado
			synchronized(attacksId) {
				for(UUID id : attacksId) {
					SlaveAttack sub = (SlaveAttack) subattacks.get(id);
					
					synchronized(sub) {
						if(localindex >= sub.getInitialindex() && localindex <= sub.getFinalindex() && sub.getAttackNumber() == attackNumber && localindex >= sub.getLastCheckedIndex()) {
							// indice do checkpoint pertence a este subataque
							sub.setLastCheckedIndex( localindex); 
							break;
						}
					}
				}
			}
		}
		synchronized(guessList.get(attackNumber)) {
			guessList.get(attackNumber).add(currentguess);
		}
		
	}

	@Override
	public void checkpoint(UUID slaveKey, int attackNumber, long currentindex) throws RemoteException {
		SlaveInfo si = slavesInfo.get(slaveKey);

		int localindex = (int) currentindex;
		synchronized(si) {
			si.setLastCheckIn(System.nanoTime());
			
			List<UUID> attacksId = si.getAttacks();
			
			// percorre a lista de ataques do escravo ate encontrar o ataque na qual esse checkpoint pertence, 
			// e atualiza seu ultimo indice checado
			
			synchronized(attacksId) {
				for(UUID id : attacksId) {
					SlaveAttack sub = (SlaveAttack) subattacks.get(id);
					synchronized(sub) {
						if(localindex >= sub.getInitialindex() && localindex <= sub.getFinalindex() && sub.getAttackNumber() == attackNumber && localindex >= sub.getLastCheckedIndex()) {
							// indice do checkpoint pertence a este subataque
							sub.setLastCheckedIndex( localindex); 
							if(sub.getFinalindex() == localindex) {
								System.out.println("CHECKPOINT: slaveKey -> " + slaveKey + " attackNumber -> " + sub.getAttackNumber() + " currentIndex -> " + localindex +" [LAST]");
								synchronized(si) {
									si.setActive(-1);
								}
							} else {
								System.out.println("CHECKPOINT: slaveKey -> " + slaveKey + " attackNumber -> " + sub.getAttackNumber() + " currentIndex -> " + localindex);
							}
							break;
						}
					}
				}
			}
		}
	}
	
	// verifica se ha alguma thread de ataque viva
	public boolean thereAreThreadsRunning(int attackNumber) {
		
		boolean running = false;
		
		for(Entry<UUID, Thread> sa : subattacks.entrySet()) {
			SlaveAttack slaveattack = (SlaveAttack) sa.getValue();
			// true caso alguma thread deste ataque esteja viva
			if (slaveattack.getAttackNumber() == attackNumber && sa.getValue().isAlive()) {
				running = true;
				break;
			}
		}
		return running;
	}
	
	@Override
	public Guess[] attack(String ciphertext, String knowntext) throws RemoteException {
		System.out.println("Tentando ler dicionário");
		if(!readDictionary())
			return null;
		
		attackNumber++;
		
		int totalWords = dictionary.size();
		int vectorSize = totalWords/m;
		int remainder = totalWords%m;
		int start = 0;
		int end = vectorSize - 1;
		
		for(int i = 0; i < m; i++)
		{
			if (remainder > 0) {
				end++;
				remainder--;
			}
			
			SubAttack sub = new SubAttack(ciphertext, knowntext, start, end, attackNumber);
			
			Gson gson = new Gson();

			String json = gson.toJson(sub);
			System.out.println(json);
			TextMessage message = context.createTextMessage(); 
			try {
				message.setText(json);
			} catch (Exception e) {
				System.out.println("Não foi possível criar a mensagem.");
			}
			producer.send(subAttackQueue,message);

			start = end + 1;
			end += vectorSize;
		}

		return null;
	}
	
	public boolean readDictionary() {
		try {
			Path dictionaryPath =  Paths.get("dictionary.txt"); // dicionario deve estar na pasta tmp de cada pc
			dictionary = (ArrayList<String>) Files.readAllLines(dictionaryPath, StandardCharsets.UTF_8);
		} catch (Exception e) {
			System.out.println("Erro na leitura do arquivo :(");
			return false;
		}
		return true;
	}
	
	public static void main(String[] args) {
		String host = (args.length < 1) ? "127.0.0.1" : args[0];

		int m = args.length > 1 ? Integer.parseInt(args[0]) : 4;
		master = new MasterImpl(m);
		
		try {
			System.out.println("obtaining connection factory...");
			com.sun.messaging.ConnectionFactory connectionFactory = new com.sun.messaging.ConnectionFactory();
			connectionFactory.setProperty(ConnectionConfiguration.imqAddressList,host+":7676");	
			System.out.println("obtained connection factory.");
			
			System.out.println("obtaining sub queue...");
			subAttackQueue = new com.sun.messaging.Queue("SubAttacksQueue");
			System.out.println("obtained queue.");

			System.out.println("obtaining guesses queue...");
			guessesQueue = new com.sun.messaging.Queue("GuessesQueue");
			System.out.println("obtained queue.");

			context = connectionFactory.createContext();
			producer = context.createProducer();
		} catch (Exception e) {
			System.out.println("Não foi possível obter as filas.");
			System.exit(2);
		}

		// implementacao paralela (com escravos)
		try {
			mref = (Master) UnicastRemoteObject.exportObject(master, 0);
			
			//Registry registry = LocateRegistry.getRegistry();
			System.setProperty("java.rmi.server.hostname", host);
			Registry registry = LocateRegistry.createRegistry(1099);
			registry.rebind("mestre", mref);
			System.out.println("Mestre ligado!");
		} catch (RemoteException e) {
			System.out.println("O mestre nao conseguiu se iniciar :(");
			//e.printStackTrace();
			System.exit(1);
		}
	}

	public int indexOf(byte[] outerArray, byte[] smallerArray) {
	    for(int i = 0; i < outerArray.length - smallerArray.length+1; ++i) {
	        boolean found = true;
	        for(int j = 0; j < smallerArray.length; ++j) {
	           if (outerArray[i+j] != smallerArray[j]) {
	               found = false;
	               break;
	           }
	        }
	        if (found) return i;
	     }
	   return -1;  
	}  

	public static int getAttackNumber() {
		return attackNumber;
	}


	public static void setAttackNumber(int attackNumber) {
		MasterImpl.attackNumber = attackNumber;
	}


	public int getActive() {
		return active;
	}


	public void setActive(int active) {
		this.active = active;
	}

	public JMSContext getContext() {
		return context;
	}

	public void setContext(JMSContext context) {
		this.context = context;
	}

	public JMSProducer getProducer() {
		return producer;
	}

	public void setProducer(JMSProducer producer) {
		this.producer = producer;
	}

	public Queue getSubAttackQueue() {
		return subAttackQueue;
	}

	public void setSubAttackQueue(Queue subAttackQueue) {
		this.subAttackQueue = subAttackQueue;
	}

	public Queue getGuessesQueue() {
		return guessesQueue;
	}

	public void setGuessesQueue(Queue guessesQueue) {
		this.guessesQueue = guessesQueue;
	}
}
