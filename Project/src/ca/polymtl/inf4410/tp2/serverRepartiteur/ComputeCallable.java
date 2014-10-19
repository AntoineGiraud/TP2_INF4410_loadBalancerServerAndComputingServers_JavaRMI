package ca.polymtl.inf4410.tp2.serverRepartiteur;

import java.rmi.RemoteException;
import java.util.concurrent.Callable;

import ca.polymtl.inf4410.tp2.shared.ServerCalculInterface;
import ca.polymtl.inf4410.tp2.shared.Tache;

public class ComputeCallable implements Callable<Tache> {

	private ServerCalculInterface stub;
	private Tache task;

	public ComputeCallable(ServerCalculInterface stub, Tache task) {
		this.stub = stub;
		this.task = task;
	}
	
	@Override
	public Tache call() {
		Tache retour;
		try {
			retour = stub.compute(task);
		} catch (RemoteException e) {
			task.setResultat(null);
			return task;
		}
		System.out.println(retour);
		return retour;
	}
	
}
