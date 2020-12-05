package br.inf.ufes.ppd;

/**
 * Attacker.java
 */


import java.rmi.Remote;
import java.rmi.RemoteException;

import javax.jms.JMSException;

import com.google.gson.JsonSyntaxException;

public interface Attacker extends Remote {

	/**
	 * Operação oferecida pelo mestre para iniciar um ataque.
	 * @param ciphertext mensagem critografada
	 * @param knowntext trecho conhecido da mensagem decriptografada
	 * @return vetor de chutes: chaves candidatas e mensagem
	 * decriptografada com chaves candidatas
	 * @throws JMSException 
	 * @throws JsonSyntaxException 
	 */
	public Guess[] attack(String ciphertext,
			String knowntext) throws RemoteException, JsonSyntaxException, JMSException ;
}