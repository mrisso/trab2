package br.inf.ufes.ppd;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import com.google.gson.Gson;
import com.sun.messaging.*;
import com.sun.messaging.Queue;
import com.sun.messaging.ConnectionConfiguration;

import javax.jms.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SlaveImpl implements Slave {
	static Master mestre;
	private UUID id;
	private String name;
	private long currentindex = 0;
	private ArrayList<String> dictionary;
	private JMSContext context;
	private JMSProducer producer;
	private JMSConsumer consumer;
	private Queue subAttackQueue;
	private Queue guessesQueue;
	
	
	public SlaveImpl(UUID id, String name) {
		this.id = id;
        this.name = name;
        readDictionary();
    }
	
	public long getCurrentIndex() {
		return this.currentindex;
	}
	
	public void sendGuess(Guess answer) {
		Gson gson = new Gson();

		String json = gson.toJson(answer);
		TextMessage message = context.createTextMessage(); 
		
		try {
			message.setText(json);
		} catch (Exception e) {
			System.out.println("Nao foi possivel criar a mensagem.");
		}
		producer.send(guessesQueue, message);
	}
	
	public void sendFinalCheckpoint(int attackNumber) {
		
		Guess answer = new Guess(attackNumber);
		answer.setKey(null);
		answer.setKey(null);
		
		Gson gson = new Gson();

		String json = gson.toJson(answer);
		TextMessage message = context.createTextMessage(); 
		
		try {
			message.setText(json);
		} catch (Exception e) {
			System.out.println("Nao foi possivel criar a mensagem.");
		}
		producer.send(guessesQueue, message);
	}
	
	@Override
	public void startSubAttack(byte[] ciphertext, byte[] knowntext, int initialwordindex, int finalwordindex,
			int attackNumber) throws RemoteException {

		long i = initialwordindex;
		currentindex = initialwordindex;
		
		// Andar pelas palavras do dicionario tentando desencriptar a mensagem
		for(i = initialwordindex; i <= finalwordindex; i++)
		{
			currentindex = i;
			byte[] decrypted = null;
			try {
				byte[] key = dictionary.get((int)i).getBytes();
				SecretKeySpec keySpec = new SecretKeySpec(key, "Blowfish");
				

				Cipher cipher = Cipher.getInstance("Blowfish");
				cipher.init(Cipher.DECRYPT_MODE, keySpec);

				decrypted = cipher.doFinal(ciphertext);
			} catch (javax.crypto.BadPaddingException e) {
				// essa excecao e jogada quando a senha esta incorreta
				// porem nao quer dizer que a senha esta correta se nao jogar essa excecao
				continue;

			} catch (Exception e) {
				e.printStackTrace();
			}
			
			// Caso a palavra conhecida seja encontrada
			if(indexOf(decrypted, knowntext) != -1) {
				Guess answer = new Guess(attackNumber);
				answer.setKey(dictionary.get((int)i));
				answer.setMessage(decrypted);
				
				sendGuess(answer);
			}
			
		}
		
		sendFinalCheckpoint(attackNumber);
	}
	
	public static void main(String[] args) {

		UUID id = UUID.randomUUID();
		// permite que o usuario forneca um nome para o escravo pela linha de comando
		String name = (args.length > 0) ? args[0] : "Escravo" + id.toString();
		
		SlaveImpl slave = new SlaveImpl(id, name);

		String host = (args.length < 1) ? "127.0.0.1" : args[0];
		
		try {
			Logger.getLogger("").setLevel(Level.INFO);
			
			System.out.println("Obtendo conexao...");
			com.sun.messaging.ConnectionFactory connectionFactory = new com.sun.messaging.ConnectionFactory();
			connectionFactory.setProperty(ConnectionConfiguration.imqAddressList,host+":7676");	
			connectionFactory.setProperty(ConnectionConfiguration.imqConsumerFlowLimitPrefetch,"false");	
			System.out.println("Conexao obtida.");
			
			System.out.println("Obtendo filas...");
			slave.setGuessesQueue(new com.sun.messaging.Queue("GuessesQueue"));
			slave.setSubAttackQueue(new com.sun.messaging.Queue("SubAttacksQueue"));
			System.out.println("Filas obtidas.");	
	
			slave.setContext(connectionFactory.createContext());
			slave.setConsumer(slave.getContext().createConsumer(slave.getSubAttackQueue()));
			slave.setProducer(slave.getContext().createProducer());
			
			Gson gson = new Gson();
			 
			while (true)
			{
				Message m = slave.getConsumer().receive();
				if (m instanceof TextMessage)
				{	
					System.out.print("\nreceived subattack command.");
					SubAttack subattack = gson.fromJson(((TextMessage) m).getText(), SubAttack.class);
					
					// ta errado
					System.out.println(subattack.getKnowntext().getBytes());
					
					slave.startSubAttack(subattack.getCiphertext().getBytes(), 
											subattack.getKnowntext().getBytes(), 
											subattack.getInitialindex(), 
											subattack.getFinalindex(), 
											subattack.getAttacknumber());
				}
				System.out.print("\nidle ");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	public void readDictionary() {
		try {
			Path dictionaryPath =  Paths.get("dictionary.txt"); // dicionario deve estar na pasta tmp de cada pc
			dictionary = (ArrayList<String>) Files.readAllLines(dictionaryPath, StandardCharsets.UTF_8);
		} catch (Exception e) {
			System.out.println("Erro na leitura do arquivo :(");
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

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
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

	public JMSConsumer getConsumer() {
		return consumer;
	}

	public void setConsumer(JMSConsumer consumer) {
		this.consumer = consumer;
	}
	
}




