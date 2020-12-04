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
	
	public MasterImpl() { 
    }
	

	@Override
	public void addSlave(Slave s, String slaveName, UUID slavekey) throws RemoteException {
		// escravo ja esta registrado
		if(slavesInfo.containsKey(slavekey)) {
			System.out.println("Mestre: Atualizei o registro do escravo de nome " + slaveName + "!");
			SlaveInfo si = slavesInfo.get(slavekey);
			synchronized(si) {
				si.setLastCheckIn(System.nanoTime());
			}
		// escravo precisa se registrar
		} else {
			SlaveInfo si = new SlaveInfo(s, slaveName, slavekey);
			slavesInfo.put(slavekey, si);
			System.out.println("Mestre: Registrei o escravo de nome " + si.getName() + "!");	
		}
		
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
	public Guess[] attack(byte[] ciphertext, byte[] knowntext) throws RemoteException {
		// le o dicionario para dividir seu conteudo entre os escravos
		if (!readDictionary()) return null;
		attackNumber++;
		AttackInfo thisAttack = new AttackInfo(attackNumber);
		ArrayList<Guess> attackGuesses = new ArrayList<Guess>();
		guessList.put(thisAttack.getAttackNumber(), attackGuesses);
		
		// adiciona 1 ao count de ataques (contribuicao deste ataque)
		active++;
		
		int totalSlaves = slavesInfo.size();
		int totalWords = dictionary.size();
		int vectorSize = totalWords/totalSlaves;
		int remainder = totalWords%totalSlaves;
		int start = 0;
		int end = vectorSize - 1;
		
		for(Entry<UUID, SlaveInfo> si : slavesInfo.entrySet()) {
			// caso a divisao de trabalho nao seja exata, adiciona +1 no vetor de cada escravo ate que o resto seja 0
			if (remainder > 0) {
				end++;
				remainder--;
			}
			
			SlaveInfo slaveInfo = si.getValue();
			slaveInfo.setLastCheckIn(System.nanoTime());
			UUID subattackId = UUID.randomUUID();
			
			// cria o ataque correspondente a este escravo
			Thread subattack = new SlaveAttack(subattackId, slaveInfo.getSref(), slaveInfo, ciphertext, knowntext, start, end, thisAttack.getAttackNumber(), mref);
			slaveInfo.getAttacks().add(subattackId);

			// atualiza parametros
			start = end + 1;
			end += vectorSize;
			
			// inicia a thread de ataque do escravo 
			subattack.start();
			subattacks.put(subattackId, subattack);
			slaveInfo.setActive(+1);
						
			// cria e inicia a thread que checa o tempo entre check-ins do escravo
			Runnable mastercheck = new MasterCheck(slaveInfo, this, subattack);
			Thread checkThread = new Thread(mastercheck);
			checkThread.start();
			checks.put(slaveInfo.getId(), checkThread);
		}
		
		// apos o inicio de todas as threads de subataque, gerenciamos o fim do ataque
		boolean complete = true;
		List<UUID> subattacksToRemove = new ArrayList<UUID>();
		
		// join das threads de subataque
		while(true) {
			for(Entry<UUID, Thread> sa : subattacks.entrySet()) {
				Thread subattack = sa.getValue();
				SlaveAttack at = (SlaveAttack) sa.getValue();
				
				if(at.getAttackNumber() == thisAttack.getAttackNumber()) {
					try {
							subattack.join();
					} catch(InterruptedException ie) {
						System.out.println("Ataque do escravo "+ at.getSlaveInfo().getId() +" foi interrompido e nao conseguiu dar join!");
					}
					// verifica se a thread finalizada foi completada com sucesso
					if(at.getLastCheckedIndex() < at.getFinalindex()) {
						complete = false;
					}else {
						subattacksToRemove.add(at.getMyId());
					}
				}
			}
			// se o ataque acabou mas alguma parte do vetor nao foi percorrida, significa que algum escravo morreu mas o mestre ainda nao viu
			// ataque espera 20s para dar tempo do mestre perceber e realocar o vetor.
			if(!complete)
				try {
					System.out.println("Algum escravo nao terminou o ataque. Aguardando redirecionamento...");
					Thread.sleep(22000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			
			// Apos os joins, verifica se nesse meio tempo surgiram novas threads neste ataque (escravos que falharam). 
			//Se sim, ainda ha threads deste ataque precisando de join e a lista eh percorrida novamente
			if(!thereAreThreadsRunning(thisAttack.getAttackNumber())) {
				break;
			}
		}
		
		// apos as threads de ataque terminarem, mestre para de checar o estado dos escravos que estao inativos
		for(Entry<UUID, Thread> sa : checks.entrySet()) {
			Thread checkin = sa.getValue();
			if(slavesInfo.get(sa.getKey()).getActive() == 0) checkin.interrupt();
		}
		
		// armazena as respostas em um vetor de Guess
		Guess[] finalGuesses;
		ArrayList<Guess> finalList = guessList.get(thisAttack.getAttackNumber());
		synchronized(finalList) {
			finalGuesses = new Guess[finalList.size()];
			finalGuesses = finalList.toArray(finalGuesses);
		}
		
		// retira a contribuicao deste ataque do count de ataques
		active--;
		
		
		//remove os ataques finalizados da lista de ataque dos escravos
		List<UUID> idsToRemove = new ArrayList<UUID>();
		
		for(Entry<UUID, SlaveInfo> slaveInfo : slavesInfo.entrySet()) {
			SlaveInfo s = slaveInfo.getValue();
			List<UUID> attacksId = s.getAttacks();
			
			synchronized(attacksId) {
				for(UUID id : attacksId) {
					SlaveAttack sl = (SlaveAttack) subattacks.get(id);
					if(sl.getAttackNumber() == thisAttack.getAttackNumber())
						idsToRemove.add(id);
				}
				
				for(UUID id : idsToRemove) {
					attacksId.remove(id);
					subattacks.remove(id);
				}
			}
			idsToRemove.clear();
		}
		
		return finalGuesses;
	}

	public Guess[] serialAttack(byte[] ciphertext, byte[] knowntext) {
		if (!readDictionary()) return null;
		byte[] decrypted = null;
		List<Guess> guessList = new ArrayList<Guess>();
		
		for(int i = 0; i < dictionary.size(); i++)
		{
			try {

				byte[] key = dictionary.get(i).getBytes();
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
				answer.setKey(dictionary.get(i));
				answer.setMessage(decrypted);
				guessList.add(answer);
			}
		}
		
		Guess[] guessArray = new Guess[guessList.size()];
		guessArray = guessList.toArray(guessArray);

		return guessArray;
	}
	
	@Override
	public void removeSlave(UUID slaveKey) throws RemoteException {
		try {
			SlaveInfo si = slavesInfo.get(slaveKey);
			
			synchronized(si) {
				// remove o escravo da lista e redistribui o trabalho
				repairAttack(slaveKey, si.getAttacks());
			}
			
			System.out.println("Mestre: Removi o escravo de id " + slaveKey + "!");
		} catch (NullPointerException ne) {
		}
		
	}
	
	// funcao chamada quando algum escravo morre. redistribui o trabalho
	public void repairAttack(UUID slaveKey, List<UUID> attacks) throws RemoteException {
		System.out.println("Algum escravo morreu! tentando redistribuir o ataque...");
		slavesInfo.remove(slaveKey);
		checks.remove(slaveKey);
		
		// itera sobre a lista de slavesInfo apenas para pegar o objeto correspondente ao algum escravo. Havera apenas uma iteracao
		for(Entry<UUID, SlaveInfo> si : slavesInfo.entrySet()) {
			
			SlaveInfo substitute = si.getValue();
			
			// itera sob cada ataque do escravo que falhou, realocando seus ataques para o substituto
			synchronized(attacks) {
				for(UUID id : attacks) {
					
					// cria nova thread de ataque para o escravo substituto com os dados do ataque que falhou
					SlaveAttack failedAttack = (SlaveAttack) subattacks.get(id);
					
					if(failedAttack.getLastCheckedIndex() < failedAttack.getFinalindex()) { // ignora os ataques finalizados do escravo
						UUID newAttackId = UUID.randomUUID();
						Thread subattack = new SlaveAttack(newAttackId, substitute.getSref(), substitute, failedAttack.getCiphertext(), failedAttack.getKnowntext(), failedAttack.getLastCheckedIndex()+1, failedAttack.getFinalindex(), failedAttack.getAttackNumber(), mref);

						// adiciona novo ataque a lista de ataques do substituto
						substitute.getAttacks().add(newAttackId);
						subattack.start();
						substitute.setActive(+1);
						
						// remove ataque falho da lista de ataques e adiciona o novo ataque
						subattacks.remove(id);
						subattacks.put(newAttackId, subattack);
					}
				}
			}
			// apos alocar os ataques falhos a um substituto (o primeiro escravo que o loop encontrar), saimos do for
			break;
		}
	}
	
	public boolean readDictionary() {
		try {
			Path dictionaryPath =  Paths.get("/tmp/dictionary.txt"); // dicionario deve estar na pasta tmp de cada pc
			dictionary = (ArrayList<String>) Files.readAllLines(dictionaryPath, StandardCharsets.UTF_8);
		} catch (Exception e) {
			System.out.println("Erro na leitura do arquivo :(");
			return false;
		}
		return true;
	}
	
	public static void main(String[] args) {
		master = new MasterImpl();
		String filename = null;
		String knowntext = null;
		byte[] byteArrayMessage = null;
		Guess[] guesses = null;

		// implementacao serial
		if(args.length > 0)
		{
			try {
				filename = args[0];
				knowntext = args[1];
			} catch (Exception e)
			{
				System.out.println("Para a execucao serial, especifique o nome do arquivo e a palavra conhecida.");
				System.exit(2);
			}
			
			// lendo o arquivo
			File arquivoCriptografado = new File(filename);
		
			// caso o arquivo exista, leia
			if(arquivoCriptografado.exists())
			{
				try {
					byteArrayMessage = Files.readAllBytes(arquivoCriptografado.toPath());
				} catch (IOException e) { 
					e.printStackTrace();
				}
			}
			
			else
			{
				System.out.println("Arquivo nao existe!");
				System.exit(3);
			}
			
			long startTime = System.nanoTime();
			//Ataque Serial
			guesses = master.serialAttack(byteArrayMessage, knowntext.getBytes());
			long endTime = System.nanoTime();

			System.out.println("Tempo de Execucao: " + ((endTime - startTime)/1000000) + "ms");
			
			if(guesses == null) {
				System.out.println("O ataque nao pode ser realizado pois houve um erro na leitura do arquivo :(");
			}
			else if(guesses.length > 0) {
				System.out.println("Chaves em potencial encontradas! salvando mensagens descriptografadas...");
				
				// salva cada mensagem em um arquivo de nome igual ao nome da chave
				FileOutputStream out = null;
				
				try {
					for (int i = 0; i < guesses.length; i++) {
						System.out.println("Criando arquivo "+guesses[i].getKey()+".msg");
						out = new FileOutputStream(guesses[i].getKey() + ".msg");
						out.write(guesses[i].getMessage());
						out.close();
					}
				} catch (Exception e) {
					System.out.println("Nao foi possivel salvar resultados nos arquivos.");
				}

			}else {
				System.out.println("Nenhuma chave em potencial encontrada...");
			}
		}
		// implementacao paralela (com escravos)
		else
		{
			try {
				mref = (Master) UnicastRemoteObject.exportObject(master, 0);
			
				//Registry registry = LocateRegistry.getRegistry();
				System.setProperty("java.rmi.server.hostname", "10.10.10.8");
				Registry registry = LocateRegistry.createRegistry(1099);
				registry.rebind("mestre", mref);
				System.out.println("Mestre ligado!");
			} catch (RemoteException e) {
				System.out.println("O mestre nao conseguiu se iniciar :(");
				//e.printStackTrace();
				System.exit(1);
			}
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


	
}
