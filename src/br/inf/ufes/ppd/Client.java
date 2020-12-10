package br.inf.ufes.ppd;

import java.io.BufferedWriter;
import java.io.File;
import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Random;
import java.util.Scanner;
import java.io.Serializable;


public class Client {

	private static void saveFile(String filename, byte[] data) throws IOException
	{
		FileOutputStream out = new FileOutputStream(filename);
	    out.write(data);
	    out.close();
	}

	private static byte[] generateRandomByteArray(int size, byte[] knownBytes)
	{
		byte[] array = new byte[size];
		
		// Gera aleatoriamente uma sequência de bytes do tamanho do array
		new Random().nextBytes(array);
		
		// Coloca os knownBytes no array
		for(int i = 0; i < knownBytes.length; i++)
			array[i] = knownBytes[i];
		
		return array;
	}
	
	public static String getRandomKey(File f) throws FileNotFoundException
	{
	     String result = null;
	     Random rand = new Random();
	     int n = 0;
	     Scanner sc = new Scanner(f);
	     while(sc.hasNext())
	     {
	        ++n;
	        String line = sc.nextLine();
	        if(rand.nextInt(n) == 0)
	           result = line;         
	     }
	     
	     sc.close();
	     
	     return result;      
	 }

	public static void main(String[] args) {
		String filename = null;
		String knowntext = null;
		byte[] vetorCriptografado = null;
		byte[] byteArrayMessage = null;
		byte[] myknowntext = null;
		long startTime = 0;
		long endTime = 0;
		
		Random randomNumber = new Random();
		
		// recebendo argumentos da linha de comando
		try {
			filename = args[0];
			knowntext = args[1];
		} catch (ArrayIndexOutOfBoundsException ex) {
			System.out.println("Erro! eh preciso fornecer o arquivo com a mensagem criptografada e a palavra conhecida.");
		}
		
		// lendo o arquivo
		File arquivoCriptografado = new File(filename);
		
		// caso o arquivo exista, leia
		if(arquivoCriptografado.exists())
		{
			try {
				vetorCriptografado = Files.readAllBytes(arquivoCriptografado.toPath());
			} catch (IOException e) { 
				e.printStackTrace();
			}
		}
		
		// caso o arquivo nao exista, criar vetor de bytes
		else
		{
			// pegar o tamanho do argumento, ou criar um tamanho aleatorio
			int tam = (args.length > 2) ? Integer.parseInt(args[2]) : randomNumber.nextInt(99001) + 1000;
			
			// gerar o vetor aleatorio
			byteArrayMessage = generateRandomByteArray(tam, knowntext.getBytes());
			
			// pegar chave aleatoria do dicionario
			String key = null;
			try {
				key = getRandomKey(new File("dictionary.txt"));
			} catch (FileNotFoundException e) {
				System.out.println("Nao encontrado arquivo de dicionário para criar arquivo criptografado.");
			}
			
			// encriptar mensagem
			try {
				byte[] byteKey = key.getBytes();
			
				SecretKeySpec keySpec = new SecretKeySpec(byteKey, "Blowfish");

				Cipher cipher = Cipher.getInstance("Blowfish");
				cipher.init(Cipher.ENCRYPT_MODE, keySpec);

				vetorCriptografado = cipher.doFinal(byteArrayMessage);
			
			} catch (Exception e) {
				e.printStackTrace();
			}

			// criando arquivo criptografado.
			try {
				saveFile(filename, vetorCriptografado);
			} catch (IOException e) {
				System.out.println("Nao foi possivel criar arquivo criptografado.");
			}
		}
		
		myknowntext = knowntext.getBytes();

		//registrando e pedindo o ataque
		try {
			Registry registry = LocateRegistry.getRegistry();
			//Registry registry = LocateRegistry.getRegistry("10.10.10.8");
			Master mestre = (Master) registry.lookup("mestre");
			
			System.out.println("Cliente: Achei o mestre!");
			
			startTime = System.nanoTime();
			Guess[] respostas = mestre.attack(vetorCriptografado, knowntext.getBytes());
			endTime = System.nanoTime();
			
			System.out.println("Tempo de Execucao: " + ((endTime - startTime)/1000000) + "ms");
				
			if(respostas == null) {
				System.out.println("O ataque nao pode ser realizado pois houve um erro na leitura do arquivo :(");
			}
			else if(respostas.length > 0) {
				System.out.println("Chaves em potencial encontradas! salvando mensagens descriptografadas...");
				
				// salva cada mensagem em um arquivo de nome igual ao nome da chave
				FileOutputStream out = null;
				
				 for (int i = 0; i < respostas.length; i++) {
					 System.out.println("cliente "+knowntext+" criando arquivo "+respostas[i].getKey()+".msg");
					 out = new FileOutputStream(respostas[i].getKey() + ".msg");
					 out.write(respostas[i].getMessage());
					 out.close();
				 }
			}else {
				System.out.println("Nenhuma chave em potencial encontrada...");
			}
			
		} catch (Exception e) {
			System.err.println("Houve um problema inesperado! :(");
			e.printStackTrace();
		}
		
	}
	
}
