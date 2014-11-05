package ca.polymtl.inf4410.tp2.serverRepartiteur;

import java.io.File;
import java.rmi.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import ca.polymtl.inf4410.tp2.shared.Operation;
import ca.polymtl.inf4410.tp2.shared.ServerCalculInterface;
import ca.polymtl.inf4410.tp2.shared.Tache;
import ca.polymtl.inf4410.tp2.shared.Travail;

/**
 * <p>
 * Le serverRepartiteur exposera les commandes list et get pour consulter les
 * fichiers du serveur.<br>
 * La commande create permettra de créer un nouveau fichier.<br>
 * Pour modifier un fichier, l'utilisateur devra d'abord le verrouiller à l'aide
 * de la commande lock.<br>
 * Il appliquera ensuite ses modifications au fichier local et les publiera sur
 * le serveur à l'aide la commande push.<br>
 * Notons qu'un seul serverRepartiteur peut verrouiller un fichier donné à la
 * fois.<br>
 * Aussi, la commande lock téléchargera toujours la dernière version du fichier
 * afin d'éviter que l'utilisateur n'applique ses modifications à une version
 * périmée.<br>
 * </p>
 * 
 * @author Antoine Giraud #1761581
 *
 */
public class ServerRepartiteur {
	public static void main(String[] args) {
		ServerRepartiteur serverRepartiteur = new ServerRepartiteur();
		if (args.length > 0) {
			functions f = functions.valueOf(args[0]);
			switch (f) {
			case listServers:
				serverRepartiteur.listServers();
				break;
			case compute:
				serverRepartiteur.compute(args[1]);
				break;
			case unbind:
				serverRepartiteur.unbind(args[1]);
				break;
			default:
				System.out.println("Mauvaise commande");
			}
		} else
			System.out.println("entrer une command comme argument");
	}

	private Hashtable<String, ServerCalculInterface> ServerDispos;

	public synchronized Hashtable<String, ServerCalculInterface> getServerDispos() {
		return ServerDispos;
	}

	private enum functions {
		listServers, compute, unbind
	};

	private Random rand = new Random();
	private Thread updateServerListThread;

	public ServerRepartiteur() {
		super();
		if (System.getSecurityManager() == null) {
			System.setSecurityManager(new SecurityManager());
		}

		this.ServerDispos = new Hashtable<String, ServerCalculInterface>();
		refreshServerList();

		this.updateServerListThread = new Thread() {
			public void run() {
				while (!this.isInterrupted()) {
					try {
						sleep(1000);
					} catch (InterruptedException e) {
						this.interrupt();
					}
					refreshServerList();
				}
			}
		};
		
	}

	private void compute(String path) {
		
		File file = new File(path);
		if (!file.exists()) {
			System.out.println("Erreur, le fichier n'existe pas");
			System.exit(0);
		}

		Travail work = new Travail(path); // On a découpé notre projet en taches
		work.show();

		// On va effectuer les tâches...
		this.updateServerListThread.start();
		ExecutorService executorService = Executors.newFixedThreadPool(10);
		ArrayList<Future<Tache>> FutureRetoursDesServeurs = new ArrayList<Future<Tache>>();
		
		if (!getServerDispos().isEmpty()) {
			// On va envoyer nos Taches !
			for (int i = 0; i < work.Taches.size(); i++) {
				String randomServerName = getRandomServerName();
				Tache task = work.Taches.get(i);
				task.setToInProgressState();
				task.setAssignedTo(randomServerName);

				FutureRetoursDesServeurs.add(executorService
						.submit(new ComputeCallable(getServerDispos()
								.get(randomServerName), task)));
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
								refreshServerList();
								String randomServerName = getRandomServerName();
								if (randomServerName.isEmpty()) { // On n'a plus de serveurs connectés !!
									System.out.println("Aucun serveur disponible dans le RMI. Veuillez reconnecter un serveur de calcul au RMI pour continuer.");
									while (randomServerName.isEmpty()) {
										Thread.sleep(1500);
										refreshServerList();
										randomServerName = getRandomServerName();
									}
								}
								futureTask.setToInProgressState();
								futureTask.setAssignedTo(randomServerName);
								FutureRetoursDesServeurs.add(executorService
										.submit(new ComputeCallable(
												getServerDispos()
														.get(randomServerName),
												futureTask)));
							} else {
								System.out.println(futureTask.getAssignedTo()
										+ " réponds pour la tâche #"+futureTask.getID()+ " : " 
										+ futureTask.getResultat());
								work.addToComputedResult(futureTask
										.getResultat());
							}
						} catch (InterruptedException e) {
							e.printStackTrace();
						} catch (ExecutionException e) {
							e.printStackTrace();
						}
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
		this.updateServerListThread.interrupt();
	}

	private synchronized String getRandomServerName() {
		if (ServerDispos.isEmpty()) return ""; // On retourne null si notre liste de serveurs est nul. On réessayera plus tard !
		int aupif = this.rand.nextInt(ServerDispos.size());
		Set<String> set = ServerDispos.keySet();
		String serverName = null;
		Iterator<String> serverNames = set.iterator();
		for (int j = 0; j <= aupif; j++) {
			serverName = serverNames.next();
		}
		return serverName;
	}

	private synchronized void listServers() {
		// refreshServerList();
		if (!ServerDispos.isEmpty()) {
			int i = 1;
			System.out.println(ServerDispos.size() + " serveur" + (ServerDispos.size() > 1 ? "s" : "") + " connecté" + (ServerDispos.size() > 1 ? "s" : "") + " :");

			// On récupère les clés de notre Hashtable, id est les serverNames
			Set<String> set = ServerDispos.keySet();
			String serverName;
			Iterator<String> serverNames = set.iterator();
			while (serverNames.hasNext()) {
				serverName = serverNames.next();
				System.out.println("Server #" + i + " : " + serverName);
				// System.out.println("Server #"+i+" : "+ServerDispos.get(serverName).toString());
				i++;
			}
		} else {
			System.out.println("Aucuns Serveurs n'est pour l'instant enregistré dans le RMI.");
		}
		
	}

	private synchronized void refreshServerList() {
		Hashtable<String, ServerCalculInterface> oldServeurs = new Hashtable<String, ServerCalculInterface>();
		oldServeurs.putAll(this.ServerDispos);
		String addedAndRemovedServers = "";
		// String newServers = "";
		this.ServerDispos = new Hashtable<String, ServerCalculInterface>();
		String[] listeServeurs = null;
		Registry registry = null;

		try {
			registry = LocateRegistry.getRegistry("127.0.0.1", 5000);
			listeServeurs = registry.list();
			// String RMIServers = "";
			// for (String serverName : listeServeurs) {
			// 	RMIServers += serverName + ", ";
			// }
			// System.out.println("## Serveurs Enregistrés dans le RMI: " + RMIServers.substring( 0, (RMIServers.length() >= 2) ? RMIServers.length() - 2 : 0));
		} catch (RemoteException e) { System.out.println("Erreur de connexion au Registre RMI : "+ e.getMessage()); }

		if (listeServeurs.length > 0 && registry != null) {
			for (String serverName : listeServeurs) {
				ServerCalculInterface stub = null;
				try {
					stub = (ServerCalculInterface) registry.lookup(serverName);
				} catch (AccessException e) { System.out.println("AccessException"); }
				  catch (RemoteException e) { System.out.println("RemoteException"); }
				  catch (NotBoundException e){System.out.println("NotBoundException");}
				try {
					if (stub != null && stub.ping()) {
						ServerDispos.put(serverName, stub);
						// newServers += serverName + ", ";
						if (!oldServeurs.containsKey(serverName))
							addedAndRemovedServers += "+"+serverName + ", ";
					} else { // Je ne devrais jamais arriver ici, il sertait tombé dans le Catch
						System.out.print(serverName + " non joignable. ");
						unbind(serverName); // On le retire de la liste du RMI car on a pas réussi à le joindre.
					}
				} catch (RemoteException e1) {
					System.out.print(serverName + " non joignable. ");
					unbind(serverName); // On le retire de la liste du RMI car on a pas réussi à le joindre.
				}
			}
			// System.out.println("## Serveurs de Calcul disponibles  : " + newServers.substring(0, (newServers.length() >= 2) ? newServers.length() - 2 : 0));
			if (!oldServeurs.isEmpty()) { // On trouve les serveurs qui ont été coupés
				// On récupère les clés de notre Hashtable, id est les serverNames
				Set<String> set = oldServeurs.keySet();
				String serverName;
				Iterator<String> serverNames = set.iterator();
				while (serverNames.hasNext()) {
					serverName = serverNames.next();
					if (!ServerDispos.containsKey(serverName))
						addedAndRemovedServers += "-"+serverName + ", ";
				}
			}
			if (!addedAndRemovedServers.isEmpty()) {
				System.out.println("## MAJ Liste servers : "
								+ addedAndRemovedServers.substring(0, (addedAndRemovedServers.length() >= 2) ? addedAndRemovedServers.length() - 2 : 0) );
			}
		}
	}

	private void unbind(String serverNameToUnbind) {
		try {
			Registry registry = LocateRegistry.getRegistry("127.0.0.1", 5000);
			String[] listeServeurs = registry.list();
			if (listeServeurs.length > 0) {
				for (String serverName : listeServeurs) {
					if (serverName.equals(serverNameToUnbind)) {
						try {
							registry.unbind(serverNameToUnbind);
							System.out.println(serverNameToUnbind
									+ " a été retiré du RMI Registery");
						} catch (NotBoundException e) {
							e.printStackTrace();
						}
					}
				}

			}
		} catch (AccessException e) {
			System.out.println("Erreur: " + e.getMessage());
		} catch (RemoteException e) {
			System.out.println("Erreur: " + e.getMessage());
		}
	}
}
