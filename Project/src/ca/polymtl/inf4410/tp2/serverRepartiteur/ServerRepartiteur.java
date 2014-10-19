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
	private enum functions {
		listServers, compute, unbind
	};
	private Random rand = new Random();

	public ServerRepartiteur() {
		super();
		if (System.getSecurityManager() == null) {
			System.setSecurityManager(new SecurityManager());
		}

		this.ServerDispos = new Hashtable<String, ServerCalculInterface>();
		refreshServerList();
	}

	private void compute(String path) {
		File file = new File(path);
		if (!file.exists()) {
			System.out.println("Erreur, le fichier n'existe pas");
			return;
		}

		Travail work = new Travail(path); // On a découpé notre projet en taches
		work.show();

		// On va effectuer les tâches...
		ExecutorService executorService = Executors.newFixedThreadPool(10);
		ArrayList<Future<Tache>> FutureRetoursDesServeurs = new ArrayList<Future<Tache>>();

		if (!ServerDispos.isEmpty()) {
			// On va envoyer nos Taches !

			for (int i = 0; i < work.Taches.size(); i++) {
				String randomServerName = getRandomServerName();
				Tache task = work.Taches.get(i);
				task.setToInProgressState();
				task.setAssignedTo(randomServerName);

				FutureRetoursDesServeurs.add(executorService
						.submit(new ComputeCallable(ServerDispos
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
								futureTask.setToInProgressState();
								futureTask.setAssignedTo(randomServerName);
								FutureRetoursDesServeurs.add(executorService.submit(new ComputeCallable(
										ServerDispos.get(randomServerName), futureTask
								)));
							} else {
								System.out.println(futureTask.getAssignedTo()+" a dit : "+futureTask.getResultat());
								work.addToComputedResult(futureTask.getResultat());
							}
						} catch (InterruptedException e) { e.printStackTrace(); }
						  catch (ExecutionException e) { e.printStackTrace(); }
					}

				}
			}
			
		} else {
			System.out.println("On avisera plus tard");
		}
		executorService.shutdown();
		System.out.println("Résultat calculé : " + work.computedResult);
		System.out.println("Résultat attendu : " + work.expectedResult);
	}

	private String getRandomServerName() {
		int aupif = this.rand.nextInt(ServerDispos.size());
		Set<String> set = ServerDispos.keySet();
		String serverName = null;
		Iterator<String> serverNames = set.iterator();
		for (int j = 0; j <= aupif; j++) {
			serverName = serverNames.next();
		}
		return serverName ;
	}

	private void listServers() {
		// refreshServerList();
		if (!ServerDispos.isEmpty()) {
			int i = 1;

			System.out.println(ServerDispos.size() + " serveur"
					+ (ServerDispos.size() > 1 ? "s" : "") + " connecté"
					+ (ServerDispos.size() > 1 ? "s" : "") + " :");

			// On récupère les clés de notre Hashtable, id est les fileNames
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

	private void refreshServerList() {
		System.out
				.println("-------------------- MAJ Server List     --------------------");
		this.ServerDispos = new Hashtable<String, ServerCalculInterface>();
		String[] listeServeurs = null;
		Registry registry = null;

		try {
			registry = LocateRegistry.getRegistry("127.0.0.1", 5000);
			listeServeurs = registry.list();
			String RMIServers = "";
			for (String serverName : listeServeurs) {
				RMIServers += serverName + ", ";
			}
			System.out
					.println("Serveurs Enregistrés dans le RMI: "
							+ RMIServers.substring(
									0,
									(RMIServers.length() >= 2) ? RMIServers
											.length() - 2 : 0));
		} catch (RemoteException e) {
			System.out.println("Erreur de connexion au Registre RMI : "
					+ e.getMessage());
		}

		if (listeServeurs.length > 0 && registry != null) {
			String newServers = "";
			for (String serverName : listeServeurs) {
				ServerCalculInterface stub = null;
				try {
					stub = (ServerCalculInterface) registry.lookup(serverName);
				} catch (AccessException e) {
					System.out.println("AccessException");
				} catch (RemoteException e) {
					System.out.println("RemoteException");
				} catch (NotBoundException e) {
					System.out.println("NotBoundException");
				}
				try {
					if (stub != null && stub.ping()) {
						ServerDispos.put(serverName, stub);
						newServers += serverName + ", ";
					} else {
						System.out.println("Error, can't contact " + serverName
								+ ", ");
					}
				} catch (RemoteException e1) {
					System.out.print(serverName + " non joignable. ");
					unbind(serverName);
				}
			}
			System.out
					.println("Serveurs de Calcul disponibles  : "
							+ newServers.substring(
									0,
									(newServers.length() >= 2) ? newServers
											.length() - 2 : 0));
		}
		System.out
				.println("-------------------- END MAJ Server List --------------------");
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
