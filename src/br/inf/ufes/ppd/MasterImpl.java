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
import com.google.gson.JsonSyntaxException;

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
	private static long timeLimit = 300000;
	private static JMSContext context;
	private static JMSProducer producer;
	private static JMSConsumer consumer;
	private static Queue subAttackQueue;
	private static Queue guessesQueue;
	
	public MasterImpl(int m) { 
		this.m = m;
    }
	
	@Override
	public Guess[] attack(String ciphertext, String knowntext) throws RemoteException, JsonSyntaxException, JMSException {
		if(!readDictionary())
			return null;
		
		int count = 0;
		long timePassed = 0;
		long beginTime = 0;
		
		int localAttackNumber = attackNumber++;
		
		int totalWords = dictionary.size();
		int vectorSize = totalWords/m;
		int remainder = totalWords%m;
		int start = 0;
		int end = vectorSize - 1;
		
		Gson gson = new Gson();
		ArrayList<Guess> guessesList = new ArrayList<Guess>();
		
		for(int i = 0; i < m; i++)
		{
			if (remainder > 0) {
				end++;
				remainder--;
			}
			
			SubAttack sub = new SubAttack(ciphertext, knowntext, start, end, localAttackNumber);

			String json = gson.toJson(sub);
			TextMessage message = context.createTextMessage(); 
			try {
				message.setText(json);
			} catch (Exception e) {
				System.out.println("Nao foi possivel criar a mensagem.");
			}
			producer.send(subAttackQueue,message);

			start = end + 1;
			end += vectorSize;
		}
		
		// recebe as respostas e os checkpoints finais do ataque
		beginTime = System.currentTimeMillis();
		timePassed = 0;
		while (count < m && timePassed < timeLimit)
		{
			Message m = consumer.receive(timeLimit);
			if (m instanceof TextMessage)
			{	
				Guess returnedMessage = gson.fromJson(((TextMessage) m).getText(), Guess.class);
				
				// mensagem eh uma guess
				if(returnedMessage.getKey() != null) {
					guessesList.add(returnedMessage);
				}
				// mensagem eh um checkpoint final
				else {
					count++;
				}
			}
			timePassed = (System.currentTimeMillis() - beginTime);
		}

		// retorna a lista de Guesses encontradas
		Guess[] finalGuesses;
		finalGuesses = new Guess[guessesList.size()];
		finalGuesses = guessesList.toArray(finalGuesses);

		return finalGuesses;
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
			System.out.println("Obtendo conexao...");
			com.sun.messaging.ConnectionFactory connectionFactory = new com.sun.messaging.ConnectionFactory();
			connectionFactory.setProperty(ConnectionConfiguration.imqAddressList,host+":7676");	
			System.out.println("Conexao obtida.");
			
			System.out.println("Obtendo filas...");
			subAttackQueue = new com.sun.messaging.Queue("SubAttacksQueue");
			guessesQueue = new com.sun.messaging.Queue("GuessesQueue");
			System.out.println("Filas obtidas.");

			context = connectionFactory.createContext();
			producer = context.createProducer();
			consumer = context.createConsumer(guessesQueue);
			
		} catch (Exception e) {
			System.out.println("Nao foi possivel configurar as filas.");
			System.exit(2);
		}

		try {
			mref = (Master) UnicastRemoteObject.exportObject(master, 0);
			
			System.out.println("Se iniciando no rmi...");
			//Registry registry = LocateRegistry.getRegistry();
			System.setProperty("java.rmi.server.hostname", host);
			Registry registry = LocateRegistry.createRegistry(1099);
			registry.rebind("mestre", mref);
			System.out.println("Mestre iniciado.");
		} catch (RemoteException e) {
			System.out.println("O mestre nao conseguiu se iniciar no rmi.");
			System.exit(1);
		}
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
