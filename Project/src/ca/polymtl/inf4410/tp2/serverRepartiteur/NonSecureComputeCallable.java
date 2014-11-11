package ca.polymtl.inf4410.tp2.serverRepartiteur;

import java.rmi.RemoteException;
import java.util.concurrent.Callable;

import ca.polymtl.inf4410.tp2.shared.ServerCalculInterface;
import ca.polymtl.inf4410.tp2.shared.Tache;

/**
 * Objet Callable qui sera utilisé par la classe serverRepartiteur pour gérer l'envoie des tâches et la réception de celles-ci de manière assychrone.
 * @author Antoine
 *
 */
public class NonSecureComputeCallable implements Callable<Tache> {

	private ServerCalculInterface stub;
	private Tache task;

	/**
	 * On passe au constructeur le stub du serveur à contacter et la tâche que l'on souhaite faire éxécuter par le server de calculs.
	 * @param stub ServerCalculInterface
	 * @param task Tache
	 */
	public NonSecureComputeCallable(ServerCalculInterface stub, Tache task) {
		this.stub = stub;
		this.task = task;
	}

	@Override
	public Tache call() {
		Tache retour;
		try {
			retour = stub.nonSecureCompute(task);
		} catch (RemoteException e) {
			// Si on a pas réussi à joindre le serveur, on s'assure que le resultat de la tache est bien à null et qu'elle est à l'état TO-DO
			task.setResultat(null);
			task.setToToDoState(); // Le répartiteur comprendra que le serveur n'a pas été joignable. il mettra à jour la liste des serveurs et allouera la tache à un nouveau serveur de calcul disponible.
			retour = task;
		}
		return retour;
	}
	
}
