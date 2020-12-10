package br.inf.ufes.ppd;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.util.ArrayList;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import com.google.gson.Gson;
import com.sun.messaging.Queue;
import com.sun.messaging.ConnectionConfiguration;

import javax.jms.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SlaveImpl implements Slave {
	private ArrayList<String> dictionary;
	private JMSContext context;
	private JMSProducer producer;
	private JMSConsumer consumer;
	private Queue subAttackQueue;
	private Queue guessesQueue;
	
	
	public SlaveImpl() {
        readDictionary();
    }

	public void sendGuess(Guess answer, int attackNumber) {
		Gson gson = new Gson();

		String json = gson.toJson(answer);
		TextMessage message = context.createTextMessage(); 
		
		try {
			message.setText(json);
			message.setIntProperty("attackNumber", attackNumber);
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
			message.setIntProperty("attackNumber", attackNumber);
		} catch (Exception e) {
			System.out.println("Nao foi possivel criar a mensagem.");
		}
		producer.send(guessesQueue, message);
	}
	
	@Override
	public void startSubAttack(byte[] ciphertext, byte[] knowntext, int initialwordindex, int finalwordindex,
			int attackNumber) throws RemoteException {

		long i = initialwordindex;
		
		// Andar pelas palavras do dicionario tentando desencriptar a mensagem
		for(i = initialwordindex; i <= finalwordindex; i++)
		{
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
				
				sendGuess(answer, attackNumber);
			}
			
		}
		System.out.println("<Escravo> Ataque "+attackNumber+": Subataque finalizado.");
		sendFinalCheckpoint(attackNumber);
	}
	
	public static void main(String[] args) {
		
		SlaveImpl slave = new SlaveImpl();

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
			
			while (true)
			{
				Message m = slave.getConsumer().receive();
				if (m instanceof ObjectMessage)
				{	
					SubAttack subattack = (SubAttack) ((ObjectMessage) m).getObject();
					
					System.out.println("<Escravo> Ataque "+subattack.getAttacknumber()+": Comando de subataque recebido.");
					slave.startSubAttack(subattack.getCiphertext(), 
											subattack.getKnowntext(), 
											subattack.getInitialindex(), 
											subattack.getFinalindex(), 
											subattack.getAttacknumber());
				}
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




