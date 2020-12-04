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

import com.sun.messaging.*;
import com.sun.messaging.Queue;

import javax.jms.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SlaveImpl implements Slave {
	static Master mestre;
	private UUID id;
	private String name;
	private long currentindex = 0;
	private ArrayList<String> dictionary;
	
	
	public SlaveImpl(UUID id, String name) {
		this.id = id;
        this.name = name;
        readDictionary();
    }
	
	public long getCurrentIndex() {
		return this.currentindex;
	}
	
	// tarefa para realizar o checkpoint 
	public TimerTask checkpointTask(int attackNumber, SlaveManager callbackinterface) {
		TimerTask tt = new TimerTask() {
			public void run() {
				try {
					callbackinterface.checkpoint(id, attackNumber, currentindex);
				} catch (Exception e) {
					System.out.println("Escravo " +name+ ": nao consegui fazer o checkpoint!");
				}
				
			}
		};
		return tt;
	}
	
	public static void lookupMaster() throws RemoteException, NotBoundException {
		//Registry registry = LocateRegistry.getRegistry();
		Registry registry = LocateRegistry.getRegistry("10.10.10.8");
		mestre = (Master) registry.lookup("mestre");
	}
	
	public static void main(String[] args) {

		UUID id = UUID.randomUUID();
		// permite que o usuario forneca um nome para o escravo pela linha de comando
		String name = (args.length > 0) ? args[0] : "Escravo" + id.toString();
		
		SlaveImpl slave = new SlaveImpl(id, name);

		String host = (args.length < 1) ? "127.0.0.1" : args[0];
		
		try {
			Logger.getLogger("").setLevel(Level.INFO);
			
			System.out.println("obtaining connection factory...");
			com.sun.messaging.ConnectionFactory connectionFactory = new com.sun.messaging.ConnectionFactory();
			connectionFactory.setProperty(ConnectionConfiguration.imqAddressList,host+":7676");	
			connectionFactory.setProperty(ConnectionConfiguration.imqConsumerFlowLimitPrefetch,"false");	
			
			System.out.println("obtained connection factory.");
			
			System.out.println("obtaining queue...");
			Queue queue = new com.sun.messaging.Queue("SubAttacksQueue");
			System.out.println("obtained queue.");			
	
			JMSContext context = connectionFactory.createContext();
			
			JMSConsumer consumer = context.createConsumer(queue); 

			while (true)
			{
				Message m = consumer.receive();
				if (m instanceof TextMessage)
				{
					System.out.print("\nreceived message: ");
					System.out.println(((TextMessage)m).getText());
				}
				
				Thread.sleep(1000);
				System.out.print("\nidle ");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

	}
		


	@Override
	public void startSubAttack(byte[] ciphertext, byte[] knowntext, long initialwordindex, long finalwordindex,
			int attackNumber, SlaveManager callbackinterface) throws RemoteException {

		long i = initialwordindex;
		currentindex = initialwordindex;
		
		// agenda a tarefa de realizar o checkpoint a cada 10s
		Timer t = new Timer();
		t.schedule(checkpointTask(attackNumber, callbackinterface), 0, 10000);
		
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
				Guess answer = new Guess();
				answer.setKey(dictionary.get((int)i));
				answer.setMessage(decrypted);
				callbackinterface.foundGuess(this.id, attackNumber, i, answer);
			}
			
		}
		
		// realiza o ultimo checkpoint e interrompe a tarefa agendada
		callbackinterface.checkpoint(this.id, attackNumber, currentindex);
		t.cancel();
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
	
}




