package br.inf.ufes.ppd;

import java.rmi.RemoteException;
import java.util.UUID;

public class MasterCheck implements Runnable{
	private SlaveInfo info;
	private MasterImpl master;
	private Thread attack;
	
	public MasterCheck(SlaveInfo i, Master m, Thread a) {
		this.info = i;
		this.master = (MasterImpl) m;
		this.attack = a;
	}

	@Override 
	public void run() {
		// realiza a checagem de tempo ate ser interrompida no fim do ataque
		while(!Thread.interrupted()) {

			// a variavel 'active' de master carrega a contribuicao de cada ataque. Caso seja 0, nao ha nenhum ataque ativo no momento
			if(master.getActive() > 0) {
				long currentTime = System.nanoTime()/1000000000;
				long lastCheckIn = info.getLastCheckIn()/1000000000;

				// verifica se o tempo do ultimo checkin eh maior que 20s e se o escravo ainda esta executando subataques
				if((currentTime - lastCheckIn) > 20 && info.getActive() > 0) {
					try {
						info.clearActive(); // torna o escravo inativo. ele nao sera mais checado
						master.removeSlave(info.getId());
						break;
					} catch (RemoteException e) {
						System.out.println("Erro ao tentar remover escravo de nome " + info.getName());
						e.printStackTrace();
					}
				}
			}
			// realiza a checagem a cada 1s
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				
			}				
		}
		
	}





	public Thread getAttack() {
		return attack;
	}

	public void setAttack(Thread attack) {
		this.attack = attack;
	}

	public SlaveInfo getInfo() {
		return info;
	}

	public void setInfo(SlaveInfo info) {
		this.info = info;
	}

	
	
}