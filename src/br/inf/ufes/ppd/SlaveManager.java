package br.inf.ufes.ppd;


/**
 * SlaveManager.java
 */
import java.rmi.Remote;

public interface SlaveManager extends Remote {
    /**
     * Indica para o mestre que o escravo achou uma chave candidata.
     *
     * @param slaveKey chave que identifica o escravo
     * @param attackNumber chave que identifica o ataque
     * @param currentindex índice da chave candidata no dicionário
     * @param currentguess chute que inclui chave candidata e mensagem
     * decriptografada com a chave candidata
     * @throws java.rmi.RemoteException
     */
//    public void foundGuess(java.util.UUID slaveKey, int attackNumber, long currentindex,
//            Guess currentguess)
//            throws java.rmi.RemoteException;

    /**
     * Chamado por cada escravo a cada 10s durante ataque para indicar progresso
     * no ataque, e ao final do ataque.
     *
     * @param slaveKey chave que identifica o escravo
     * @param attackNumber chave que identifica o ataque     
     * @param currentindex índice da chave já verificada
     * @throws java.rmi.RemoteException
     */
 //   public void checkpoint(java.util.UUID slaveKey, int attackNumber, long currentindex)
 //           throws java.rmi.RemoteException;
}
