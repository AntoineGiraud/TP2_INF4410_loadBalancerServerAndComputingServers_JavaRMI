package ca.polymtl.inf4410.tp2.serverRepartiteur;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import ca.polymtl.inf4410.tp2.shared.Tache;

/**
 * @author Antoine Giraud #1761581
 *
 */
public class ServerRepartiteur {
	private enum functions {listServers, compute, unbind};
	public static void main(String[] args) {
		ServerRepartiteur serverRepartiteur = new ServerRepartiteur();
		if (args.length > 0) {
			functions f = functions.valueOf(args[0]);
			switch (f) {
				case listServers: serverRepartiteur.listServers(); break;
				case compute:     serverRepartiteur.compute(args[1]); break;
				case unbind:      serverRepartiteur.unbind(args[1]); break;
				default:          System.out.println("Mauvaise commande");
			}
		} else System.out.println("entrer une command comme argument");
	}

	private ServerCalculStubsManager stubsManager;

	/**
	 * Contructeur de la classe qui initialise diférentes choses :
	 * <ul><li>Le SecurityManager de RMI</li><li>La liste des serveurs disponibles</li><li>Lance le thread qui va vérifier que l'on a bien tous les serveurs de connecté toutes les 2 secondes.</li>
	 */
	public ServerRepartiteur() {
		this.stubsManager = new ServerCalculStubsManager();
	}
	/**
	 * Commande disponible pour l'utilisateur pour retirer manuelement un serveur enregistré dans le RMI.<br>
	 * Cette fonction a été utilie au tout début du projet.
	 * @param serverNameToUnbind String, nom du serveur à retirer du RMI.
	 */
	private void unbind(String serverNameToUnbind) {
		stubsManager.unbind(serverNameToUnbind);
	}
	/**
	 * Affiche dans la console la liste des serveur disponibles dans le RMI.
	 */
	private void listServers() {
		stubsManager.listServers();
	}

	/**
	 * Commande disponible pour l'utilisateur qui va lui permettre d'effectuer le calcul d'un fichier d'opérations qu'il passe en paramètre.
	 * @param path
	 */
	private void compute(String path) {
		// On vérifie que le fichier passé en paramètre existe sinon on arrête le programme.
		if (!(new File(path).exists())) { System.out.println("Erreur, le fichier n'existe pas"); System.exit(0); }

		Travail work = new Travail(path); // On a découpé notre projet en taches
		work.show();

		// On va effectuer les tâches...
		stubsManager.startServersMajWatch();
		
		ExecutorService executorService = Executors.newFixedThreadPool(10);
		ArrayList<Future<Tache>> FutureRetoursDesServeurs = new ArrayList<Future<Tache>>();
		
		if (stubsManager.hasServers()) {
			// On va envoyer nos Taches !
			for (int i = 0; i < work.Taches.size(); i++) {
				String randomServerName = stubsManager.getRandomServerName();
				Tache task = work.Taches.get(i);
				task.setToInProgressState();
				task.setAssignedTo(randomServerName);
				work.Taches.set(i,task);

				FutureRetoursDesServeurs.add(executorService.submit(new ComputeCallable(stubsManager.get(randomServerName), task)));
			}

			while (!FutureRetoursDesServeurs.isEmpty()) {
				ArrayList<Future<Tache>> FutureRetoursDesServeurs2 = new ArrayList<Future<Tache>>();
				FutureRetoursDesServeurs2.addAll(FutureRetoursDesServeurs);
				for (Future<Tache> future : FutureRetoursDesServeurs2) {
					if (future.isDone()) {
						FutureRetoursDesServeurs.remove(future);
						try {
							Tache futureTask = future.get();

							if (futureTask.getResultat() == null) {
								if (futureTask.getState().equals("Refused"))
									System.out.println("Tâche #"+futureTask.getID()+" refusée par "+futureTask.getAssignedTo());
								else if (futureTask.getState().equals("NotDelivered"))
									System.out.println("Tâche #"+futureTask.getID()+" n'a pas atteint "+futureTask.getAssignedTo());
								else
									System.out.println("Tâche #"+futureTask.getID()+" non exécutée pour raison inconnue.");
								
								stubsManager.refreshServerList();
								if (!stubsManager.hasServers()) { // On n'a plus de serveurs connectés !!
									System.out.println("Aucun serveur disponible dans le RMI. Veuillez reconnecter un serveur de calcul au RMI pour continuer.");
									while (!stubsManager.hasServers()) {
										Thread.sleep(1500);
										stubsManager.refreshServerList();
									}
								}
								String randomServerName = stubsManager.getRandomServerName();
								futureTask.setToInProgressState();
								futureTask.setAssignedTo(randomServerName);
								FutureRetoursDesServeurs.add(executorService
										.submit(new ComputeCallable(
												stubsManager.get(randomServerName),
												futureTask)));
							} else {
								System.out.println(futureTask.getAssignedTo()
										+ " réponds pour la tâche #"+futureTask.getID()+ " : " 
										+ futureTask.getResultat());
								work.submitCompletedTask(futureTask);
							}
						} catch (InterruptedException e) { e.printStackTrace(); }
						  catch (ExecutionException e) { e.printStackTrace(); }
					}

				}
			}
			System.out.println("Résultat calculé : " + work.computedResult);
			System.out.println("Résultat attendu : " + work.expectedResult);
		} else {
			System.out.println("Aucun serveur disponible dans le RMI. Veuillez lancer vos serveurs de calcul et recommencer.");
			System.exit(0);
		}
		executorService.shutdown();
		stubsManager.interruptServersMajWatch();
	}
}
