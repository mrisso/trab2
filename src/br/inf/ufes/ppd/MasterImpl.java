package br.inf.ufes.ppd;

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
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import javax.jms.*;

import com.sun.messaging.ConnectionConfiguration;
import  com.sun.messaging.ConnectionFactory;

public class MasterImpl implements Master {
	private static Master mref;
	private static MasterImpl master;
	private static List<String> dictionary = new ArrayList<String>();
	private static int atN = 0;
	private int m;
	private static long timeLimit = 20000;
	private static ConnectionFactory connectionFactory;
	private static Queue subAttackQueue;
	private static Queue guessesQueue;
	private static boolean successfulRead = false;
	
	public MasterImpl(int m) { 
		this.m = m;
		readDictionary();
    }
	
	@Override
	public Guess[] attack(byte[] ciphertext, byte[] knowntext) throws RemoteException, JsonSyntaxException, JMSException {
		if(!successfulRead)
			return null;
		
		int count = 0;
		long timePassed = 0;
		long beginTime = 0;
		
		int localAttackNumber = atN++;
		
		int totalWords = dictionary.size();
		int vectorSize = totalWords/m;
		int remainder = totalWords%m;
		int start = 0;
		int end = vectorSize - 1;
		
		Gson gson = new Gson();
		ArrayList<Guess> guessesList = new ArrayList<Guess>();
		
		JMSContext context = connectionFactory.createContext();
		JMSProducer producer = context.createProducer();
		JMSConsumer consumer = context.createConsumer(guessesQueue, "attackNumber = " + localAttackNumber);
		
		
		for(int i = 0; i < m; i++)
		{
			if (remainder > 0) {
				end++;
				remainder--;
			}
			
			SubAttack sub = new SubAttack(ciphertext, knowntext, start, end, localAttackNumber);
			
			ObjectMessage objectMessage = context.createObjectMessage();
			objectMessage.setObject(sub);

			producer.send(subAttackQueue, objectMessage);

			start = end + 1;
			end += vectorSize;
		}
		
		// recebe as respostas e os checkpoints finais do ataque
		beginTime = System.currentTimeMillis();
		timePassed = 0;
		while (count < m && timePassed < timeLimit)
		{
			Message ms = consumer.receive(timeLimit);
			if (ms instanceof TextMessage)
			{	
				Guess returnedMessage = gson.fromJson(((TextMessage) ms).getText(), Guess.class);
				
				// mensagem eh uma guess
				if(returnedMessage.getKey() != null) {
					guessesList.add(returnedMessage);
					System.out.println("<Mestre> Ataque "+localAttackNumber+": chave encontrada! key = "+returnedMessage.getKey());
				}
				// mensagem eh um checkpoint final
				else {
					count++;
					System.out.println("<Mestre> Ataque "+localAttackNumber+": "+count+"/"+m+" completo");
				}
			}
			timePassed = (System.currentTimeMillis() - beginTime);
			System.out.println("ataque "+localAttackNumber+", time passed: "+timePassed);
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
		successfulRead = true;
		return true;
	}
	
	public static void main(String[] args) {
		String host = (args.length < 1) ? "127.0.0.1" : args[0];

		int m = args.length > 1 ? Integer.parseInt(args[1]) : 4;
		master = new MasterImpl(m);
		
		try {
			System.out.println("Obtendo conexao...");
			connectionFactory = new com.sun.messaging.ConnectionFactory();
			connectionFactory.setProperty(ConnectionConfiguration.imqAddressList,host+":7676");	
			System.out.println("Conexao obtida.");
			
			System.out.println("Obtendo filas...");
			subAttackQueue = new com.sun.messaging.Queue("SubAttacksQueue");
			guessesQueue = new com.sun.messaging.Queue("GuessesQueue");
			System.out.println("Filas obtidas.");
			
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


}
