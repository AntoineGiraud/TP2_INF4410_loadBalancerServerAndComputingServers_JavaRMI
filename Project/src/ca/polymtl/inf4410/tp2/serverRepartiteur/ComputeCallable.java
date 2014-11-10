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
			retour = stub.secureCompute(task);
		} catch (RemoteException e) {
			// Si on a pas réussi à joindre le serveur, on s'assure que le resultat de la tache est bien à null et qu'elle est à l'état TO-DO
			task.setResultat(null);
			task.setToToDoState(); // Le répartiteur comprendra que le serveur n'a pas été joignable. il mettra à jour la liste des serveurs et allouera la tache à un nouveau serveur de calcul disponible.
			retour = task;
		}
		return retour;
	}
	
}
